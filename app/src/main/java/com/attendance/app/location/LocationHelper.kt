package com.attendance.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val readableAddress: String
)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationHelper"
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.e(TAG, "Location permission not granted by user")
            return@withContext LocationData(0.0, 0.0, "Location permission not granted")
        }

        var resolvedLocation: Location? = null

        // Strategy 1: Check fresh lastKnownLocation from FusedLocationProviderClient
        try {
            val lastLoc = fusedLocationClient.lastLocation.await()
            if (lastLoc != null) {
                val ageMillis = System.currentTimeMillis() - lastLoc.time
                Log.d(TAG, "Fused lastLocation retrieved: (${lastLoc.latitude}, ${lastLoc.longitude}) age: ${ageMillis}ms")
                if (ageMillis < 5 * 60 * 1000) { // Younger than 5 minutes is great
                    resolvedLocation = lastLoc
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fused lastLocation query failed: ${e.message}")
        }

        // Strategy 2: Active getCurrentLocation with a 3.5-second timeout
        if (resolvedLocation == null) {
            try {
                val priority = if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
                val cts = CancellationTokenSource()
                resolvedLocation = withTimeoutOrNull(3500) {
                    fusedLocationClient.getCurrentLocation(priority, cts.token).await()
                }
                if (resolvedLocation == null) {
                    cts.cancel()
                    Log.d(TAG, "Fused getCurrentLocation timed out, falling back")
                } else {
                    Log.d(TAG, "Fused getCurrentLocation succeeded: (${resolvedLocation.latitude}, ${resolvedLocation.longitude})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fused getCurrentLocation exception: ${e.message}")
            }
        }

        // Strategy 3: Check Android System LocationManager (GPS and Network providers)
        if (resolvedLocation == null) {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    val providers = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    )
                    for (provider in providers) {
                        try {
                            if (lm.isProviderEnabled(provider)) {
                                val loc = lm.getLastKnownLocation(provider)
                                if (loc != null) {
                                    if (resolvedLocation == null || loc.time > resolvedLocation.time) {
                                        resolvedLocation = loc
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            // permission restriction
                        }
                    }
                }
                if (resolvedLocation != null) {
                    Log.d(TAG, "LocationManager fallback succeeded: (${resolvedLocation.latitude}, ${resolvedLocation.longitude})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "LocationManager fallback exception: ${e.message}")
            }
        }

        // Strategy 4: Fall back to ANY cached fused location even if older
        if (resolvedLocation == null) {
            try {
                resolvedLocation = fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                Log.w(TAG, "Fallback lastLocation exception: ${e.message}")
            }
        }

        val lat = resolvedLocation?.latitude ?: 0.0
        val lng = resolvedLocation?.longitude ?: 0.0

        Log.i(TAG, "Final recorded location: Lat=$lat, Lng=$lng")

        val addressStr = if (lat != 0.0 && lng != 0.0) {
            getReadableAddress(lat, lng)
        } else {
            "Location unavailable (GPS signal pending)"
        }

        LocationData(
            latitude = lat,
            longitude = lng,
            readableAddress = addressStr
        )
    }

    private suspend fun getReadableAddress(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val fallback = "%.5f, %.5f".format(latitude, longitude)
        try {
            withTimeoutOrNull(3000) {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val addr = addresses.firstOrNull()?.let { formatAddress(it) } ?: fallback
                                continuation.resume(addr)
                            }

                            override fun onError(errorMessage: String?) {
                                continuation.resume(fallback)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    addresses?.firstOrNull()?.let { formatAddress(it) } ?: fallback
                }
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()
        if (!address.subLocality.isNullOrBlank()) {
            parts.add(address.subLocality)
        } else if (!address.thoroughfare.isNullOrBlank()) {
            parts.add(address.thoroughfare)
        }
        if (!address.locality.isNullOrBlank()) {
            parts.add(address.locality)
        }
        if (!address.adminArea.isNullOrBlank()) {
            parts.add(address.adminArea)
        }
        return if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0) ?: "%.5f, %.5f".format(address.latitude, address.longitude)
    }
}
