package com.example.hamstertravelsapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.hamstertravelsapp.databinding.FragmentMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.SphericalUtil
import kotlin.math.sqrt

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HamsterViewModel by activityViewModels()
    private lateinit var mMap: GoogleMap
    private var haveDrawnMap = false
    
    private var hamsterMarker: Marker? = null
    private var hamsterCircle: Circle? = null
    private var cachedHamsterIcon: BitmapDescriptor? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        viewModel.fetchCurrentDistance()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isRotateGesturesEnabled = false
        mMap.uiSettings.isCompassEnabled = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Shift map UI elements (like the compass and Google logo) so they aren't 
            // obscured by the status bar or notch. We add a small extra margin (16dp) 
            // for better visual breathing room.
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            mMap.setPadding(systemBars.left, systemBars.top + extraPadding, systemBars.right, 0)
            insets
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLng(viewModel.home))

        drawPrevious()

        viewModel.currentDistance.observe(viewLifecycleOwner) { radius ->
            if (radius > 0) {
                drawTotalDistance(viewModel.home, radius)
            }
        }
        haveDrawnMap = true
    }

    private fun drawPrevious() {
        val icon = getHamsterIcon()
        viewModel.previousHamsters.forEach { (name, value) ->
            val circleOptions = CircleOptions()
                .center(viewModel.home)
                .radius(value)
                .strokeColor(Color.GRAY)

            mMap.addCircle(circleOptions)

            val labelPosition = SphericalUtil.computeOffset(viewModel.home, value, 90.0)
            mMap.addMarker(
                MarkerOptions()
                    .position(labelPosition)
                    .title(name)
                    .icon(icon),
            )
        }
    }

    private fun drawTotalDistance(home: LatLng, radius: Double) {
        hamsterCircle?.remove()
        hamsterMarker?.remove()

        val circleOptions = CircleOptions()
            .center(home)
            .radius(radius)

        hamsterCircle = mMap.addCircle(circleOptions)

        val labelPosition = SphericalUtil.computeOffset(home, radius, 90.0)
        hamsterMarker = mMap.addMarker(
            MarkerOptions()
                .position(labelPosition)
                .title(viewModel.currentHamsterName)
                .icon(getHamsterIcon()),
        )

        val cameraBounds = toBounds(home, radius)
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds, 100))
    }

    private fun getHamsterIcon(): BitmapDescriptor {
        cachedHamsterIcon?.let { return it }
        val size = (24 * resources.displayMetrics.density).toInt()
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = ContextCompat.getDrawable(requireContext(), R.mipmap.ic_hamster)
        drawable?.setBounds(0, 0, size, size)
        drawable?.draw(canvas)
        val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        cachedHamsterIcon = icon
        return icon
    }

    private fun toBounds(center: LatLng, radiusInMeters: Double): LatLngBounds {
        val distanceFromCenterToCorner = radiusInMeters * sqrt(2.0)
        val southwestCorner: LatLng =
            SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 225.0)
        val northeastCorner: LatLng =
            SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 45.0)
        return LatLngBounds(southwestCorner, northeastCorner)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}