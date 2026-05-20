package com.abi.widgettracker

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.abi.widgettracker.data.DatabaseHelper
import com.abi.widgettracker.utils.InstagramMonitorWorker
import com.abi.widgettracker.utils.StepTrackerHelper
import com.abi.widgettracker.utils.UsageStatsHelper
import com.abi.widgettracker.widget.TrackerWidgetProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Sleek glassmorphic theme color tokens
val SpaceBlack = Color(0xFF0D0E12)
val CardGray = Color(0xFF1B1D26)
val GlassStroke = Color(0xFF333742)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00E676)
val NeonOrange = Color(0xFFFF5722)
val GradientRed = Color(0xFFFF3366)

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var stepTracker: StepTrackerHelper
    private lateinit var usageHelper: UsageStatsHelper

    // Permission Launchers
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Force refresh telemetry when permissions status updates
        triggerTelemetryRefresh()
    }

    // Telemetry state variables
    private val todaySteps = mutableIntStateOf(0)
    private val instagramSeconds = mutableIntStateOf(0)
    private val workoutProgress = mutableIntStateOf(0)
    private val streakCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dbHelper = DatabaseHelper(this)
        stepTracker = StepTrackerHelper(this)
        usageHelper = UsageStatsHelper(this)

        // Initialize WorkManager alert monitoring job
        scheduleInstagramMonitor()

        // Initial permissions request & telemetry update
        requestNecessaryPermissions()
        triggerTelemetryRefresh()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = SpaceBlack,
                    surface = CardGray
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SpaceBlack
                ) {
                    SetupDashboardScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        triggerTelemetryRefresh()
    }

    private fun scheduleInstagramMonitor() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<InstagramMonitorWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "InstagramUsageMonitor",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestPermissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun triggerTelemetryRefresh() {
        val dateStr = getCurrentDateString()
        
        // Refresh Steps
        stepTracker.refreshSteps { steps ->
            todaySteps.intValue = steps
            // Refresh widget
            refreshWidgetBroadcast()
        }

        // Refresh Instagram
        instagramSeconds.intValue = usageHelper.getInstagramScreenTimeSeconds()

        // Fetch logs
        val log = dbHelper.getWorkoutLog(dateStr)
        workoutProgress.intValue = log.completedReps

        // Streak
        streakCount.intValue = dbHelper.getStreakCount()

        refreshWidgetBroadcast()
    }

    private fun incrementWorkout(delta: Int) {
        val dateStr = getCurrentDateString()
        val updated = dbHelper.incrementWorkoutProgress(dateStr, delta)
        workoutProgress.intValue = updated.completedReps
        streakCount.intValue = dbHelper.getStreakCount()
        refreshWidgetBroadcast()
    }

    private fun refreshWidgetBroadcast() {
        val intent = Intent(this, TrackerWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, TrackerWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    @Composable
    fun SetupDashboardScreen() {
        val dateStr = getCurrentDateString()
        val template = dbHelper.getWorkoutTemplateForDate(dateStr)

        var hasUsagePermission by remember { mutableStateOf(usageHelper.hasUsageAccessPermission()) }
        var hasActivityPermission by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                } else true
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "HabitWidget Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 10.dp)
            )

            // Streak Badge (Motivating visual indicator)
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(colors = listOf(GradientRed, NeonOrange)),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "🔥 Streak: ${streakCount.intValue} Days",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Telemetry Cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Today's Telemetry",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    // Steps Progress Indicator
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "🚶 Steps Count", color = Color.Gray, fontSize = 13.sp)
                            Text(text = "${todaySteps.intValue} / 10,000", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = (todaySteps.intValue.toFloat() / 10000f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = NeonCyan,
                            trackColor = Color.DarkGray
                        )
                    }

                    // Instagram Progress Indicator
                    val instaMins = instagramSeconds.intValue / 60
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "📱 Instagram Screen Time", color = Color.Gray, fontSize = 13.sp)
                            Text(text = "${instaMins}m / 2h", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = (instaMins.toFloat() / 120f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (instaMins >= 120) Color.Red else NeonGreen,
                            trackColor = Color.DarkGray
                        )
                    }
                }
            }

            // Workout Progress Card with Incremental Button Inputs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💪 Today's Workout target",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Text(
                        text = template.exerciseName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )

                    val unitStr = if (template.exerciseName.lowercase().contains("plank")) "seconds" else "reps"
                    Text(
                        text = "${workoutProgress.intValue} / ${template.targetReps} $unitStr completed",
                        fontSize = 15.sp,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Strictly Incremental buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { incrementWorkout(-template.incrementStep) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "-${template.incrementStep}", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { incrementWorkout(template.incrementStep) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = "+${template.incrementStep}", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Permissions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassStroke, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "System Authorization Status",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    // Usage Access Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Usage Stats (Instagram): ${if (hasUsagePermission) "🟢 Active" else "🔴 Disabled"}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        if (!hasUsagePermission) {
                            Button(
                                onClick = {
                                    usageHelper.openUsageAccessSettings()
                                    // Refresh status
                                    hasUsagePermission = usageHelper.hasUsageAccessPermission()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GlassStroke)
                            ) {
                                Text(text = "Authorize", fontSize = 11.sp)
                            }
                        }
                    }

                    // Activity Recognition Permission
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Step Sensor permission: ${if (hasActivityPermission) "🟢 Active" else "🔴 Disabled"}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                        if (!hasActivityPermission) {
                            Button(
                                onClick = { requestNecessaryPermissions() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GlassStroke)
                            ) {
                                Text(text = "Grant", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info Footer
            Text(
                text = "HabitWidget is fully configured! Long-press your phone's home screen and add the HabitWidget to begin tracking.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }
}
