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