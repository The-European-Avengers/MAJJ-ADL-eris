package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val SessionGreen = Color(0xFF2E7D32)
private val SessionBlue  = Color(0xFF1565C0)
private val TimeFormat   = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ChargingSessionCard(session: ChargingSession) {
    val plugInTime  = Instant.ofEpochMilli(session.plugInTime)
        .atZone(ZoneId.systemDefault()).format(TimeFormat)
    val plugOutTime = Instant.ofEpochMilli(session.plugOutTime)
        .atZone(ZoneId.systemDefault()).format(TimeFormat)

    val hours   = session.durationMinutes / 60
    val minutes = session.durationMinutes % 60
    val durationLabel = if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"

    Card(
        modifier  = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text       = "🔋 Last Charging Session",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SessionBlue.copy(alpha = 0.10f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                TimeChip(label = "Plugged in", value = plugInTime,    color = SessionBlue)
                Text("→", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                TimeChip(label = "Unplugged",  value = plugOutTime,   color = SessionBlue)
                TimeChip(label = "Duration",   value = durationLabel, color = SessionBlue)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text       = "📊 Measured emission",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SessionMetricChip(
                    label = "Avg intensity",
                    value = "${session.avgIntensity.roundToInt()} gCO₂/kWh"
                )
                SessionMetricChip(
                    label = "Energy drawn",
                    value = String.format("%.1f Wh", session.energyDrawnWh)
                )
                SessionMetricChip(
                    label     = "CO₂ emitted",
                    value     = String.format("%.2f g", session.totalCarbonGrams),
                    highlight = true
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SessionGreen.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val equivalentMeters = (session.totalCarbonGrams / 0.12f).roundToInt()
                Text("≈ driving ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("${equivalentMeters} m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SessionGreen)
                Text(" in a petrol car", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            Text(
                text     = "Intensity averaged hour-by-hour from ML forecast. Energy assumes constant draw over session duration.",
                fontSize  = 10.sp,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TimeChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold,  color = color)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}

@Composable
private fun SessionMetricChip(label: String, value: String, highlight: Boolean = false) {
    val bg   = if (highlight) SessionGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val onBg = if (highlight) SessionGreen else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,  color = onBg)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = onBg.copy(alpha = 0.75f))
    }
}