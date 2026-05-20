package com.abi.widgettracker.utils

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.abi.widgettracker.data.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepTrackerHelper(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun hasStepSensor(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    fun hasPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun refreshSteps(onComplete: (Int) -> Unit = {}) {
        val dateStr = getCurrentDateString()
        val metrics = dbHelper.getMetrics(dateStr)

        if (!hasPermission() || !hasStepSensor()) {
            onComplete(metrics.steps)
            return
        }

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            onComplete(metrics.steps)
            return
        }
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null && event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val rawSteps = event.values[0].toInt()
                    val currentMetrics = dbHelper.getMetrics(dateStr)
                    
                    val todaySteps: Int
                    if (currentMetrics.baseSteps == -1 || rawSteps < currentMetrics.baseSteps) {
                        dbHelper.updateBaseSteps(dateStr, rawSteps)
                        dbHelper.updateStepsAndInstagram(dateStr, 0, currentMetrics.instagramSeconds)
                        todaySteps = 0
                    } else {
                        todaySteps = rawSteps - currentMetrics.baseSteps
                        dbHelper.updateStepsAndInstagram(dateStr, todaySteps, currentMetrics.instagramSeconds)
                    }
                    
                    sensorManager.unregisterListener(this)
                    onComplete(todaySteps)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
