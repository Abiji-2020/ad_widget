package com.abi.widgettracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.os.Build
import com.abi.widgettracker.MainActivity
import com.abi.widgettracker.R
import com.abi.widgettracker.data.DatabaseHelper
import com.abi.widgettracker.utils.StepTrackerHelper
import com.abi.widgettracker.utils.UsageStatsHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackerWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_INCREMENT = "com.abi.widgettracker.ACTION_INCREMENT"
        const val ACTION_DECREMENT = "com.abi.widgettracker.ACTION_DECREMENT"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val stepTracker = StepTrackerHelper(context)
        val usageHelper = UsageStatsHelper(context)
        val dbHelper = DatabaseHelper(context)
        
        val dateStr = getCurrentDateString()

        // 1. Refresh step telemetry on-demand when widget is updated
        stepTracker.refreshSteps { todaySteps ->
            val instagramSeconds = usageHelper.getInstagramScreenTimeSeconds()
            val workoutLog = dbHelper.getWorkoutLog(dateStr)
            val streak = dbHelper.getStreakCount()

            // Update all widgets
            for (appWidgetId in appWidgetIds) {
                updateWidgetView(
                    context, appWidgetManager, appWidgetId,
                    todaySteps, instagramSeconds, workoutLog.completedReps,
                    workoutLog.targetReps, workoutLog.exerciseName, streak
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == ACTION_INCREMENT || action == ACTION_DECREMENT) {
            val dbHelper = DatabaseHelper(context)
            val dateStr = getCurrentDateString()
            val template = dbHelper.getWorkoutTemplateForDate(dateStr)
            
            val delta = if (action == ACTION_INCREMENT) template.incrementStep else -template.incrementStep
            dbHelper.incrementWorkoutProgress(dateStr, delta)

            // Force immediate update of widget UI
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TrackerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            
            // Query current metrics to refresh view
            val stepTracker = StepTrackerHelper(context)
            val usageHelper = UsageStatsHelper(context)
            
            stepTracker.refreshSteps { todaySteps ->
                val instagramSeconds = usageHelper.getInstagramScreenTimeSeconds()
                val workoutLog = dbHelper.getWorkoutLog(dateStr)
                val streak = dbHelper.getStreakCount()

                for (appWidgetId in appWidgetIds) {
                    updateWidgetView(
                        context, appWidgetManager, appWidgetId,
                        todaySteps, instagramSeconds, workoutLog.completedReps,
                        workoutLog.targetReps, workoutLog.exerciseName, streak
                    )
                }
            }
        }
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        steps: Int,
        instagramSeconds: Int,
        workoutCompleted: Int,
        workoutTarget: Int,
        workoutName: String,
        streak: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // 1. Steps display
        views.setTextViewText(R.id.widget_steps_label, "🚶 Steps: $steps / 10k")
        views.setProgressBar(R.id.widget_steps_progress, 10000, steps, false)

        // 2. Instagram display (converting seconds to minutes)
        val instaMinutes = instagramSeconds / 60
        views.setTextViewText(R.id.widget_instagram_label, "📱 Insta: ${instaMinutes}m / 2h")
        views.setProgressBar(R.id.widget_instagram_progress, 120, instaMinutes, false)

        // Color logic for Instagram Limit warning
        // High limit threshold = 2 hours (120 minutes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val tintColor = when {
                instaMinutes >= 120 -> "#FF1744" // Red
                instaMinutes >= 96 -> "#FF9100"  // Orange
                else -> "#00E676"                // Green
            }
            views.setColorStateList(
                R.id.widget_instagram_progress,
                "setProgressTintList",
                android.content.res.ColorStateList.valueOf(Color.parseColor(tintColor))
            )
        }

        // 3. Streak display (🔥 motivated)
        views.setTextViewText(R.id.widget_streak_text, "🔥 $streak Days")

        // 4. Workout display & Button labels
        val workoutDisplay = "💪 Today: $workoutName"
        views.setTextViewText(R.id.widget_workout_title, workoutDisplay)
        
        // Show proper unit (seconds for plank, reps for others)
        val unit = if (workoutName.lowercase().contains("plank")) "s" else ""
        views.setTextViewText(R.id.widget_workout_progress, "Progress: $workoutCompleted / $workoutTarget$unit")

        val dbHelper = DatabaseHelper(context)
        val template = dbHelper.getWorkoutTemplateForDate(getCurrentDateString())
        views.setTextViewText(R.id.btn_widget_plus, "+${template.incrementStep}")
        views.setTextViewText(R.id.btn_widget_minus, "-${template.incrementStep}")

        // 5. Wire button clicks to receiver intents
        val intentPlus = Intent(context, TrackerWidgetProvider::class.java).apply {
            action = ACTION_INCREMENT
        }
        val pendingPlus = PendingIntent.getBroadcast(
            context, 1, intentPlus,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_plus, pendingPlus)

        val intentMinus = Intent(context, TrackerWidgetProvider::class.java).apply {
            action = ACTION_DECREMENT
        }
        val pendingMinus = PendingIntent.getBroadcast(
            context, 2, intentMinus,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_widget_minus, pendingMinus)

        // 6. Open Main setup app if header/widget is clicked
        val intentApp = Intent(context, MainActivity::class.java)
        val pendingApp = PendingIntent.getActivity(
            context, 0, intentApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, pendingApp)

        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
