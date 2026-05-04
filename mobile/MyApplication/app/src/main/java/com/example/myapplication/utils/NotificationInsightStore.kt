package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.ChargingAnalysis

data class NotificationInsightSnapshot(
    val isNowBest: Boolean,
    val bestHourOffset: Int,
    val currentIntensity: Float,
    val bestIntensity: Float,
    val estimatedSavingPct: Float,
    val carbonProducedGrams: Float
)

class NotificationInsightStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("notification_insight_prefs", Context.MODE_PRIVATE)

    fun saveAnalysisSnapshot(analysis: ChargingAnalysis) {
        prefs.edit()
            .putBoolean(KEY_IS_NOW_BEST, analysis.isNowBest)
            .putInt(KEY_BEST_HOUR_OFFSET, analysis.bestHourOffset)
            .putFloat(KEY_CURRENT_INTENSITY, analysis.currentIntensity)
            .putFloat(KEY_BEST_INTENSITY, analysis.bestIntensity)
            .putFloat(KEY_ESTIMATED_SAVING_PCT, analysis.estimatedSavingPct)
            .putFloat(KEY_CARBON_PRODUCED_GRAMS, analysis.carbonProducedGrams)
            .apply()
    }

    fun getLatestSnapshot(): NotificationInsightSnapshot? {
        if (!prefs.contains(KEY_CARBON_PRODUCED_GRAMS)) {
            return null
        }

        return NotificationInsightSnapshot(
            isNowBest = prefs.getBoolean(KEY_IS_NOW_BEST, false),
            bestHourOffset = prefs.getInt(KEY_BEST_HOUR_OFFSET, 0),
            currentIntensity = prefs.getFloat(KEY_CURRENT_INTENSITY, 0f),
            bestIntensity = prefs.getFloat(KEY_BEST_INTENSITY, 0f),
            estimatedSavingPct = prefs.getFloat(KEY_ESTIMATED_SAVING_PCT, 0f),
            carbonProducedGrams = prefs.getFloat(KEY_CARBON_PRODUCED_GRAMS, 0f)
        )
    }

    companion object {
        private const val KEY_IS_NOW_BEST = "is_now_best"
        private const val KEY_BEST_HOUR_OFFSET = "best_hour_offset"
        private const val KEY_CURRENT_INTENSITY = "current_intensity"
        private const val KEY_BEST_INTENSITY = "best_intensity"
        private const val KEY_ESTIMATED_SAVING_PCT = "estimated_saving_pct"
        private const val KEY_CARBON_PRODUCED_GRAMS = "carbon_produced_grams"
    }
}