package com.example.routes
import com.example.db.DatabaseFactory
import com.example.models.*
import com.example.repository.FocusRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

// Pass the repository as a parameter so both route sets can use it
fun Route.allRoutes(repository: FocusRepository) {

    // --- FOCUS SESSIONS ROUTES ---
    route("/sessions") {
        post("/start") {
            try {
                val request = call.receive<StartSessionRequest>()
                println("FocusRoutes: Starting session for user ${request.userId}, task ${request.taskId}")
                val taskId = request.taskId?.let { UUID.fromString(it) }
                val session = DatabaseFactory.startFocusSession(UUID.fromString(request.userId), taskId, request.sessionName)
                if (session != null) {
                    println("FocusRoutes: Session started with ID ${session.id}")
                    call.respond(HttpStatusCode.Created, session)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Could not start session")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
            }
        }

        post("/end") {
            try {
                val request = call.receive<EndSessionRequest>()
                println("FocusRoutes: Ending session ${request.sessionId} with ${request.distractions} distractions")
                val updatedSession = DatabaseFactory.endFocusSession(request.sessionId, request.distractions, request.targetDurationMinutes)

                if (updatedSession != null) {
                    println("FocusRoutes: Session found, calculating risk and saving history...")
                    val startDateTime = java.time.LocalDateTime.parse(updatedSession.startTime)
                    val endDateTime = java.time.LocalDateTime.now()
                    val actualDuration = java.time.Duration.between(startDateTime, endDateTime).toMinutes().toInt()
                    val currentHour = endDateTime.hour

                    val riskLabel = getMLBurnoutPrediction(
                        duration = actualDuration.toDouble(),
                        hour = currentHour,
                        distractions = updatedSession.distractions,
                        score = updatedSession.focusScore
                    )

                    if (!request.distractedApps.isNullOrEmpty()) {
                        DatabaseFactory.insertDistractions(
                            sessionId = updatedSession.id,
                            userId = updatedSession.userId.toString(),
                            apps = request.distractedApps
                        )
                    }

                    // 🚀 CRITICAL: Update the Analytics table whenever a session ends!
                    repository.saveSessionAndUpdateAnalytics(
                        userId = updatedSession.userId.toString(),
                        sessionId = updatedSession.id,
                        score = updatedSession.focusScore,
                        duration = actualDuration,
                        switches = updatedSession.distractions,
                        risk = riskLabel
                    )

                    // 🚀 Save to History table as well 
                    repository.saveSessionToHistory(
                        SaveSessionRequest(
                            userId = updatedSession.userId.toString(),
                            startTime = updatedSession.startTime,
                            endTime = java.time.LocalDateTime.now().toString(),
                            durationMinutes = actualDuration,
                            distractions = updatedSession.distractions,
                            stabilityScore = updatedSession.focusScore,
                            avgDeepBlock = actualDuration, // Simple mapping
                            cognitiveLoad = updatedSession.cognitiveLoad
                        )
                    )


                    println("FocusRoutes: Session ${updatedSession.id} automatically saved to history for user ${updatedSession.userId}")

                    call.respond(HttpStatusCode.OK, EndSessionResponse(
                        session = updatedSession,
                        burnoutRisk = riskLabel
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
            }
        }

        authenticate("auth-jwt") {
            post("/chat") {
                try {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asString()
                        ?: return@post call.respond(HttpStatusCode.Unauthorized, "Missing User ID")

                    val request = call.receive<ChatRequest>()

                    // ─── Build rich user context from real DB data ───
                    val avgScore = repository.getUserAverageFocusScore(userId)
                    val recentSessions = repository.getUserSessionHistory(userId).take(5)
                    val distractions = try {
                        DatabaseFactory.getDistractionsList(UUID.fromString(userId))
                    } catch (_: Exception) { emptyList() }

                    // Top 3 distracting apps across all sessions
                    val topApps = distractions
                        .flatMap { it.apps }
                        .groupBy { it.appName }
                        .mapValues { (_, v) -> v.sumOf { it.usageTime } }
                        .toList()
                        .sortedByDescending { it.second }
                        .take(3)

                    val totalSessions = recentSessions.size
                    val sessionLines = recentSessions.take(3).joinToString("; ") { s ->
                        val name = s.sessionName ?: "untitled"
                        val date = s.startTime.take(10)
                        "$date \"$name\" score=${s.focusScore}% distractions=${s.distractions} load=${s.cognitiveLoad}"
                    }
                    val appsLine = if (topApps.isNotEmpty()) {
                        topApps.joinToString(", ") { "${it.first} (${it.second}s total)" }
                    } else "none recorded"

                    val userContext = buildString {
                        appendLine("Average focus score across all sessions: $avgScore%.")
                        appendLine("Total recent completed sessions: $totalSessions.")
                        if (sessionLines.isNotBlank()) {
                            appendLine("Last sessions: $sessionLines.")
                        }
                        appendLine("Top distracting apps: $appsLine.")
                    }.trim()

                    val reply = getAIAssistantResponse(request.query, userContext, request.schedule)
                    call.respond(HttpStatusCode.OK, ChatResponse(reply))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
                }
            }
        }
    }

    // --- ANALYTICS ROUTES (Moved outside /sessions) ---
    route("/analytics") {
        get("/dashboard/{userId}") {
            val userId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val period = call.request.queryParameters["period"] ?: "weekly"
            val limit = if (period.lowercase() == "monthly") 30 else 7
            
            println("FocusRoutes: Fetching $period dashboard for user $userId (limit=$limit)")
            try {
                val dashboard = repository.getDashboard(userId, limit)
                call.respond(dashboard)
            } catch (e: Exception) {
                println("FocusRoutes: ERROR fetching dashboard: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }

        get("/distractions/{userId}") {
            val userId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            try {
                val distractions = DatabaseFactory.getDistractionsList(UUID.fromString(userId))
                val appsJson = Json.encodeToString(distractions)
                
                // Call Python to get recommendation
                val recommendation = getMLDistractionRecommendation(appsJson)
                
                call.respond(HttpStatusCode.OK, com.example.models.DistractionInsightsResponse(distractions, recommendation))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
    }
}

// ── Path config ──────────────────────────────────────────────────────────────
// PYTHON_EXE  env var overrides; falls back to the system Python on this machine.
// ML_SCRIPTS_DIR env var overrides; falls back to the sibling deepwork_ml folder.
private val PYTHON_EXE: String =
    System.getenv("PYTHON_EXE")
        ?: "C:\\Users\\ASUS\\AppData\\Local\\Programs\\Python\\Python313\\python.exe"

private val ML_DIR: String =
    System.getenv("ML_SCRIPTS_DIR")
        ?: "C:\\Users\\ASUS\\OneDrive\\Desktop\\deepwork\\deepwork_ml"
// ─────────────────────────────────────────────────────────────────────────────

fun getMLBurnoutPrediction(duration: Double, hour: Int, distractions: Int, score: Int): String {
    return try {
        val scriptPath = "$ML_DIR\\predict_for_ktor.py"

        val process = ProcessBuilder(
            PYTHON_EXE, scriptPath,
            duration.toString(), hour.toString(), distractions.toString(), score.toString()
        ).start()

        val result = process.inputStream.bufferedReader().readText().trim()

        when (result) {
            "0" -> "Low"
            "1" -> "Medium"
            "2" -> "High"
            else -> "Low"
        }
    } catch (e: Exception) {
        println("getMLBurnoutPrediction error: ${e.message}")
        "Low"
    }
}

fun getMLDistractionRecommendation(appsDataJson: String): String {
    return try {
        val scriptPath = "$ML_DIR\\get_ai_recommendation.py"

        val process = ProcessBuilder(
            PYTHON_EXE, scriptPath, appsDataJson
        ).start()

        val result = process.inputStream.bufferedReader().readText().trim()
        result.ifBlank {
            "Consider limiting your usage of these apps during focus sessions."
        }
    } catch (e: Exception) {
        println("getMLDistractionRecommendation error: ${e.message}")
        "Reduce your time on distracting apps to stay more focused."
    }
}

fun getAIAssistantResponse(query: String, context: String, schedule: String): String {
    return try {
        val scriptPath = "$ML_DIR\\ai_chatbot.py"

        val process = ProcessBuilder(
            PYTHON_EXE, scriptPath, query, context, schedule
        ).start()

        val result = process.inputStream.bufferedReader().readText().trim()
        result.ifBlank {
            "I'm sorry, I couldn't process your request."
        }
    } catch (e: Exception) {
        println("getAIAssistantResponse error: ${e.message}")
        "Failed to reach AI service."
    }
}

fun Route.sessionHistoryRoutes(repository: FocusRepository) {
    route("/sessions") {
        post("/save"){
            try{
                // Receive request from android
                val request = call.receive<SaveSessionRequest>()
                // Save to PostgreSQL history table
                repository.saveSessionToHistory(request)

                // Respond 201 Created
                call.respond(HttpStatusCode.Created, "Session saved successfully to history")
            }catch (e: Exception){
                call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
            }
        }

        // Added this block to fetch user history based on the user's request
        get("/history/{userId}") {
            val userId = call.parameters["userId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            println("FocusRoutes: Fetching history for user $userId")
            try {
                val history = repository.getUserSessionHistory(userId)
                println("FocusRoutes: Found ${history.size} sessions for $userId")
                call.respond(history)
            } catch (e: Exception) {
                println("FocusRoutes: ERROR fetching history: ${e.message}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
            }
        }
        // End of added history route

        // NOTE: The legacy Python-based PDF export route (/sessions/export/{userId}) was
        // removed. It pointed at a hardcoded path on a different developer's machine
        // (C:\Users\srija\...) and was never called by the app. PDF/CSV export is handled
        // by the working pure-Kotlin routes in ExportRoutes.kt (/api/export/pdf and /csv),
        // which is what the Android client actually uses.
    }
}