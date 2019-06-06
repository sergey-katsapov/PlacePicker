package com.sucho.placepicker

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.CoroutineContext

class PlacePickerActivity : AppCompatActivity(), OnMapReadyCallback, CoroutineScope {

  companion object {
    private const val TAG = "PlacePickerActivity"
  }

  private lateinit var job: Job
  override val coroutineContext: CoroutineContext
    get() = job + Dispatchers.Main

  private lateinit var map: GoogleMap
  private lateinit var markerImage: ImageView
  private lateinit var bottomSheet: CurrentPlaceSelectionBottomSheet

  private var latitude = Constants.DEFAULT_LATITUDE
  private var longitude = Constants.DEFAULT_LONGITUDE
  private var showLatLong = true
  private var zoom = Constants.DEFAULT_ZOOM
  private var addressRequired: Boolean = true
  private var shortAddress = ""
  private var fullAddress = ""
  var addresses: List<Address>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_place_picker)
    getIntentData()
    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    val mapFragment = supportFragmentManager
        .findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    bottomSheet = findViewById(R.id.bottom_sheet)
    markerImage = findViewById(R.id.marker_image_view)

    findViewById<FloatingActionButton>(R.id.place_chosen_button).setOnClickListener {
      if (addresses != null) {
        val addressData = AddressData(latitude, longitude, addresses)
        val returnIntent = Intent()
        returnIntent.putExtra(Constants.ADDRESS_INTENT, addressData)
        setResult(RESULT_OK, returnIntent)
        finish()
      } else {
        if(!addressRequired) {
          val addressData = AddressData(latitude, longitude, null)
          val returnIntent = Intent()
          returnIntent.putExtra(Constants.ADDRESS_INTENT, addressData)
          setResult(RESULT_OK, returnIntent)
          finish()
        } else {
          Toast.makeText(this@PlacePickerActivity, R.string.no_address, Toast.LENGTH_LONG).show()
        }
      }
    }

    job = Job()
  }

  private fun getIntentData() {
    latitude = intent.getDoubleExtra(Constants.INITIAL_LATITUDE_INTENT, Constants.DEFAULT_LATITUDE)
    longitude = intent.getDoubleExtra(Constants.INITIAL_LONGITUDE_INTENT, Constants.DEFAULT_LONGITUDE)
    showLatLong = intent.getBooleanExtra(Constants.SHOW_LAT_LONG_INTENT, true)
    addressRequired = intent.getBooleanExtra(Constants.ADDRESS_REQUIRED_INTENT, true)
    zoom = intent.getFloatExtra(Constants.INITIAL_ZOOM_INTENT, Constants.DEFAULT_ZOOM)
  }

  override fun onMapReady(googleMap: GoogleMap) {
    map = googleMap

    map.setOnCameraMoveStartedListener {
      if (markerImage.translationY == 0f) {
        markerImage.animate()
            .translationY(-75f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(250)
            .start()
        if (bottomSheet.isShowing) {
          bottomSheet.dismissPlaceDetails()
        }
      }
    }

    map.setOnCameraIdleListener {
      markerImage.animate()
          .translationY(0f)
          .setInterpolator(OvershootInterpolator())
          .setDuration(250)
          .start()

      bottomSheet.showLoadingBottomDetails()

      launch {
        val latLng = map.cameraPosition.target
        latitude = latLng.latitude
        longitude = latLng.longitude
        async(Dispatchers.Default) {
          getAddressForLocation()
        }.await()
        bottomSheet.setPlaceDetails(latitude, longitude, shortAddress, fullAddress)
      }
    }
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
  }

  private fun getAddressForLocation() {
    setAddress(latitude, longitude)
  }

  private fun setAddress(
    latitude: Double,
    longitude: Double
  ) {
    val geoCoder = Geocoder(this, Locale.getDefault())
    try {
      val addresses = geoCoder.getFromLocation(latitude, longitude, 1)
      this.addresses = addresses
      return if (addresses != null && addresses.size != 0) {
        fullAddress = addresses[0].getAddressLine(
            0
        ) // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        shortAddress = generateFinalAddress(fullAddress).trim()
      } else {
        shortAddress = ""
        fullAddress = ""
      }
    } catch (e: Exception) {
      //Time Out in getting address
      Log.e(TAG, e.message)
      shortAddress = ""
      fullAddress = ""
      addresses = null
    }
  }

  private fun generateFinalAddress(
    address: String
  ): String {
    val s = address.split(",")
    return if (s.size >= 3) s[1] + "," + s[2] else if (s.size == 2) s[1] else s[0]
  }

  override fun onDestroy() {
    super.onDestroy()
    job.cancel()
  }
}