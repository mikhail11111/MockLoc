package com.mockloc.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Watchdog: every 15 min (minimum interval WorkManager allows) checks whether
 * the user left mocking ON. If the service was killed meanwhile, it fires a
 * start intent — the service resumes the saved point from prefs.
 * Scheduled on every successful start, cancelled on explicit stop.
 */
class MockWatchWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences(
                MockLocationService.PREFS_NAME, Context.MODE_PRIVATE
            )
            if (prefs.getBoolean(MockLocationService.KEY_MOCKING, false)) {
                val intent = Intent(applicationContext, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(applicationContext, intent)
                } catch (_: Exception) {
                    // Background FGS start blocked on Android 12+ in some states;
                    // onTaskRemoved restart + battery exemption cover the rest.
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "mock-watch"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<MockWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.KEEP, req
            )
        }

        fun cancel(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork(TAG)
            } catch (_: Exception) {}
        }
    }
}
