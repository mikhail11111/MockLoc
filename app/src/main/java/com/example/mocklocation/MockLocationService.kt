package com.example.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps pushing the mock location every second.
 * Keeps mock alive even when the activity is in background.
 */
class MockLocationService : Service() {

    private lateinit var mockManager: LocationMockManager
    private val handler = Handler(Looper.getMainLooper())
    private var lat = 0.0
    private var lng = 0.0

    private val pushRunnable = object : Runnable {
        override fun run() {
            try {
                mockManager.updateLocation(lat, lng)
            } catch (_: Exception) {}
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mockManager = LocationMockManager(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMocking()
                return START_NOT_STICKY
            }
            else -> {
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val hasExtras = intent?.hasExtra(EXTRA_LAT) == true &&
                    intent.hasExtra(EXTRA_LNG)
                lat = if (hasExtras) {
                    intent!!.getDoubleExtra(EXTRA_LAT, Double.NaN)
                } else {
                    // System restarted us (null intent): resume the saved point,
                    // never fall back to 0,0 ("Null Island").
                    prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: Double.NaN
                }
                lng = if (hasExtras) {
                    intent!!.getDoubleExtra(EXTRA_LNG, Double.NaN)
                } else {
                    prefs.getString(KEY_LNG, null)?.toDoubleOrNull() ?: Double.NaN
                }
                if (lat.isNaN() || lng.isNaN()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!prefs.getBoolean(KEY_MOCKING, false) && !hasExtras) {
                    // Restarted by the system after the user explicitly stopped.
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundWithNotification()
                try {
                    mockManager.startMocking(lat, lng)
                    prefs.edit().putBoolean(KEY_MOCKING, true)
                        .putString(KEY_LAT, lat.toString())
                        .putString(KEY_LNG, lng.toString())
                        .remove(MainActivity.KEY_LAST_ERROR).apply()
                } catch (e: LocationMockManager.MockNotSelectedException) {
                    // Surface it in the activity — otherwise the failure is silent
                    prefs.edit().putString(MainActivity.KEY_LAST_ERROR, e.message).apply()
                    stopSelf()
                    return START_NOT_STICKY
                }
                handler.removeCallbacks(pushRunnable)
                handler.post(pushRunnable)
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock location active")
            .setContentText(String.format("%.6f, %.6f", lat, lng))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Mock location",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopMocking() {
        handler.removeCallbacks(pushRunnable)
        try {
            mockManager.stopMocking()
        } catch (_: Exception) {}
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().remove(KEY_MOCKING).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pushRunnable)
        try {
            mockManager.stopMocking()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    /**
     * App swiped away from recents: restart ourselves so the mock keeps
     * pushing. Only when the user hasn't explicitly stopped (flag check
     * happens in onStartCommand).
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restart = Intent(this, MockLocationService::class.java).apply {
                action = ACTION_START
            }
            androidx.core.content.ContextCompat.startForegroundService(this, restart)
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIF_ID = 1001
        const val BOOT_NOTIF_ID = 1002
        const val PREFS_NAME = "mock_prefs"
        const val KEY_MOCKING = "mocking"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val ACTION_START = "com.example.mocklocation.START"
        const val ACTION_STOP = "com.example.mocklocation.STOP"
    }
}
