package com.example.hamstertravelsapp

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Refreshes the widget from the daily channel: field2 carries the 12 hour rolling distance and
 * field4 the cumulative total. Figures are reported against the current reporting window (see
 * [ReportingWindow]), with the preceding windows used as the comparison baseline.
 */
class HamsterWidgetWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        val KEY_LAST_NIGHT_PEAK = doublePreferencesKey("last_night_peak")
        val KEY_TOTAL_DISTANCE = doublePreferencesKey("total_distance")
        val KEY_PERCENTAGE = doublePreferencesKey("percentage")

        /**
         * created_at of the newest reading behind the displayed figures, preformatted in UTC and
         * split so the widget can stack the date over the time.
         */
        val KEY_DATA_DATE = stringPreferencesKey("data_date")
        val KEY_DATA_TIME = stringPreferencesKey("data_time")

        private const val DAILY_CHANNEL_ID = "3540677"
        private const val DAILY_READ_API_KEY = "51BNZU5YHL8CMLXK"

        /** Windows before the reporting window that form the trend baseline. */
        private const val BASELINE_WINDOWS = 7

        private const val TAG = "HamsterWidgetWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val windowEnd = ReportingWindow.reportingWindowEnd()
            val rangeStart = ReportingWindow.startOfWindow(windowEnd, BASELINE_WINDOWS)

            val url = "https://api.thingspeak.com/channels/$DAILY_CHANNEL_ID/feeds.json" +
                "?api_key=$DAILY_READ_API_KEY" +
                "&results=8000" +
                "&start=${ReportingWindow.toThingSpeakParam(rangeStart)}" +
                "&end=${ReportingWindow.toThingSpeakParam(windowEnd)}"

            val body = httpGet(url) ?: return@withContext Result.retry()
            val feeds = JSONObject(body).optJSONArray("feeds")
            if (feeds == null) {
                Log.e(TAG, "Daily channel response had no feeds array")
                return@withContext Result.failure()
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val peakByWindow = mutableMapOf<Int, Double>()
            var maxTotal = 0.0
            var latestReading = 0L

            for (i in 0 until feeds.length()) {
                val feed = feeds.optJSONObject(i) ?: continue
                val timestamp = sdf.parse(feed.optString("created_at"))?.time ?: continue

                val total = feed.positiveDouble("field4")
                val daily = feed.positiveDouble("field2")
                if ((total == null) && (daily == null)) continue

                // Track the newest reading behind the figures, so the widget can stamp what it
                // is showing rather than when the worker happened to run.
                latestReading = maxOf(latestReading, timestamp)

                if (total != null) {
                    maxTotal = maxOf(maxTotal, total)
                }

                if (daily == null) continue
                val windowIndex = ReportingWindow.windowIndexOf(timestamp, windowEnd)
                if (windowIndex < 0) continue
                peakByWindow[windowIndex] = maxOf(peakByWindow[windowIndex] ?: 0.0, daily)
            }

            if (peakByWindow.isEmpty() && (maxTotal == 0.0)) {
                Log.e(TAG, "No usable readings in the reporting range")
                return@withContext Result.failure()
            }

            // Index 0 is the reporting window; later indices are the baseline windows behind it.
            val reportingPeak = peakByWindow[0] ?: 0.0
            val baselinePeaks = (1..BASELINE_WINDOWS).mapNotNull { index -> peakByWindow[index] }
            val baselineAverage = if (baselinePeaks.isNotEmpty()) {
                baselinePeaks.average()
            } else {
                reportingPeak
            }

            val percentageValue = if (baselineAverage > 0.0) {
                (reportingPeak / baselineAverage) * 100.0
            } else {
                100.0
            }

            // Reported in UTC to match the midday window boundaries and the in-app chart axes.
            val hasReading = latestReading > 0L
            val dataDate = if (hasReading) utcFormat("MMM d", latestReading) else "--"
            val dataTime = if (hasReading) utcFormat("HH:mm", latestReading) else ""

            val manager = GlanceAppWidgetManager(applicationContext)
            for (glanceId in manager.getGlanceIds(HamsterWidget::class.java)) {
                updateAppWidgetState(
                    applicationContext,
                    PreferencesGlanceStateDefinition,
                    glanceId,
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[KEY_LAST_NIGHT_PEAK] = reportingPeak
                        this[KEY_TOTAL_DISTANCE] = maxTotal
                        this[KEY_PERCENTAGE] = percentageValue
                        this[KEY_DATA_DATE] = dataDate
                        this[KEY_DATA_TIME] = dataTime
                    }
                }
                HamsterWidget().update(applicationContext, glanceId)
            }

            Log.d(
                TAG,
                "Worker success: window peak=$reportingPeak, total=$maxTotal, " +
                    "baseline=$baselineAverage over ${baselinePeaks.size} windows, " +
                    "data as at $dataDate $dataTime",
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.failure()
        }
    }

    /** Formats [millis] in UTC, so displayed times line up with the midday window boundaries. */
    private fun utcFormat(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))

    /** Returns the response body, or null when the request did not succeed. */
    private fun httpGet(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Request failed with HTTP ${connection.responseCode}")
                return null
            }
            return BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Reads a ThingSpeak field, treating missing, null and non-positive values as absent. */
    private fun JSONObject.positiveDouble(field: String): Double? {
        val raw = optString(field)
        if (raw.isEmpty() || raw == "null") return null
        return raw.toDoubleOrNull()?.takeIf { value -> value > 0.0 }
    }
}
