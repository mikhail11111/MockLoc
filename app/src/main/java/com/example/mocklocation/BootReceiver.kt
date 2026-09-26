package com.example.mocklocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * After a reboot the mock can't silently restart itself in the background
 * (blocked on Android 12+), so we post a notification whose action resumes
 * mocking at the saved point via [PendingIntent.getForegroundService].
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != QUICKBOOT_POWERON
        ) return

        val prefs = context.getSharedPreferences(
            MockLocationService.PREFS_NAME, Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean(MockLocationService.KEY_MOCKING, false)) return
        val lat = prefs.getString(MockLocationService.KEY_LAT, null)?.toDoubleOrNull() ?: return
        val lng = prefs.getString(MockLocationService.KEY_LNG, null)?.toDoubleOrNull() ?: return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    MockLocationService.CHANNEL_ID,
                    "Mock location",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val resume = Intent(context, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
        }
        val resumePi = PendingIntent.getForegroundService(
            context, 2, resume,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, MockLocationService.CHANNEL_ID)
            .setContentTitle("Resume mock location?")
            .setContentText(
                String.format("Mock was active at %.6f, %.6f before reboot", lat, lng)
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_mylocation, "Resume mock", resumePi)
            .setAutoCancel(true)
            .build()
        nm.notify(MockLocationService.BOOT_NOTIF_ID, notif)
    }

    companion object {
        private const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
