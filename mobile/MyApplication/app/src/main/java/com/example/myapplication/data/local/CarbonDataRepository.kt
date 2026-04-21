package com.example.myapplication.data.local

import android.content.Context
import android.util.Log
import com.example.myapplication.CarbonRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.TreeMap

data class CacheDiagnostics(
    val recordCount: Int,
    val firstTimestamp: LocalDateTime?,
    val lastTimestamp: LocalDateTime?,
    val lastRefreshMs: Long,
    val largestGapHours: Long,
    val avgCarbonIntensity: Float?,
    val minCarbonIntensity: Float?,
    val maxCarbonIntensity: Float?
)

class CarbonDataRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    suspend fun refreshCacheFromPublicApi(daysBack: Int = 20): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cache refresh from API (daysBack=$daysBack)")
            val hourlySeries = fetchHourlyCarbonSeries(daysBack)
            Log.d(TAG, "Fetched ${hourlySeries.length()} hourly records from API")
            
            if (hourlySeries.length() == 0) {
                Log.w(TAG, "API returned empty series")
                return@withContext false
            }

            sharedPreferences.edit()
                .putString(KEY_CARBON_SERIES, hourlySeries.toString())
                .putLong(KEY_LAST_REFRESH_MS, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Cache successfully saved with ${hourlySeries.length()} records")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh carbon cache: ${e.message} | ${e.cause}", e)
            false
        }
    }

    fun getCachedRecords(): List<CarbonRecord> {
        val raw = sharedPreferences.getString(KEY_CARBON_SERIES, null) ?: return emptyList()

        return try {
            val series = JSONArray(raw)
            val records = mutableListOf<CarbonRecord>()

            for (i in 0 until series.length()) {
                val item = series.optJSONObject(i) ?: continue
                val timestamp = item.optString("datetimeUtc")
                val carbonIntensity = item.optDouble("carbonIntensity", Double.NaN)

                if (timestamp.isBlank() || carbonIntensity.isNaN()) {
                    continue
                }

                val dt = LocalDateTime.parse(timestamp)
                records.add(CarbonRecord(dt, carbonIntensity.toFloat()))
            }

            records.sortedBy { it.datetime }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid cached carbon data", e)
            emptyList()
        }
    }

    fun getCacheDiagnostics(): CacheDiagnostics {
        val records = getCachedRecords()
        val sorted = records.sortedBy { it.datetime }

        var largestGap = 0L
        for (i in 1 until sorted.size) {
            val gap = Duration.between(sorted[i - 1].datetime, sorted[i].datetime).toHours()
            if (gap > largestGap) {
                largestGap = gap
            }
        }

        val values = sorted.map { it.carbonIntensity }
        val avg = if (values.isNotEmpty()) values.average().toFloat() else null

        return CacheDiagnostics(
            recordCount = sorted.size,
            firstTimestamp = sorted.firstOrNull()?.datetime,
            lastTimestamp = sorted.lastOrNull()?.datetime,
            lastRefreshMs = sharedPreferences.getLong(KEY_LAST_REFRESH_MS, 0L),
            largestGapHours = largestGap,
            avgCarbonIntensity = avg,
            minCarbonIntensity = values.minOrNull(),
            maxCarbonIntensity = values.maxOrNull()
        )
    }

    private fun fetchHourlyCarbonSeries(daysBack: Int): JSONArray {
        val filterJson = URLEncoder.encode("{\"PriceArea\":[\"DK1\",\"DK2\"]}", Charsets.UTF_8.name())
        val requestUrl =
            "https://api.energidataservice.dk/dataset/CO2Emis?start=now-P${daysBack}D&filter=$filterJson&sort=Minutes5UTC%20asc&limit=0"
        
        Log.d(TAG, "Fetching from: $requestUrl")

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }

        return connection.useAndRead { body ->
            Log.d(TAG, "Response body length: ${body.length} chars")
            val json = JSONObject(body)
            val records = json.optJSONArray("records") ?: JSONArray()
            Log.d(TAG, "Parsed ${records.length()} raw 5-min records from API")
            val hourlyStats = TreeMap<LocalDateTime, DoubleArray>()

            for (i in 0 until records.length()) {
                val item = records.optJSONObject(i) ?: continue
                val rawTimestamp = item.optString("Minutes5UTC")
                val emission = item.optDouble("CO2Emission", Double.NaN)

                if (rawTimestamp.isBlank() || emission.isNaN()) {
                    continue
                }

                val hourBucket = parseToHourBucket(rawTimestamp) ?: continue
                val stats = hourlyStats.getOrPut(hourBucket) { doubleArrayOf(0.0, 0.0) }
                stats[0] += emission
                stats[1] += 1.0
            }

            val output = JSONArray()
            for ((hour, stats) in hourlyStats) {
                if (stats[1] <= 0.0) {
                    continue
                }
                val avg = stats[0] / stats[1]
                output.put(
                    JSONObject()
                        .put("datetimeUtc", hour.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .put("carbonIntensity", avg)
                )
            }

            output
        }
    }

    private fun parseToHourBucket(rawTimestamp: String): LocalDateTime? {
        return try {
            val dt = try {
                OffsetDateTime.parse(rawTimestamp).toLocalDateTime()
            } catch (_: DateTimeParseException) {
                LocalDateTime.parse(rawTimestamp)
            }
            dt.withMinute(0).withSecond(0).withNano(0)
        } catch (_: Exception) {
            null
        }
    }

    private inline fun HttpURLConnection.useAndRead(onSuccess: (String) -> JSONArray): JSONArray {
        return try {
            Log.d(TAG, "Connecting to API...")
            connect()
            Log.d(TAG, "HTTP response code: $responseCode")
            val stream = if (responseCode in 200..299) inputStream else errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                Log.e(TAG, "HTTP error: $responseCode | body: ${body.take(500)}")
                throw IllegalStateException("Carbon API request failed with HTTP $responseCode")
            }
            Log.d(TAG, "API response received successfully")
            onSuccess(body)
        } catch (e: Exception) {
            Log.e(TAG, "Request exception: ${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val TAG = "CarbonDataRepository"
        private const val PREF_NAME = "carbon_data_cache"
        private const val KEY_CARBON_SERIES = "hourly_carbon_series"
        private const val KEY_LAST_REFRESH_MS = "last_refresh_ms"
    }
}
