package com.abi.widgettracker.utils

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.abi.widgettracker.data.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UsageStatsHelper(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getInstagramScreenTimeSeconds(): Int {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val metrics = dbHelper.getMetrics(dateStr)

        if (!hasUsageAccessPermission()) {
            return metrics.instagramSeconds
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        var totalTimeMs = 0L
        if (statsList != null) {
            for (usageStats in statsList) {
                if (usageStats.packageName == "com.instagram.android") {
                    totalTimeMs = usageStats.totalTimeInForeground
                    break
                }
            }
        }

        val seconds = (totalTimeMs / 1000).toInt()
        
        dbHelper.updateStepsAndInstagram(dateStr, metrics.steps, seconds)

        return seconds
    }
}
