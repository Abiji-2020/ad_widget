package com.abi.widgettracker.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstagramMonitorWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val CHANNEL_ID = "instagram_alerts"
        private const val CHANNEL_NAME = "Instagram Wellness Alerts"
        private const val PREFS_NAME = "wellness_prefs"
        private const val KEY_PREFIX_80 = "notified_80_"
        private const val KEY_PREFIX_90 = "notified_90_"
        private const val KEY_PREFIX_100 = "notified_100_"
    }

    override suspend fun doWork(): Result {
        val helper = UsageStatsHelper(context)
        val seconds = helper.getInstagramScreenTimeSeconds()
        val minutes = seconds / 60

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Thresholds
        // 80% of 2 hours = 1h 36m = 96 minutes
        // 90% of 2 hours = 1h 48m = 108 minutes
        // 100% of 2 hours = 2h = 120 minutes

        if (minutes >= 120) {
            val key = KEY_PREFIX_100 + todayStr
            if (!prefs.getBoolean(key, false)) {
                showNotification(
                    100,
                    "Instagram Limit Reached! 📱",
                    "You have used Instagram for 2 hours today. Limit exceeded!"
                )
                prefs.edit().putBoolean(key, true).apply()
            }
        } else if (minutes >= 108) {
            val key = KEY_PREFIX_90 + todayStr
            if (!prefs.getBoolean(key, false)) {
                showNotification(
                    90,
                    "Instagram Limit Warning (90%) ⚠️",
                    "You've used Instagram for 1h 48m. Only 12 minutes left for today!"
                )
                prefs.edit().putBoolean(key, true).apply()
            }
        } else if (minutes >= 96) {
            val key = KEY_PREFIX_80 + todayStr
            if (!prefs.getBoolean(key, false)) {
                showNotification(
                    80,
                    "Instagram Limit Alert (80%) ⏳",
                    "You've been scrolling for 1h 36m today. Consider taking a break!"
                )
                prefs.edit().putBoolean(key, true).apply()
            }
        }

        return Result.success()
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel if Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for Instagram screen time pacing"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Build notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Standard warning icon
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)

        notificationManager.notify(id, builder.build())
    }
}
