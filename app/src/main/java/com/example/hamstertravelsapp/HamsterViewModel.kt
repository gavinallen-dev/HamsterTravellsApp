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
    val currentHamsterName = "Liam"
    val previousHamsters = mapOf(
        "Lily" to 412353.0,
        "Tiffany" to 981041.0,
        "Claude" to 5062603.0,
        "Paddy" to 2445456.0,
    )

    private val cities = mapOf(
        // Europe
        "London" to ("🇬🇧" to LatLng(51.5074, -0.1278)),
        "Paris" to ("🇫🇷" to LatLng(48.8566, 2.3522)),
        "Berlin" to ("🇩🇪" to LatLng(52.5200, 13.4050)),
        "Rome" to ("🇮🇹" to LatLng(41.9028, 12.4964)),
        "Madrid" to ("🇪🇸" to LatLng(40.4168, -3.7038)),
        "Amsterdam" to ("🇳🇱" to LatLng(52.3676, 4.9041)),
        "Brussels" to ("🇧🇪" to LatLng(50.8503, 4.3517)),
        "Vienna" to ("🇦🇹" to LatLng(48.2082, 16.3738)),
        "Prague" to ("🇨🇿" to LatLng(50.0755, 14.4378)),
        "Warsaw" to ("🇵🇱" to LatLng(52.2297, 21.0122)),
        "Athens" to ("🇬🇷" to LatLng(37.9838, 23.7275)),
        "Lisbon" to ("🇵🇹" to LatLng(38.7223, -9.1393)),
        "Stockholm" to ("🇸🇪" to LatLng(59.3293, 18.0686)),
        "Oslo" to ("🇳🇴" to LatLng(59.9139, 10.7522)),
        "Copenhagen" to ("🇩🇰" to LatLng(55.6761, 12.5683)),
        "Helsinki" to ("🇫🇮" to LatLng(60.1699, 24.9384)),
        "Dublin" to ("🇮🇪" to LatLng(53.3498, -6.2603)),
        "Budapest" to ("🇭🇺" to LatLng(47.4979, 19.0402)),
        "Bucharest" to ("🇷🇴" to LatLng(44.4268, 26.1025)),
        "Sofia" to ("🇧🇬" to LatLng(42.6977, 23.3219)),
        "Belgrade" to ("🇷🇸" to LatLng(44.7866, 20.4489)),
        "Zagreb" to ("🇭🇷" to LatLng(45.8150, 15.9819)),
        "Bratislava" to ("🇸🇰" to LatLng(48.1486, 17.1077)),
        "Ljubljana" to ("🇸🇮" to LatLng(46.0569, 14.5058)),
        "Tallinn" to ("🇪🇪" to LatLng(59.4370, 24.7536)),
        "Riga" to ("🇱🇻" to LatLng(56.9496, 24.1052)),
        "Vilnius" to ("🇱🇹" to LatLng(54.6872, 25.2797)),
        "Reykjavik" to ("🇮🇸" to LatLng(64.1265, -21.8174)),
        "Bern" to ("🇨🇭" to LatLng(46.9480, 7.4474)),
        "Monaco" to ("🇲🇨" to LatLng(43.7384, 7.4246)),
        "Vatican City" to ("🇻🇦" to LatLng(41.9029, 12.4534)),
        "San Marino" to ("🇸🇲" to LatLng(43.9424, 12.4578)),
        "Vaduz" to ("🇱🇮" to LatLng(47.1410, 9.5209)),
        "Luxembourg" to ("🇱🇺" to LatLng(49.6117, 6.1319)),
        // Asia
        "Tokyo" to ("🇯🇵" to LatLng(35.6895, 139.6917)),
        "Beijing" to ("🇨🇳" to LatLng(39.9042, 116.4074)),
        "Seoul" to ("🇰🇷" to LatLng(37.5665, 126.9780)),
        "New Delhi" to ("🇮🇳" to LatLng(28.6139, 77.2090)),
        "Bangkok" to ("🇹🇭" to LatLng(13.7563, 100.5018)),
        "Jakarta" to ("🇮🇩" to LatLng(-6.2088, 106.8456)),
        "Manila" to ("🇵🇭" to LatLng(14.5995, 120.9842)),
        "Hanoi" to ("🇻🇳" to LatLng(21.0285, 105.8542)),
        "Kuala Lumpur" to ("🇲🇾" to LatLng(3.1390, 101.6869)),
        "Singapore" to ("🇸🇬" to LatLng(1.3521, 103.8198)),
        "Tashkent" to ("🇺🇿" to LatLng(41.2995, 69.2401)),
        "Nur-Sultan" to ("🇰🇿" to LatLng(51.1605, 71.4704)),
        "Ulaanbaatar" to ("🇲🇳" to LatLng(47.8864, 106.9057)),
        "Tehran" to ("🇮🇷" to LatLng(35.6892, 51.3890)),
        "Baghdad" to ("🇮🇶" to LatLng(33.3152, 44.3661)),
        "Riyadh" to ("🇸🇦" to LatLng(24.7136, 46.6753)),
        "Abu Dhabi" to ("🇦🇪" to LatLng(24.4539, 54.3773)),
        "Doha" to ("🇶🇦" to LatLng(25.2854, 51.5310)),
        "Muscat" to ("🇴🇲" to LatLng(23.5859, 58.4059)),
        "Kuwait City" to ("🇰🇼" to LatLng(29.3759, 47.9774)),
        "Amman" to ("🇯🇴" to LatLng(31.9454, 35.9284)),
        "Damascus" to ("🇸🇾" to LatLng(33.5138, 36.2765)),
        "Beirut" to ("🇱🇧" to LatLng(33.8938, 35.5018)),
        "Jerusalem" to ("🇮🇱" to LatLng(31.7683, 35.2137)),
        "Ankara" to ("🇹🇷" to LatLng(39.9334, 32.8597)),
        "Baku" to ("🇦🇿" to LatLng(40.4093, 49.8671)),
        "Tbilisi" to ("🇬🇪" to LatLng(41.7151, 44.8271)),
        "Yerevan" to ("🇦🇲" to LatLng(40.1792, 44.5132)),
        "Islamabad" to ("🇵🇰" to LatLng(33.6844, 73.0479)),
        "Kabul" to ("🇦🇫" to LatLng(34.5553, 69.2075)),
        "Dhaka" to ("🇧🇩" to LatLng(23.8103, 90.4125)),
        "Colombo" to ("🇱🇰" to LatLng(6.9271, 79.8612)),
        "Kathmandu" to ("🇳🇵" to LatLng(27.7172, 85.3240)),
        "Thimphu" to ("🇧🇹" to LatLng(27.4728, 89.6393)),
        "Male" to ("🇲🇻" to LatLng(4.1755, 73.5093)),
        "Naypyidaw" to ("🇲🇲" to LatLng(19.7633, 96.0785)),
        "Vientiane" to ("🇱🇦" to LatLng(17.9757, 102.6331)),
        "Phnom Penh" to ("🇰🇭" to LatLng(11.5564, 104.9282)),
        "Dili" to ("🇹🇱" to LatLng(-8.5586, 125.5736)),
        // Africa
        "Cairo" to ("🇪🇬" to LatLng(30.0444, 31.2357)),
        "Tripoli" to ("🇱🇾" to LatLng(32.8872, 13.1913)),
        "Tunis" to ("🇹🇳" to LatLng(36.8065, 10.1815)),
        "Algiers" to ("🇩🇿" to LatLng(36.7538, 3.0588)),
        "Rabat" to ("🇲🇦" to LatLng(34.0209, -6.8416)),
        "Nouakchott" to ("🇲🇷" to LatLng(18.0735, -15.9582)),
        "Dakar" to ("🇸🇳" to LatLng(14.7167, -17.4677)),
        "Banjul" to ("🇬🇲" to LatLng(13.4549, -16.5790)),
        "Bissau" to ("🇬🇼" to LatLng(11.8817, -15.6178)),
        "Conakry" to ("🇬🇳" to LatLng(9.5092, -13.7122)),
        "Freetown" to ("🇸🇱" to LatLng(8.4844, -13.2344)),
        "Monrovia" to ("🇱🇷" to LatLng(6.3005, -10.7972)),
        "Yamoussoukro" to ("🇨🇮" to LatLng(6.8276, -5.2767)),
        "Accra" to ("🇬🇭" to LatLng(5.6037, -0.1870)),
        "Lome" to ("🇹🇬" to LatLng(6.1375, 1.2123)),
        "Porto-Novo" to ("🇧🇯" to LatLng(6.4969, 2.6289)),
        "Abuja" to ("🇳🇬" to LatLng(9.0765, 7.3986)),
        "N'Djamena" to ("🇹🇩" to LatLng(12.1348, 15.0557)),
        "Niamey" to ("🇳🇪" to LatLng(13.5116, 2.1254)),
        "Bamako" to ("🇲🇱" to LatLng(12.6392, -8.0029)),
        "Ouagadougou" to ("🇧🇫" to LatLng(12.3714, -1.5197)),
        "Khartoum" to ("🇸🇩" to LatLng(15.5007, 32.5599)),
        "Juba" to ("🇸🇸" to LatLng(4.8517, 31.5713)),
        "Addis Ababa" to ("🇪🇹" to LatLng(8.9806, 38.7578)),
        "Asmara" to ("🇪🇷" to LatLng(15.3229, 38.9251)),
        "Djibouti" to ("🇩🇯" to LatLng(11.5883, 43.1450)),
        "Mogadishu" to ("🇸🇴" to LatLng(2.0469, 45.3182)),
        "Nairobi" to ("🇰🇪" to LatLng(-1.2864, 36.8172)),
        "Kampala" to ("🇺🇬" to LatLng(0.3476, 32.5825)),
        "Kigali" to ("🇷🇼" to LatLng(-1.9441, 30.0619)),
        "Bujumbura" to ("🇧🇮" to LatLng(-3.3822, 29.3644)),
        "Dodoma" to ("🇹🇿" to LatLng(-6.1630, 35.7516)),
        "Victoria" to ("🇸🇨" to LatLng(-4.6191, 55.4513)),
        "Moroni" to ("🇰🇲" to LatLng(-11.7172, 43.2473)),
        "Antananarivo" to ("🇲🇬" to LatLng(-18.8792, 47.5079)),
        "Port Louis" to ("🇲🇺" to LatLng(-20.1609, 57.5012)),
        "Lilongwe" to ("🇲🇼" to LatLng(-13.9626, 33.7741)),
        "Lusaka" to ("🇿🇲" to LatLng(-15.3875, 28.3228)),
        "Harare" to ("🇿🇼" to LatLng(-17.8252, 31.0335)),
        "Maputo" to ("🇲🇿" to LatLng(-25.9692, 32.5732)),
        "Luanda" to ("🇦🇴" to LatLng(-8.8390, 13.2894)),
        "Brazzaville" to ("🇨🇬" to LatLng(-4.2634, 15.2429)),
        "Kinshasa" to ("🇨🇩" to LatLng(-4.4419, 15.2663)),
        "Libreville" to ("🇬🇦" to LatLng(0.4162, 9.4673)),
        "Malabo" to ("🇬🇶" to LatLng(3.7504, 8.7832)),
        "Yaounde" to ("🇨🇲" to LatLng(3.8480, 11.5021)),
        "Bangui" to ("🇨🇫" to LatLng(4.3947, 18.5583)),
        "Gaborone" to ("🇧🇼" to LatLng(-24.6282, 25.9231)),
        "Windhoek" to ("🇳🇦" to LatLng(-22.5609, 17.0658)),
        "Pretoria" to ("🇿🇦" to LatLng(-25.7479, 28.2293)),
        "Maseru" to ("🇱🇸" to LatLng(-29.3175, 27.4854)),
        "Mbabane" to ("🇸🇿" to LatLng(-26.3055, 31.1367)),
        // North America
        "Washington, D.C." to ("🇺🇸" to LatLng(38.9072, -77.0369)),
        "Ottawa" to ("🇨🇦" to LatLng(45.4215, -75.6972)),
        "Mexico City" to ("🇲🇽" to LatLng(19.4326, -99.1332)),
        "Havana" to ("🇨🇺" to LatLng(23.1136, -82.3666)),
        "Nassau" to ("🇧🇸" to LatLng(25.0475, -77.3404)),
        "Kingston" to ("🇯🇲" to LatLng(17.9714, -76.7936)),
        "Port-au-Prince" to ("🇭🇹" to LatLng(18.5392, -72.3350)),
        "Santo Domingo" to ("🇩🇴" to LatLng(18.4861, -69.9312)),
        "San Juan" to ("🇵🇷" to LatLng(18.4655, -66.1057)),
        "Guatemala City" to ("🇬🇹" to LatLng(14.6349, -90.5069)),
        "Belmopan" to ("🇧🇿" to LatLng(17.2510, -88.7590)),
        "San Salvador" to ("🇸🇻" to LatLng(13.6929, -89.2182)),
        "Tegucigalpa" to ("🇭🇳" to LatLng(14.0723, -87.1921)),
        "Managua" to ("🇳🇮" to LatLng(12.1150, -86.2362)),
        "San Jose" to ("🇨🇷" to LatLng(9.9281, -84.0907)),
        "Panama City" to ("🇵🇦" to LatLng(8.9833, -79.5167)),
        "Bridgetown" to ("🇧🇧" to LatLng(13.1132, -59.5988)),
        "Castries" to ("🇱🇨" to LatLng(14.0101, -60.9870)),
        "Kingstown" to ("🇻🇨" to LatLng(13.1600, -61.2248)),
        "St. George's" to ("🇬🇩" to LatLng(12.0561, -61.7488)),
        "Port of Spain" to ("🇹🇹" to LatLng(10.6667, -61.5167)),
        // South America
        "Brasilia" to ("🇧🇷" to LatLng(-15.7975, -47.8919)),
        "Buenos Aires" to ("🇦🇷" to LatLng(-34.6037, -58.3816)),
        "Santiago" to ("🇨🇱" to LatLng(-33.4489, -70.6693)),
        "Lima" to ("🇵🇪" to LatLng(-12.0464, -77.0428)),
        "Bogota" to ("🇨🇴" to LatLng(4.7110, -74.0721)),
        "Quito" to ("🇪🇨" to LatLng(-0.1807, -78.4678)),
        "Caracas" to ("🇻🇪" to LatLng(10.4806, -66.9036)),
        "Montevideo" to ("🇺🇾" to LatLng(-34.9011, -56.1645)),
        "Asuncion" to ("🇵🇾" to LatLng(-25.2637, -57.5759)),
        "La Paz" to ("🇧🇴" to LatLng(-16.4897, -68.1193)),
        "Sucre" to ("🇧🇴" to LatLng(-19.0333, -65.2627)),
        "Paramaribo" to ("🇸🇷" to LatLng(5.8520, -55.2038)),
        "Georgetown" to ("🇬🇾" to LatLng(6.8013, -58.1551)),
        // Oceania
        "Canberra" to ("🇦🇺" to LatLng(-35.2809, 149.1300)),
        "Wellington" to ("🇳🇿" to LatLng(-41.2865, 174.7762)),
        "Port Moresby" to ("🇵🇬" to LatLng(-9.4438, 147.1803)),
        "Suva" to ("🇫🇯" to LatLng(-18.1248, 178.4501)),
        "Honiara" to ("🇸🇧" to LatLng(-9.4456, 159.9729)),
        "Port Vila" to ("🇻🇺" to LatLng(-17.7333, 168.3273)),
        "Apia" to ("🇼🇸" to LatLng(-13.8333, -171.7667)),
        "Nuku'alofa" to ("🇹🇴" to LatLng(-21.1394, -175.2049)),
        "Palikir" to ("🇫🇲" to LatLng(6.9172, 158.1589)),
        "Majuro" to ("🇲🇭" to LatLng(7.1315, 171.3644)),
        "Funafuti" to ("🇹🇻" to LatLng(-8.5211, 179.1962)),
        "South Tarawa" to ("🇰🇮" to LatLng(1.3290, 172.9790)),
        "Yaren" to ("🇳🇷" to LatLng(-0.5477, 166.9163)),
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
        val list = mutableListOf<Landmark>()

        list.add(Landmark(name = "Home", distance = 0.0, flag = "🏠"))

        cities.forEach { (name, info) ->
            val (flag, latLng) = info
            val distance = com.google.maps.android.SphericalUtil.computeDistanceBetween(home, latLng)
            list.add(Landmark(name = name, distance = distance, flag = flag))
        }

        previousHamsters.forEach { (name, distance) ->
            list.add(Landmark(name = name, distance = distance, flag = "🐹"))
        }

        val currentDist = _currentDistance.value ?: 0.0
        list.add(
            Landmark(
                name = currentHamsterName,
                distance = currentDist,
                flag = "🐹",
                isCurrent = true,
            )
        )

        _leaderboard.value = list.sortedBy { (_, distance) -> distance }
    }

    companion object {
        private const val TRANSACTION_CHANNEL_ID = "1061276"
        private const val DAILY_CHANNEL_ID = "3540677"
        private const val DAILY_READ_API_KEY = "51BNZU5YHL8CMLXK"
    }

    data class HistoryPoint(val timestamp: Long, val totalDistance: Double?, val dailyDistance: Double?)
    private val _history = MutableLiveData<List<HistoryPoint>>()
    val history: LiveData<List<HistoryPoint>> = _history

    fun fetchHistory(days: Int) {
        val queue = Volley.newRequestQueue(getApplication())
        val url = if (days <= 7) {
            "https://api.thingspeak.com/channels/$TRANSACTION_CHANNEL_ID/feeds.json?days=$days"
        } else {
            "https://api.thingspeak.com/channels/$DAILY_CHANNEL_ID/feeds.json?api_key=$DAILY_READ_API_KEY&days=$days"
        }
        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                try {
                    val feeds = response.getJSONArray("feeds")
                    processFeeds(feeds, days)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            { error ->
                Log.e("HamsterViewModel", "Error fetching history: $error")
            }
        )
        queue.add(request)
    }

    private fun processFeeds(feeds: JSONArray, days: Int) {
        val list = mutableListOf<HistoryPoint>()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")

        val cutoffTime = if ((days in 1..364)) {
            System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000L)
        } else {
            0L
        }

        for (i in 0 until feeds.length()) {
            val feed = feeds.getJSONObject(i)
            val field4 = feed.optString("field4")
            val field8 = feed.optString("field8")
            val field2 = feed.optString("field2")
            val field5 = feed.optString("field5")

            val totalDistance = if ((field4 != "null") && field4.isNotEmpty()) field4.toDoubleOrNull()
                else if ((field8 != "null") && field8.isNotEmpty()) field8.toDoubleOrNull() else null

            val dailyDistance = if ((field2 != "null") && field2.isNotEmpty()) field2.toDoubleOrNull()
                else if ((field5 != "null") && field5.isNotEmpty()) field5.toDoubleOrNull() else null

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
        val url = "https://api.thingspeak.com/channels/$TRANSACTION_CHANNEL_ID/fields/8/last.json"

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
