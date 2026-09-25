package com.example.mocklocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices

/**
 * Mocks location on BOTH stacks:
 *  1. LocationManager test providers (GPS + Network) — for apps using
 *     LocationManager directly.
 *  2. Google Play Services Fused provider via setMockMode/setMockLocation —
 *     for apps using FusedLocationProviderClient (e.g. Google Maps).
 * Without #2, most modern apps keep showing the real location.
 *
 * Call [startMocking] once, then [updateLocation] repeatedly (or just once —
 * the service loops it every second so the mock stays alive).
 */
class LocationMockManager(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private var fusedMockEnabled = false

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    fun startMocking(lat: Double, lng: Double, accuracy: Float = 3f) {
        for (provider in providers) {
            try {
                // Remove stale test provider if it already exists
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) { /* not registered yet */ }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        provider,
                        false, // requiresNetwork
                        false, // requiresSatellite
                        false, // requiresCell
                        false, // hasMonetaryCost
                        false, // supportsAltitude
                        false, // supportsSpeed
                        false, // supportsBearing
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, false, false, false,
                        Criteria.POWER_LOW, Criteria.ACCURACY_FINE
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing mock permission — select app in Developer options", e)
                throw MockNotSelectedException(e)
            } catch (e: IllegalArgumentException) {
                // Provider already exists as real provider; try to enable anyway
                Log.w(TAG, "addTestProvider failed for $provider: ${e.message}")
                try {
                    locationManager.setTestProviderEnabled(provider, true)
                } catch (_: Exception) {}
            }
        }
        // Fused provider (Google Play Services) — without this, apps like
        // Google Maps keep showing the real location.
        try {
            fusedClient.setMockMode(true).addOnFailureListener { e ->
                Log.e(TAG, "Fused setMockMode(true) failed", e)
            }
            fusedMockEnabled = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Fused mock denied — select app in Developer options", e)
            throw MockNotSelectedException(e)
        } catch (e: Exception) {
            // Play Services missing/outdated — LocationManager mock still applies
            Log.w(TAG, "Fused mock unavailable: ${e.message}")
            fusedMockEnabled = false
        }
        updateLocation(lat, lng, accuracy)
    }

    @SuppressLint("MissingPermission")
    fun updateLocation(lat: Double, lng: Double, accuracy: Float = 3f) {
        for (provider in providers) {
            try {
                locationManager.setTestProviderLocation(
                    provider, buildMockLocation(provider, lat, lng, accuracy)
                )
            } catch (e: SecurityException) {
                throw MockNotSelectedException(e)
            } catch (e: Exception) {
                Log.w(TAG, "setTestProviderLocation failed for $provider", e)
            }
        }
        if (fusedMockEnabled) {
            try {
                fusedClient.setMockLocation(
                    buildMockLocation(LocationManager.GPS_PROVIDER, lat, lng, accuracy)
                ).addOnFailureListener { e ->
                    Log.w(TAG, "Fused setMockLocation failed", e)
                }
            } catch (e: SecurityException) {
                throw MockNotSelectedException(e)
            } catch (e: Exception) {
                Log.w(TAG, "Fused setMockLocation failed", e)
            }
        }
    }

    private fun buildMockLocation(
        provider: String, lat: Double, lng: Double, accuracy: Float
    ): Location = Location(provider).apply {
        latitude = lat
        longitude = lng
        altitude = 0.0
        this.accuracy = accuracy
        bearing = 0f
        speed = 0f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bearingAccuracyDegrees = 0.1f
            speedAccuracyMetersPerSecond = 0.1f
            verticalAccuracyMeters = 0.1f
        }
    }

    fun stopMocking() {
        if (fusedMockEnabled) {
            try {
                fusedClient.setMockMode(false)
            } catch (_: Exception) {}
            fusedMockEnabled = false
        }
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
            } catch (_: Exception) {}
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
    }

    /** True if we can add a test provider without SecurityException. */
    fun isMockSelected(): Boolean {
        return try {
            // AppOps check: OPSTR_MOCK_LOCATION
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    class MockNotSelectedException(cause: Throwable) : SecurityException(
        "Select this app under Settings > Developer options > Select mock location app",
        cause
    )

    companion object {
        private const val TAG = "LocationMockManager"
    }
}
