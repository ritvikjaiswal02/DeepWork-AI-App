package com.example.deepworkai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.deepworkai.models.FocusSession

// Generate a meaningful name from the session start time
fun sessionDisplayName(session: FocusSession): String {
    session.sessionName?.let { return it }
    return try {
        val hour = session.startTime.substring(11, 13).toInt()
        when {
            hour in 5..8   -> "Early Morning Session"
            hour in 9..11  -> "Morning Session"
            hour in 12..13 -> "Midday Session"
            hour in 14..16 -> "Afternoon Session"
            hour in 17..20 -> "Evening Session"
            else           -> "Night Session"
        }
    } catch (_: Exception) { "Focus Session" }
}

@Composable
fun SessionHistoryItem(
    session: FocusSession,
    onClick: () -> Unit = {}
) {
    val durationMin = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val start = sdf.parse(session.startTime)
        val end = session.endTime?.let { sdf.parse(it) } ?: start
        if (start != null && end != null) (end.time - start.time) / (1000 * 60) else 0L
    } catch (_: Exception) { 0L }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val tag = session.tags ?: "Focus"
    val displayTitle = sessionDisplayName(session)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        color = surfaceColor.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score ring
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = session.focusScore / 100f,
                    modifier = Modifier.size(48.dp),
                    color = Color(0xFF3B82F6),
                    trackColor = MaterialTheme.colorScheme.background,
                    strokeWidth = 4.dp,
                    strokeCap = StrokeCap.Round
                )
                Text("${session.focusScore}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
                    ) {
                        Text(tag, color = Color(0xFF3B82F6), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Text("$durationMin min  •  ${session.distractions} distractions",
                    color = Color(0xFF94A3B8), fontSize = 12.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    session.cognitiveLoad,
                    color = if (session.cognitiveLoad == "Optimal") Color(0xFF10B981) else Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Icon(Icons.Default.ChevronRight, contentDescription = "View details",
                    tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Preview
@Composable
fun SessionHistoryItemPreview() {
    val mock = FocusSession("1", "userId", "2023-10-27T10:00:00", "2023-10-27T10:45:00",
        87, 2, 42, "Optimal", null, "Design System", "Coding")
    SessionHistoryItem(session = mock)
}
