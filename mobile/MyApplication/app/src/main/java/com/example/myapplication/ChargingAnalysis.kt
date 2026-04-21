package com.example.myapplication

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Result of analysing the 24-hour carbon-intensity forecast.
 *
 * @param isNowBest          True when the current hour is already the greenest window.
 * @param bestHourOffset     Hours from now until the best window (0 = now).
 * @param currentIntensity   gCO₂eq/kWh for the current hour.
 * @param bestIntensity      gCO₂eq/kWh for the best predicted hour.
 * @param estimatedSavingPct Relative reduction if the user waits   (0–100 %).
 * @param carbonProducedGrams Grams of CO₂eq emitted to charge the phone NOW.
 */
data class ChargingAnalysis(
    val isNowBest: Boolean,
    val bestHourOffset: Int,
    val currentIntensity: Float,
    val bestIntensity: Float,
    val estimatedSavingPct: Float,
    val carbonProducedGrams: Float,
    val bestCarbonGrams: Float,
    val carbonSavedGrams: Float
)

// ──────────────────────────────────────────────────────────────────────────────
// Constants – phone charging profile
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Typical smartphone battery capacity in kWh.
 * A 4 000 mAh / 3.85 V cell ≈ 15.4 Wh ≈ 0.0154 kWh.
 * We model a *50 % top-up* (half a charge cycle), which is the most common
 * real-world scenario when a user plugs in at a random moment.
 */
private const val PHONE_CHARGE_KWH = 0.0154f * 0.5f   // ≈ 0.0077 kWh per session

/**
 * Wall-charger efficiency (energy drawn from grid vs stored in battery).
 * Modern USB-C fast chargers are ~85–90 % efficient.
 */
private const val CHARGER_EFFICIENCY = 0.87f

/** Actual energy drawn from the grid for one charging session. */
private val GRID_ENERGY_KWH = PHONE_CHARGE_KWH / CHARGER_EFFICIENCY   // ≈ 0.00886 kWh

// ──────────────────────────────────────────────────────────────────────────────
// Charging session model
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Represents one completed charging session with its measured emission.
 *
 * @param plugInTime        Epoch millis when the charger was connected.
 * @param plugOutTime       Epoch millis when the charger was disconnected.
 * @param durationMinutes   Total charging time in minutes.
 * @param totalCarbonGrams  Total gCO₂eq emitted during the session.
 * @param avgIntensity      Weighted average grid intensity during the session (gCO₂eq/kWh).
 * @param energyDrawnWh     Total energy drawn from the grid (Wh).
 */
data class ChargingSession(
    val plugInTime: Long,
    val plugOutTime: Long,
    val durationMinutes: Int,
    val totalCarbonGrams: Float,
    val avgIntensity: Float,
    val energyDrawnWh: Float
)

/**
 * Calculates the carbon emitted during a real charging session.
 *
 * Strategy: the phone draws power at a roughly constant rate. We know the
 * charger efficiency and max battery capacity. For each hour (or fraction)
 * that the session overlaps, we use the corresponding predicted intensity.
 *
 * [predictionValues] has 24 entries where index 0 = the hour in which
 * [predictionStartHour] falls (i.e. the current hour when the model ran).
 *
 * @param plugInMs              System.currentTimeMillis() at plug-in.
 * @param plugOutMs             System.currentTimeMillis() at plug-out.
 * @param predictionValues      24-hour intensity forecast (gCO₂eq/kWh).
 * @param predictionStartHour   Wall-clock hour (0–23) of prediction index 0.
 */
fun calculateSessionEmission(
    plugInMs: Long,
    plugOutMs: Long,
    predictionValues: List<Float>,
    predictionStartHour: Int
): ChargingSession? {
    if (predictionValues.isEmpty() || plugOutMs <= plugInMs) return null

    val durationMs      = plugOutMs - plugInMs
    val durationMinutes = (durationMs / 60_000L).toInt().coerceAtLeast(1)
    val durationHours   = durationMs / 3_600_000.0   // fractional hours

    // Power draw (W): energy stored ÷ time. We use full-battery capacity (15.4 Wh)
    // as the denominator assuming up to 2 h for a full charge.
    // Charger input power = battery power ÷ efficiency.
    // For a real session we distribute energy proportionally to time.
    // Total grid energy = constant rate × total duration
    val gridEnergyKwh = (PHONE_CHARGE_KWH / CHARGER_EFFICIENCY) *
            (durationHours / 1.0).toFloat()   // scale by actual session fraction vs 30-min base
    val gridEnergyWh  = gridEnergyKwh * 1000f

    // Break the session into per-hour slices and weight each slice's intensity
    // by what fraction of total time falls in that hour.
    var weightedIntensitySum = 0.0
    var totalWeight          = 0.0

    val plugInHour  = java.time.Instant.ofEpochMilli(plugInMs)
        .atZone(java.time.ZoneId.systemDefault()).hour
    val plugInMin   = java.time.Instant.ofEpochMilli(plugInMs)
        .atZone(java.time.ZoneId.systemDefault()).minute

    // Walk through each hour slot the session touches
    var remainingMs = durationMs
    var slotStart   = plugInMs

    while (remainingMs > 0) {
        val slotInstant  = java.time.Instant.ofEpochMilli(slotStart)
            .atZone(java.time.ZoneId.systemDefault())
        val slotHour     = slotInstant.hour
        // How many ms until the end of this clock-hour?
        val msUntilNextHour = ((60 - slotInstant.minute) * 60_000L) -
                              (slotInstant.second * 1000L)
        val slotMs       = minOf(remainingMs, msUntilNextHour).coerceAtLeast(1L)

        // Map wall-clock hour → prediction index
        val hourOffset   = ((slotHour - predictionStartHour + 24) % 24)
        val intensity    = if (hourOffset < predictionValues.size)
            predictionValues[hourOffset].toDouble()
        else
            predictionValues.last().toDouble()   // clamp to last known value

        weightedIntensitySum += intensity * slotMs
        totalWeight          += slotMs

        remainingMs -= slotMs
        slotStart   += slotMs
    }

    val avgIntensity      = if (totalWeight > 0) (weightedIntensitySum / totalWeight).toFloat() else 0f
    val totalCarbonGrams  = gridEnergyKwh * avgIntensity

    return ChargingSession(
        plugInTime       = plugInMs,
        plugOutTime      = plugOutMs,
        durationMinutes  = durationMinutes,
        totalCarbonGrams = totalCarbonGrams,
        avgIntensity     = avgIntensity,
        energyDrawnWh    = gridEnergyWh
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Core analysis function
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Analyses [predictionValues] (24 hourly gCO₂eq/kWh forecasts starting at
 * hour 0 = "now") and returns a [ChargingAnalysis] summary.
 *
 * @param predictionValues List of 24 floats produced by the ML model.
 */
fun analyseChargingWindow(predictionValues: List<Float>): ChargingAnalysis? {
    if (predictionValues.isEmpty()) return null

    val currentIntensity = predictionValues[0]

    // Find the hour with the lowest predicted carbon intensity
    val bestHourOffset = predictionValues.indices.minByOrNull { predictionValues[it] } ?: 0
    val bestIntensity  = predictionValues[bestHourOffset]

    // A threshold: only flag "not now" if the best hour is meaningfully greener
    // (> 5 % better) to avoid spurious recommendations.
    val isNowBest = bestHourOffset == 0 ||
            (currentIntensity - bestIntensity) / currentIntensity.coerceAtLeast(1f) < 0.05f

    val estimatedSavingPct = if (currentIntensity > 0f)
        ((currentIntensity - bestIntensity) / currentIntensity * 100f).coerceAtLeast(0f)
    else 0f

    // Carbon produced (gCO₂eq) = grid energy (kWh) × intensity (gCO₂eq/kWh)
    val carbonProducedGrams = GRID_ENERGY_KWH * currentIntensity
    val bestCarbonGrams     = GRID_ENERGY_KWH * bestIntensity
    val carbonSavedGrams    = (carbonProducedGrams - bestCarbonGrams).coerceAtLeast(0f)

    return ChargingAnalysis(
        isNowBest            = isNowBest,
        bestHourOffset       = bestHourOffset,
        currentIntensity     = currentIntensity,
        bestIntensity        = bestIntensity,
        estimatedSavingPct   = estimatedSavingPct,
        carbonProducedGrams  = carbonProducedGrams,
        bestCarbonGrams      = bestCarbonGrams,
        carbonSavedGrams     = carbonSavedGrams
    )
}