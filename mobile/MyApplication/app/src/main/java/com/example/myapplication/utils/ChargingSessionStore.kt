package com.example.myapplication.utils

import android.content.Context
import android.content.SharedPreferences

class ChargingSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("charging_session_prefs", Context.MODE_PRIVATE)

    fun saveSessionStart(plugInTimeMs: Long, predictionHourRef: Int) {
        prefs.edit()
            .putLong(KEY_PLUG_IN_TIME_MS, plugInTimeMs)
            .putInt(KEY_PREDICTION_HOUR_REF, predictionHourRef)
            .apply()
    }

    fun getPlugInTimeMs(): Long? {
        val value = prefs.getLong(KEY_PLUG_IN_TIME_MS, NO_VALUE)
        return if (value == NO_VALUE) null else value
    }

    fun getPredictionHourRef(): Int? {
        return if (prefs.contains(KEY_PREDICTION_HOUR_REF)) {
            prefs.getInt(KEY_PREDICTION_HOUR_REF, 0)
        } else {
            null
        }
    }

    fun clearSessionStart() {
        prefs.edit()
            .remove(KEY_PLUG_IN_TIME_MS)
            .remove(KEY_PREDICTION_HOUR_REF)
            .apply()
    }

    companion object {
        private const val KEY_PLUG_IN_TIME_MS = "plug_in_time_ms"
        private const val KEY_PREDICTION_HOUR_REF = "prediction_hour_ref"
        private const val NO_VALUE = -1L
    }
}