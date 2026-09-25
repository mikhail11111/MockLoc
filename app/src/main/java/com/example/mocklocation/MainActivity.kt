package com.example.mocklocation

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etLat: EditText
    private lateinit var etLng: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var mockManager: LocationMockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mockManager = LocationMockManager(this)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        etLat = findViewById(R.id.etLat)
        etLng = findViewById(R.id.etLng)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)

        etLat.setText(prefs.getString(KEY_LAT, "40.712776"))
        etLng.setText(prefs.getString(KEY_LNG, "-74.005974"))

        findViewById<Button>(R.id.btnDevOptions).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        btnStart.setOnClickListener { startMocking() }
        btnStop.setOnClickListener { stopMocking() }
        findViewById<Button>(R.id.btnVerify).setOnClickListener { verifyMocking() }
        findViewById<Button>(R.id.btnIpCountry).setOnClickListener { detectIpCountry() }

        requestLocationPermissions()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        // Surface silent service failures (e.g. mock permission revoked)
        val lastError = prefs.getString(KEY_LAST_ERROR, null)
        if (lastError != null) {
            tvStatus.text = "ERROR: $lastError"
            Toast.makeText(this, lastError, Toast.LENGTH_LONG).show()
        } else {
            refreshStatus()
        }
    }

    private fun startMocking() {
        val lat = etLat.text.toString().toDoubleOrNull()
        val lng = etLng.text.toString().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "Enter valid lat (-90..90) / lng (-180..180)", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasLocationPermission()) {
            requestLocationPermissions()
            Toast.makeText(this, "Grant location permission first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!mockManager.isMockSelected()) {
            showMockNotSelectedDialog()
            return
        }
        prefs.edit().putString(KEY_LAT, lat.toString()).putString(KEY_LNG, lng.toString())
            .remove(KEY_LAST_ERROR).apply()

        try {
            val intent = Intent(this, MockLocationService::class.java).apply {
                action = MockLocationService.ACTION_START
                putExtra(MockLocationService.EXTRA_LAT, lat)
                putExtra(MockLocationService.EXTRA_LNG, lng)
            }
            ContextCompat.startForegroundService(this, intent)
            tvStatus.text = getString(R.string.status_mocking, lat, lng)
            Toast.makeText(this, "Mocking $lat, $lng", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            showMockNotSelectedDialog()
        }
    }

    private fun stopMocking() {
        startService(Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        })
        // Also stop foreground instance directly in case it is running
        try {
            val stopIntent = Intent(this, MockLocationService::class.java)
            stopService(stopIntent)
        } catch (_: Exception) {}
        try {
            mockManager.stopMocking()
        } catch (_: Exception) {}
        refreshStatus()
        Toast.makeText(this, "Mock stopped", Toast.LENGTH_SHORT).show()
    }

    private fun refreshStatus() {
        val selected = try { mockManager.isMockSelected() } catch (_: Exception) { false }
        tvStatus.text = if (selected) getString(R.string.status_ready)
        else getString(R.string.status_not_selected)
    }

    /** Reads back what the OS actually reports and compares it to the entered point. */
    private fun verifyMocking() {
        val expLat = etLat.text.toString().toDoubleOrNull()
        val expLng = etLng.text.toString().toDoubleOrNull()
        if (expLat == null || expLng == null) {
            Toast.makeText(this, "Enter lat/lng first", Toast.LENGTH_SHORT).show()
            return
        }
        tvStatus.text = "Checking what the system reports..."
        Thread {
            val result = try {
                mockManager.verifyMock()
            } catch (e: Exception) {
                null
            }
            runOnUiThread { showVerifyResult(result, expLat, expLng) }
        }.start()
    }

    private fun showVerifyResult(
        r: LocationMockManager.VerifyResult?, expLat: Double, expLng: Double
    ) {
        if (r == null) {
            tvStatus.text = "Verify failed with an exception."
            return
        }
        fun match(lat: Double?, lng: Double?): String {
            if (lat == null || lng == null) return "no data"
            val res = FloatArray(1)
            Location.distanceBetween(expLat, expLng, lat, lng, res)
            return if (res[0] < 500) "MATCHES mock (${res[0].toInt()} m off)"
            else "SHOWS SOMETHING ELSE (${(res[0] / 1000).toInt()} km away)"
        }
        val msg =
            "Mock app selected: ${r.mockSelected}\n\n" +
            "GPS provider reports:\n${r.gpsLat ?: "?"}, ${r.gpsLng ?: "?"} → ${match(r.gpsLat, r.gpsLng)}" +
            (if (r.gpsIsMock == true) " [isMock=true]" else "") + "\n\n" +
            "Fused provider reports:\n${r.fusedLat ?: "?"}, ${r.fusedLng ?: "?"} → ${match(r.fusedLat, r.fusedLng)}" +
            (if (r.fusedIsMock == true) " [isMock=true]" else "") +
            (if (r.error != null) "\n\nNote: ${r.error}" else "")
        AlertDialog.Builder(this)
            .setTitle("Verification result")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
        tvStatus.text = if (r.mockSelected) getString(R.string.status_ready)
        else getString(R.string.status_not_selected)
    }

    private fun showMockNotSelectedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select mock location app")
            .setMessage(
                "1. Enable Developer options (Settings > About phone > tap Build number 7x).\n" +
                "2. Go to Settings > System > Developer options > Select mock location app.\n" +
                "3. Choose \"MockLocation\" (this app).\n" +
                "4. Come back and press Start again."
            )
            .setPositiveButton("Open developer options") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Detects the current IP country and offers to mock inside it. */
    private fun detectIpCountry() {
        tvStatus.text = "Detecting country from IP address..."
        Thread {
            try {
                val geo = IpCountryLocator.fetch()
                val point = geo.resolveLatLng()
                runOnUiThread {
                    if (point == null) {
                        tvStatus.text = "IP country found (${geo.label()}) but no coordinates available."
                        Toast.makeText(
                            this, "No coordinates for ${geo.country}", Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showIpCountryDialog(geo, point.first, point.second)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "IP lookup failed (no internet?)."
                    Toast.makeText(this, "IP lookup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showIpCountryDialog(geo: IpCountryLocator.IpGeo, lat: Double, lng: Double) {
        val msg = "IP: ${geo.ip.ifBlank { "?" }}\n" +
            "Country: ${geo.country.ifBlank { geo.countryCode }}\n" +
            "City: ${geo.city.ifBlank { "?" }}\n\n" +
            "Mock location to:\n$lat, $lng"
        AlertDialog.Builder(this)
            .setTitle("IP country detected")
            .setMessage(msg)
            .setPositiveButton("Mock here") { _, _ ->
                etLat.setText(lat.toString())
                etLng.setText(lng.toString())
                startMocking()
            }
            .setNegativeButton("Cancel") { _, _ -> refreshStatus() }
            .show()
    }

    private fun hasLocationPermission(): Boolean {        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }
    }

    companion object {
        private const val REQ_PERMS = 1001
        private const val PREFS = "mock_prefs"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
        const val KEY_LAST_ERROR = "last_error"
    }
}
