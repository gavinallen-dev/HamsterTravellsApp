package com.example.hamstertravelsapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HamsterViewModel(application: Application) : AndroidViewModel(application) {

    val home = LatLng(51.5074, -0.1278)
    val currentHamsterName = "You"
    val previousHamsters = mapOf(
        "Cheerio" to 150000.0,
        "Nibbles" to 450000.0,
    )

    private val _currentDistance = MutableLiveData<Double>()
    val currentDistance: LiveData<Double> = _currentDistance

    private val _leaderboard = MutableLiveData<List<Landmark>>()
    val leaderboard: LiveData<List<Landmark>> = _leaderboard

    data class Landmark(
        val name: String,
        val distance: Double,
        val flag: String,
        val isCurrent: Boolean = false,
    )

    init {
        fetchCurrentDistance()
    }

    fun updateLeaderboard() {
        val currentDist = _currentDistance.value ?: 0.0
        val entries = mutableListOf(
            Landmark("London", 0.0, "🇬🇧"),
            Landmark("Paris", 344000.0, "🇫🇷"),
            Landmark("Berlin", 932000.0, "🇩🇪"),
            Landmark("Rome", 1433000.0, "🇮🇹"),
            Landmark("Madrid", 1263000.0, "🇪🇸"),
            Landmark("You", currentDist, "🐹", isCurrent = true),
        )
        _leaderboard.value = entries.sortedBy { it.distance }
    }

    data class HistoryPoint(val timestamp: Long, val totalDistance: Double?, val dailyDistance: Double?)
    private val _history = MutableLiveData<List<HistoryPoint>>()
    val history: LiveData<List<HistoryPoint>> = _history

    fun fetchHistory(days: Int) {
        val queue = Volley.newRequestQueue(getApplication())
        val url = if (days >= 30) {
            "https://api.thingspeak.com/channels/1061276/feeds.json?results=8000"
        } else {
            "https://api.thingspeak.com/channels/1061276/feeds.json?days=$days"
        }

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val feeds = response.getJSONArray("feeds")
                    if (days >= 30 && feeds.length() > 0) {
                        val oldestFeed = feeds.getJSONObject(0)
                        val oldestCreated = oldestFeed.getString("created_at")
                        val url2 = "https://api.thingspeak.com/channels/1061276/feeds.json?results=8000&end=$oldestCreated"

                        val request2 = JsonObjectRequest(
                            Request.Method.GET,
                            url2,
                            null,
                            { response2 ->
                                try {
                                    val feeds2 = response2.getJSONArray("feeds")
                                    val combinedFeeds = JSONArray()
                                    for (i in 0 until feeds2.length()) {
                                        combinedFeeds.put(feeds2.getJSONObject(i))
                                    }
                                    for (i in 0 until feeds.length()) {
                                        combinedFeeds.put(feeds.getJSONObject(i))
                                    }
                                    processFeeds(combinedFeeds, days)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    processFeeds(feeds, days)
                                }
                            },
                            { error ->
                                Log.e("HamsterViewModel", "Error fetching history batch 2: $error")
                                processFeeds(feeds, days)
                            }
                        )
                        queue.add(request2)
                    } else {
                        processFeeds(feeds, days)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        ) { error ->
            Log.e("HamsterViewModel", "Error fetching history: $error")
        }

        queue.add(request)
    }

    private fun processFeeds(feeds: JSONArray, days: Int) {
        val list = mutableListOf<HistoryPoint>()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")

        val cutoffTime = if (days > 0 && days < 365) {
            System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        } else {
            0L
        }

        for (i in 0 until feeds.length()) {
            val feed = feeds.getJSONObject(i)
            val field8 = feed.optString("field8")
            val field5 = feed.optString("field5")

            val totalDistance = if ((field8 != "null") && field8.isNotEmpty()) field8.toDouble() else null
            val dailyDistance = if ((field5 != "null") && field5.isNotEmpty()) field5.toDouble() else null

            if ((totalDistance != null) || (dailyDistance != null)) {
                val timeStr = feed.getString("created_at")
                val time = sdf.parse(timeStr)?.time ?: 0L
                if (days == 365 || time >= cutoffTime) {
                    list.add(HistoryPoint(time, totalDistance, dailyDistance))
                }
            }
        }
        _history.postValue(list)
    }

    fun fetchCurrentDistance() {
        val queue = Volley.newRequestQueue(getApplication())
        val url = "https://api.thingspeak.com/channels/1061276/fields/8/last.json"

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val distance = response.getString("field8").toDouble()
                    _currentDistance.value = distance
                    updateLeaderboard()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
        ) { error ->
            Log.e("HamsterViewModel", "Error fetching distance: $error")
        }

        queue.add(request)
    }
}
