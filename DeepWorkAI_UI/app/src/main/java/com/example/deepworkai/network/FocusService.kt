package com.example.deepworkai.network

import com.example.deepworkai.models.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import com.example.deepworkai.BuildConfig

class FocusService {
    private val BASE_URL get() = "${NetworkPreferences.backendUrl}/sessions"

    suspend fun startSession(userId: String, taskId: String? = null, sessionName: String? = null): FocusSession? {
        return try {
            android.util.Log.d("FocusService", "POST $BASE_URL/start  user=$userId task=$taskId name=$sessionName")
            KtorClient.httpClient.post("$BASE_URL/start") {
                contentType(ContentType.Application.Json)
                setBody(StartSessionRequest(userId, taskId, sessionName))
            }.body()
        } catch (e: Exception) {
            android.util.Log.e("FocusService", "startSession FAILED on $BASE_URL/start : ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    // This is the ONLY endSession function you need now
    suspend fun endSession(sessionId: String, distractions: Int, distractedApps: List<DistractionApp>? = null, targetDuration: Int = 25): EndSessionResponse? {
        return try {
            KtorClient.httpClient.post("$BASE_URL/end") {
                contentType(ContentType.Application.Json)
                setBody(EndSessionRequest(sessionId, distractions, distractedApps, targetDuration))
            }.body() // This now captures both session and burnoutRisk
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getDistractionInsights(userId: String): DistractionInsightsResponse? {
        return try {
            KtorClient.httpClient.get("${NetworkPreferences.backendUrl}/analytics/distractions/$userId").body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun askAIAssistant(query: String, schedule: String): ChatResponse? {
        return try {
            KtorClient.httpClient.post("$BASE_URL/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(query, schedule))
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}