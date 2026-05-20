package com.abi.widgettracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WorkoutTemplate(
    val exerciseName: String,
    val targetReps: Int,
    val incrementStep: Int
)

data class DailyMetrics(
    val date: String,
    val steps: Int,
    val instagramSeconds: Int,
    val baseSteps: Int
)

data class WorkoutLog(
    val date: String,
    val exerciseName: String,
    val targetReps: Int,
    val completedReps: Int
) {
    val completionPercentage: Float
        get() = if (targetReps > 0) (completedReps.toFloat() / targetReps.toFloat()) else 0f
    
    val isGoalMet: Boolean
        get() = completionPercentage >= 0.8f
}

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "habit_tracker.db"
        private const val DATABASE_VERSION = 1

        // Table Names
        private const val TABLE_METRICS = "daily_metrics"
        private const val TABLE_WORKOUTS = "workout_logs"

        // Metrics Columns
        private const val KEY_DATE = "date" // YYYY-MM-DD
        private const val KEY_STEPS = "steps"
        private const val KEY_INSTAGRAM = "instagram_seconds"
        private const val KEY_BASE_STEPS = "base_steps"

        // Workouts Columns
        private const val KEY_EXERCISE = "exercise_name"
        private const val KEY_TARGET = "target_reps"
        private const val KEY_COMPLETED = "completed_reps"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMetricsTable = ("CREATE TABLE " + TABLE_METRICS + "("
                + KEY_DATE + " TEXT PRIMARY KEY,"
                + KEY_STEPS + " INTEGER DEFAULT 0,"
                + KEY_INSTAGRAM + " INTEGER DEFAULT 0,"
                + KEY_BASE_STEPS + " INTEGER DEFAULT -1" + ")")

        val createWorkoutsTable = ("CREATE TABLE " + TABLE_WORKOUTS + "("
                + KEY_DATE + " TEXT,"
                + KEY_EXERCISE + " TEXT,"
                + KEY_TARGET + " INTEGER,"
                + KEY_COMPLETED + " INTEGER DEFAULT 0,"
                + "PRIMARY KEY (" + KEY_DATE + ", " + KEY_EXERCISE + ")" + ")")

        db.execSQL(createMetricsTable)
        db.execSQL(createWorkoutsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_METRICS)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORKOUTS)
        onCreate(db)
    }

    // --- Static Workout Templates Mapping ---
    fun getWorkoutTemplateForDate(dateStr: String): WorkoutTemplate {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = try {
            format.parse(dateStr) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> WorkoutTemplate("Pushups", 50, 1)
            Calendar.TUESDAY -> WorkoutTemplate("Squats", 100, 2)
            Calendar.WEDNESDAY -> WorkoutTemplate("Plank", 240, 10) // 240s Plank, increments by 10s
            Calendar.THURSDAY -> WorkoutTemplate("Pushups", 50, 1)
            Calendar.FRIDAY -> WorkoutTemplate("Lunges", 100, 1) // 100 lunges, increments by 1
            Calendar.SATURDAY -> WorkoutTemplate("Pullups", 40, 1)
            Calendar.SUNDAY -> WorkoutTemplate("Plank", 240, 10) // 240s Plank, increments by 10s
            else -> WorkoutTemplate("Pushups", 50, 1)
        }
    }

    // --- Daily Metrics Operations ---
    fun getMetrics(dateStr: String): DailyMetrics {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_METRICS,
            arrayOf(KEY_DATE, KEY_STEPS, KEY_INSTAGRAM, KEY_BASE_STEPS),
            "$KEY_DATE = ?",
            arrayOf(dateStr),
            null, null, null
        )

        val metrics = if (cursor.moveToFirst()) {
            DailyMetrics(
                cursor.getString(0),
                cursor.getInt(1),
                cursor.getInt(2),
                cursor.getInt(3)
            )
        } else {
            // Insert default row
            val values = ContentValues().apply {
                put(KEY_DATE, dateStr)
                put(KEY_STEPS, 0)
                put(KEY_INSTAGRAM, 0)
                put(KEY_BASE_STEPS, -1)
            }
            db.insert(TABLE_METRICS, null, values)
            DailyMetrics(dateStr, 0, 0, -1)
        }
        cursor.close()
        return metrics
    }

    fun updateStepsAndInstagram(dateStr: String, steps: Int, instagramSeconds: Int) {
        val db = this.writableDatabase
        // Ensure row exists
        getMetrics(dateStr)

        val values = ContentValues().apply {
            put(KEY_STEPS, steps)
            put(KEY_INSTAGRAM, instagramSeconds)
        }
        db.update(TABLE_METRICS, values, "$KEY_DATE = ?", arrayOf(dateStr))
    }

    fun updateBaseSteps(dateStr: String, baseSteps: Int) {
        val db = this.writableDatabase
        // Ensure row exists
        getMetrics(dateStr)

        val values = ContentValues().apply {
            put(KEY_BASE_STEPS, baseSteps)
        }
        db.update(TABLE_METRICS, values, "$KEY_DATE = ?", arrayOf(dateStr))
    }

    // --- Workout Log Operations ---
    fun getWorkoutLog(dateStr: String): WorkoutLog {
        val db = this.writableDatabase
        val template = getWorkoutTemplateForDate(dateStr)

        val cursor = db.query(
            TABLE_WORKOUTS,
            arrayOf(KEY_DATE, KEY_EXERCISE, KEY_TARGET, KEY_COMPLETED),
            "$KEY_DATE = ?",
            arrayOf(dateStr),
            null, null, null
        )

        val log = if (cursor.moveToFirst()) {
            WorkoutLog(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getInt(2),
                cursor.getInt(3)
            )
        } else {
            // Insert row based on static template for that day
            val values = ContentValues().apply {
                put(KEY_DATE, dateStr)
                put(KEY_EXERCISE, template.exerciseName)
                put(KEY_TARGET, template.targetReps)
                put(KEY_COMPLETED, 0)
            }
            db.insert(TABLE_WORKOUTS, null, values)
            WorkoutLog(dateStr, template.exerciseName, template.targetReps, 0)
        }
        cursor.close()
        return log
    }

    fun incrementWorkoutProgress(dateStr: String, delta: Int): WorkoutLog {
        val db = this.writableDatabase
        val currentLog = getWorkoutLog(dateStr)
        val newCompleted = (currentLog.completedReps + delta).coerceAtLeast(0)

        val values = ContentValues().apply {
            put(KEY_COMPLETED, newCompleted)
        }
        db.update(TABLE_WORKOUTS, values, "$KEY_DATE = ? AND $KEY_EXERCISE = ?", arrayOf(dateStr, currentLog.exerciseName))
        
        return WorkoutLog(
            currentLog.date,
            currentLog.exerciseName,
            currentLog.targetReps,
            newCompleted
        )
    }

    // --- Dynamic Streak Calculation ---
    // Streak counts backwards starting from yesterday. 
    // If today is also perfectly met, we add 1 to the final streak.
    fun getStreakCount(): Int {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        
        // 1. Evaluate "today" first to see if it's currently complete
        val todayStr = format.format(calendar.time)
        val todayCompleted = isDayPerfect(todayStr)
        
        // 2. Count backwards starting from yesterday
        var streak = 0
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        
        while (true) {
            val dateStr = format.format(calendar.time)
            if (isDayPerfect(dateStr)) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        
        // If today is complete, it adds to the streak. If not, the yesterday-streak is still active.
        return if (todayCompleted) streak + 1 else streak
    }

    // A day is "perfect" (meets streak goals) if:
    // 1. Steps >= 10,000
    // 2. Instagram screen time < 2 hours (7200 seconds)
    // 3. Workout completed >= 80%
    private fun isDayPerfect(dateStr: String): Boolean {
        val db = this.readableDatabase
        
        // Check metrics (steps and instagram)
        var steps = 0
        var instagramSeconds = 0
        val cursorMetrics = db.query(
            TABLE_METRICS,
            arrayOf(KEY_STEPS, KEY_INSTAGRAM),
            "$KEY_DATE = ?",
            arrayOf(dateStr),
            null, null, null
        )
        if (cursorMetrics.moveToFirst()) {
            steps = cursorMetrics.getInt(0)
            instagramSeconds = cursorMetrics.getInt(1)
        }
        cursorMetrics.close()

        // Check workouts
        var completedReps = 0
        var targetReps = 0
        val cursorWorkouts = db.query(
            TABLE_WORKOUTS,
            arrayOf(KEY_COMPLETED, KEY_TARGET),
            "$KEY_DATE = ?",
            arrayOf(dateStr),
            null, null, null
        )
        if (cursorWorkouts.moveToFirst()) {
            completedReps = cursorWorkouts.getInt(0)
            targetReps = cursorWorkouts.getInt(1)
        }
        cursorWorkouts.close()

        // If targetReps is 0, we can't do workout check. But database helper guarantees it will set template targets.
        if (targetReps == 0) {
            val template = getWorkoutTemplateForDate(dateStr)
            targetReps = template.targetReps
        }

        val stepGoalMet = steps >= 10000
        val instagramGoalMet = instagramSeconds < 7200
        val workoutGoalMet = (completedReps.toFloat() / targetReps.toFloat()) >= 0.8f

        return stepGoalMet && instagramGoalMet && workoutGoalMet
    }
}
