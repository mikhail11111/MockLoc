package com.example.mocklocation

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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

        requestLocationPermissions()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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
        prefs.edit().putString(KEY_LAT, lat.toString()).putString(KEY_LNG, lng.toString()).apply()

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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
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
    }
}
