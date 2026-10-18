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
                // 1. Fetch daily metrics from new Daily Channel (3540677)
                val urlString = "https://api.thingspeak.com/channels/3540677/feeds.json?api_key=51BNZU5YHL8CMLXK&days=8"
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

                    val dailyStr = if (feed.has("field2") && feed.optString("field2") != "null") feed.optString("field2") else feed.optString("field5", "")
                    val dailyVal = if (dailyStr.isNotEmpty() && dailyStr != "null") dailyStr.toDoubleOrNull() ?: 0.0 else 0.0

                    if (feedTime >= twelveHoursAgo) {
                        if (dailyVal > lastNightPeak) {
                            lastNightPeak = dailyVal
                        }
                    }

                    if (dailyVal > (dailyPeaksMap[dateStr] ?: 0.0)) {
                        dailyPeaksMap[dateStr] = dailyVal
                    }
                }

                if ((lastNightPeak == 0.0) && (feeds.length() > 0)) {
                    for (i in (feeds.length() - 1) downTo maxOf(0, feeds.length() - 50)) {
                        val feed = feeds.getJSONObject(i)
                        val dailyStr = if (feed.has("field2") && feed.optString("field2") != "null") feed.optString("field2") else feed.optString("field5", "")
                        val dailyVal = if (dailyStr.isNotEmpty() && dailyStr != "null") dailyStr.toDoubleOrNull() ?: 0.0 else 0.0
                        if (dailyVal > 0.0) {
                            lastNightPeak = dailyVal
                            break
                        }
                    }
                }

                // 2. Fetch latest total cumulative distance from Daily Channel (3540677, Field 4) or Transaction Channel (1061276, Field 8)
                try {
                    val totalUrl = URL("https://api.thingspeak.com/channels/3540677/fields/4/last.json?api_key=51BNZU5YHL8CMLXK")
                    val totalConn = totalUrl.openConnection() as HttpURLConnection
                    totalConn.requestMethod = "GET"
                    totalConn.connectTimeout = 10000
                    totalConn.readTimeout = 10000
                    if (totalConn.responseCode == HttpURLConnection.HTTP_OK) {
                        val totalReader = BufferedReader(InputStreamReader(totalConn.inputStream))
                        val totalSb = StringBuilder()
                        var totalLine: String?
                        while (totalReader.readLine().also { totalLine = it } != null) {
                            totalSb.append(totalLine)
                        }
                        totalReader.close()
                        val totalJson = JSONObject(totalSb.toString())
                        val field4Str = totalJson.optString("field4", "")
                        totalDistance = field4Str.toDoubleOrNull() ?: 0.0
                    }
                    totalConn.disconnect()
                } catch (e: Exception) {
                    Log.e("HamsterWidgetWorker", "Failed to fetch cumulative total distance from daily channel", e)
                }

                if (totalDistance == 0.0) {
                    try {
                        val totalUrl = URL("https://api.thingspeak.com/channels/1061276/fields/8/last.json")
                        val totalConn = totalUrl.openConnection() as HttpURLConnection
                        totalConn.requestMethod = "GET"
                        totalConn.connectTimeout = 10000
                        totalConn.readTimeout = 10000
                        if (totalConn.responseCode == HttpURLConnection.HTTP_OK) {
                            val totalReader = BufferedReader(InputStreamReader(totalConn.inputStream))
                            val totalSb = StringBuilder()
                            var totalLine: String?
                            while (totalReader.readLine().also { totalLine = it } != null) {
                                totalSb.append(totalLine)
                            }
                            totalReader.close()
                            val totalJson = JSONObject(totalSb.toString())
                            val field8Str = totalJson.optString("field8", "")
                            totalDistance = field8Str.toDoubleOrNull() ?: 0.0
                        }
                        totalConn.disconnect()
                    } catch (e: Exception) {
                        Log.e("HamsterWidgetWorker", "Failed to fetch cumulative total distance from transaction channel", e)
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
