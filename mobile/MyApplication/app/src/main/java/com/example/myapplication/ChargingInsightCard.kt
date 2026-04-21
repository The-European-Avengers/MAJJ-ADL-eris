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
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Colour helpers (matching the GreenPause theme)
// ──────────────────────────────────────────────────────────────────────────────

private val GreenOk   = Color(0xFF2E7D32)
private val AmberWait = Color(0xFFF57F17)

// ──────────────────────────────────────────────────────────────────────────────
// Public composable
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Displays a contextual insight card when the phone is plugged in.
 *
 * • If now is the best moment → green "charge now" banner.
 * • Otherwise → amber "wait N hours" banner with savings estimate.
 * • Always shows the estimated CO₂ produced by charging now.
 *
 * Add this composable inside [HomeScreen], just above the action button,
 * wrapped in an [AnimatedVisibility] conditioned on [isCharging].
 *
 * ```kotlin
 * AnimatedVisibility(
 *     visible = isCharging && analysis != null,
 *     enter   = fadeIn() + expandVertically()
 * ) {
 *     analysis?.let { ChargingInsightCard(it) }
 * }
 * ```
 */
@Composable
fun ChargingInsightCard(analysis: ChargingAnalysis) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Header ──────────────────────────────────────────────────────
            Text(
                text       = "⚡ Charging Insight",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            // ── Recommendation banner ────────────────────────────────────────
            if (analysis.isNowBest) {
                RecommendationBanner(
                    color   = GreenOk,
                    icon    = "✅",
                    title   = "Now is a great time to charge!",
                    subtitle = "Carbon intensity is at its lowest in the next 24 h " +
                               "(${analysis.currentIntensity.roundToInt()} gCO₂/kWh)."
                )
            } else {
                val hoursLabel = if (analysis.bestHourOffset == 1) "1 hour" else "${analysis.bestHourOffset} hours"
                RecommendationBanner(
                    color   = AmberWait,
                    icon    = "⏳",
                    title   = "Wait ~$hoursLabel for cleaner energy",
                    subtitle = "Grid intensity will drop to ~${analysis.bestIntensity.roundToInt()} gCO₂/kWh " +
                               "(≈${analysis.estimatedSavingPct.roundToInt()} % less than now)."
                )
            }

            // ── Divider ──────────────────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Carbon cost: NOW ─────────────────────────────────────────────
            CarbonCostRow(
                label     = "📊 Carbon cost of charging now",
                intensity = analysis.currentIntensity,
                grams     = analysis.carbonProducedGrams,
                chipColor = AmberWait
            )

            // Only show the best-hour block and comparison when now isn't already best
            if (!analysis.isNowBest) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Carbon cost: BEST HOUR ───────────────────────────────────
                val hoursLabel = if (analysis.bestHourOffset == 1) "in 1 hour" else "in ${analysis.bestHourOffset} hours"
                CarbonCostRow(
                    label     = "🌿 Carbon cost if you wait ($hoursLabel)",
                    intensity = analysis.bestIntensity,
                    grams     = analysis.bestCarbonGrams,
                    chipColor = GreenOk
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Comparison ───────────────────────────────────────────────
                ComparisonRow(analysis)
            }

            // Footnote
            Text(
                text = "Assumes 50 % top-up of a 4 000 mAh battery via an 87 % efficient charger.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendationBanner(
    color: Color,
    icon: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text       = title,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = color
            )
            Text(
                text     = subtitle,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun CarbonCostRow(
    label: String,
    intensity: Float,
    grams: Float,
    chipColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricChip(
                label = "Grid intensity",
                value = "${intensity.roundToInt()} gCO₂/kWh"
            )
            MetricChip(
                label = "Energy drawn",
                value = "~8.9 Wh"
            )
            MetricChip(
                label     = "CO₂ emitted",
                value     = String.format("%.2f g", grams),
                highlight = true,
                color     = chipColor
            )
        }
    }
}

@Composable
private fun ComparisonRow(analysis: ChargingAnalysis) {
    val savingPct = analysis.estimatedSavingPct.roundToInt()
    val hoursLabel = if (analysis.bestHourOffset == 1) "1 h" else "${analysis.bestHourOffset} h"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = "📉 Comparison",
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface
        )

        // Side-by-side NOW vs BEST bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // NOW column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Now", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AmberWait.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = String.format("%.2f g", analysis.carbonProducedGrams),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AmberWait
                    )
                }
            }

            // Arrow + saving badge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("→", fontSize = 20.sp, color = GreenOk, fontWeight = FontWeight.Bold)
                Text(
                    text       = "-$savingPct%",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = GreenOk
                )
            }

            // BEST column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("In $hoursLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GreenOk.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = String.format("%.2f g", analysis.bestCarbonGrams),
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = GreenOk
                    )
                }
            }
        }

        // Saving summary chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(GreenOk.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "You'd save ",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text       = String.format("%.2f g CO₂", analysis.carbonSavedGrams),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = GreenOk
            )
            Text(
                text = " by waiting",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    highlight: Boolean = false,
    color: Color = Color.Unspecified
) {
    val resolvedColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.primary
    val bg   = if (highlight) resolvedColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val onBg = if (highlight) resolvedColor else MaterialTheme.colorScheme.onSurfaceVariant

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