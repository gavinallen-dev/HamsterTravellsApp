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

class HamsterWidgetWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    companion object {
        val KEY_LAST_NIGHT_PEAK = doublePreferencesKey("last_night_peak")
        val KEY_TOTAL_DISTANCE = doublePreferencesKey("total_distance")
        val KEY_PERCENTAGE = doublePreferencesKey("percentage")
        val KEY_LAST_UPDATE = stringPreferencesKey("last_update")
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://api.thingspeak.com/channels/1061276/feeds.json?days=8"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.retry()
                }

                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseStringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseStringBuilder.append(line)
                }
                reader.close()
                connection.disconnect()

                val jsonObject = JSONObject(responseStringBuilder.toString())
                val feeds = jsonObject.getJSONArray("feeds")

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")

                val currentTime = System.currentTimeMillis()
                val twelveHoursAgo = currentTime - (12 * 60 * 60 * 1000L)

                var lastNightPeak = 0.0
                var totalDistance = 0.0

                val dailyPeaksMap = mutableMapOf<String, Double>()

                for (i in 0 until feeds.length()) {
                    val feed = feeds.getJSONObject(i)
                    val timeStr = feed.optString("created_at", "")
                    if (timeStr.length < 10) continue

                    val feedTime = try {
                        sdf.parse(timeStr)?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }

                    // Offset the date calculation by 12 hours so that a full night run (e.g. 6pm to 6am)
                    // stays within a single "hamster day" bucket.
                    val bucketTime = feedTime - (12 * 60 * 60 * 1000L)
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(bucketTime))

                    val field5Str = feed.optString("field5", "")
                    val field5Val = if ((field5Str != "null") && field5Str.isNotEmpty()) field5Str.toDoubleOrNull() ?: 0.0 else 0.0

                    val field8Str = feed.optString("field8", "")
                    val field8Val = if ((field8Str != "null") && field8Str.isNotEmpty()) field8Str.toDoubleOrNull() ?: 0.0 else 0.0

                    if (feedTime >= twelveHoursAgo) {
                        if (field5Val > lastNightPeak) {
                            lastNightPeak = field5Val
                        }
                    }

                    if (field5Val > (dailyPeaksMap[dateStr] ?: 0.0)) {
                        dailyPeaksMap[dateStr] = field5Val
                    }

                    if (field8Val > 0.0) {
                        totalDistance = field8Val
                    }
                }

                if ((lastNightPeak == 0.0) && (feeds.length() > 0)) {
                    for (i in (feeds.length() - 1) downTo maxOf(0, feeds.length() - 50)) {
                        val feed = feeds.getJSONObject(i)
                        val field5Str = feed.optString("field5", "")
                        val field5Val = if ((field5Str != "null") && field5Str.isNotEmpty()) field5Str.toDoubleOrNull() ?: 0.0 else 0.0
                        if (field5Val > 0.0) {
                            lastNightPeak = field5Val
                            break
                        }
                    }
                }

                val sortedDates = dailyPeaksMap.keys.sorted()
                if (sortedDates.isEmpty()) return@withContext Result.failure()

                // Sort peaks in chronological order to find history
                val chronologicalPeaks = sortedDates.mapNotNull { dailyPeaksMap[it] }

                // The latest peak in history represents the latest active session
                val latestPeak = chronologicalPeaks.lastOrNull() ?: 0.0

                if (lastNightPeak == 0.0) {
                    lastNightPeak = latestPeak
                }

                // Exclude the last item (current/latest day) and compute the true 7-day average baseline from completed past nights
                val pastPeaksList = chronologicalPeaks.dropLast(1).filter { it > 10.0 }.takeLast(7)
                val baselineAverage = if (pastPeaksList.isNotEmpty()) {
                    pastPeaksList.average()
                } else {
                    lastNightPeak
                }

                Log.d("HamsterWidgetWorker", "Logic Debug: lastNightPeak=$lastNightPeak, baselineAvg=$baselineAverage, pastPeaksCount=${pastPeaksList.size}")

                // Calculate percentage of average (e.g. 105 for +5%, 97 for -3%)
                val percentageValue = if (baselineAverage > 0.0) {
                    (lastNightPeak / baselineAverage) * 100.0
                } else {
                    100.0
                }

                val updateTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                val manager = GlanceAppWidgetManager(applicationContext)
                val glanceIds = manager.getGlanceIds(HamsterWidget::class.java)

                for (glanceId in glanceIds) {
                    updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[KEY_LAST_NIGHT_PEAK] = lastNightPeak
                            this[KEY_TOTAL_DISTANCE] = totalDistance
                            this[KEY_PERCENTAGE] = percentageValue
                            this[KEY_LAST_UPDATE] = updateTimeStr
                        }
                    }
                    HamsterWidget().update(applicationContext, glanceId)
                }

                Log.d("HamsterWidgetWorker", "Worker success: Peak=$lastNightPeak, Total=$totalDistance, %=$percentageValue")
                Result.success()
            } catch (e: Exception) {
                Log.e("HamsterWidgetWorker", "Worker failed", e)
                Result.failure()
            }
        }
    }
}
