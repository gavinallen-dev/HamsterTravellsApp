package com.example.hamstertravellsapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.hamstertravellsapp.databinding.ActivityMapsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.SphericalUtil

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val home = LatLng(51.54264825288355, -0.2931393365066958)
    var radius = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startLoadContent(home)


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    var haveDrawnMap = false

    private fun startLoadContent(home: LatLng){
            val queue: RequestQueue = Volley.newRequestQueue(applicationContext)
            val url = "https://api.thingspeak.com/channels/1061276/fields/8/last.json"

            val request = JsonObjectRequest(Request.Method.GET, url, null, { response ->
                try {
                    radius = response.getString("field8").toDouble()

                    if (haveDrawnMap) {
                        drawTotalDistance(home, radius)
                    }
                } catch (e: Exception) {
                    // on below line we are
                    // handling our exception.
                    e.printStackTrace()
                }
            }, { error ->
                // this method is called when we get
                // any error while fetching data from our API
                Log.e("TAG", "RESPONSE IS $error")
                // in this case we are simply displaying a toast message.
                Toast.makeText(this, "Fail to get response", Toast.LENGTH_SHORT)
                    .show()
            })

            queue.add(request)
        }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val markerOptions = MarkerOptions()
            .position(home)
            .title("Home")

        mMap.addMarker(markerOptions)

        mMap.moveCamera(CameraUpdateFactory.newLatLng(home))

        if (radius > 0)
        {
            drawTotalDistance(home, radius)
        }
        haveDrawnMap = true
    }

    fun toBounds(center: LatLng?, radiusInMeters: Double): LatLngBounds {
        val distanceFromCenterToCorner = radiusInMeters * Math.sqrt(2.0)
        val southwestCorner: LatLng =
            SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 225.0)
        val northeastCorner: LatLng =
            SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 45.0)
        return LatLngBounds(southwestCorner, northeastCorner)
    }

    fun drawTotalDistance(home: LatLng, radius: Double){
        val circleOptions = CircleOptions()
            .center(home)
            .radius(radius) // In meters

        val circle = mMap.addCircle(circleOptions)

        mMap.setOnMapLoadedCallback {
            val cameraBounds = toBounds(home, radius)
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds, 10))
            mMap.moveCamera(CameraUpdateFactory.newLatLng(home))
        }
    }
}