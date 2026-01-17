package com.healthtracker.service.step

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.healthtracker.R
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

/**
 * Foreground Service for continuous step counting even when app is closed.
 * 
 * This service:
 * - Runs in foreground with persistent notification
 * - Counts steps using device sensor
 * - Saves data to SharedPreferences
 * - Works even when app is killed
 */
@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {
    
    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.healthtracker.START_STEP_COUNTER"
        const val ACTION_STOP = "com.healthtracker.STOP_STEP_COUNTER"
        
        private const val PREFS_NAME = "step_history_v3"
        
        fun startService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private lateinit var prefs: android.content.SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Step tracking
    private var todayDate: String = LocalDate.now().toString()
    private var lastSensorValue: Int = -1
    private var baselineStepsToday: Int = 0
    private var userWeightKg: Float = 70f
    private var userHeightCm: Float = 170f
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("StepCounterService onCreate")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        createNotificationChannel()
        loadTodayData()
        
        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HealthTracker::StepCounterWakeLock"
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("StepCounterService onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startStepCounting()
            }
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("StepCounterService onDestroy")
        stopStepCounting()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startStepCounting() {
        if (stepSensor == null) {
            Timber.w("Step sensor not available")
            return
        }
        
        // Check for new day
        val today = LocalDate.now().toString()
        if (today != todayDate) {
            saveHistoricalData(todayDate, baselineStepsToday)
            todayDate = today
            baselineStepsToday = 0
            lastSensorValue = -1
            saveTodayData()
        }
        
        sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        
        wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
        
        Timber.d("Step counting started, current: $baselineStepsToday")
    }
    
    private fun stopStepCounting() {
        sensorManager.unregisterListener(this)
        saveTodayData()
        Timber.d("Step counting stopped, saved: $baselineStepsToday")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorValue = event.values[0].toInt()
            
            // Check for new day
            val today = LocalDate.now().toString()
            if (today != todayDate) {
                saveHistoricalData(todayDate, baselineStepsToday)
                todayDate = today
                baselineStepsToday = 0
                lastSensorValue = currentSensorValue
                saveTodayData()
                updateNotification()
                return
            }
            
            if (lastSensorValue < 0) {
                lastSensorValue = currentSensorValue
                Timber.d("Service: First reading baseline: $currentSensorValue")
            } else {
                val newSteps = currentSensorValue - lastSensorValue
                
                if (newSteps > 0 && newSteps < 1000) { // Sanity check
                    baselineStepsToday += newSteps
                    lastSensorValue = currentSensorValue
                    saveTodayData()
                    
                    // Update notification every 100 steps
                    if (baselineStepsToday % 100 == 0) {
                        updateNotification()
                    }
                    
                    Timber.d("Service: +$newSteps steps, Total: $baselineStepsToday")
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Timber.d("Service: Sensor accuracy changed: $accuracy")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows your live step count"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val calories = (baselineStepsToday * 0.0005 * userWeightKg).toInt()
        val distanceKm = (baselineStepsToday * (userHeightCm * 0.415) / 100) / 1000
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👟 $baselineStepsToday steps today")
            .setContentText("🔥 $calories kcal • 📍 ${String.format("%.1f", distanceKm)} km")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun saveTodayData() {
        prefs.edit()
            .putInt("steps_$todayDate", baselineStepsToday)
            .putString("last_date", todayDate)
            .apply()
    }
    
    private fun loadTodayData() {
        val savedDate = prefs.getString("last_date", "") ?: ""
        todayDate = LocalDate.now().toString()
        
        userWeightKg = prefs.getFloat("user_weight", 70f)
        userHeightCm = prefs.getFloat("user_height", 170f)
        
        if (savedDate == todayDate) {
            baselineStepsToday = prefs.getInt("steps_$todayDate", 0)
            Timber.d("Service: Restored steps: $baselineStepsToday")
        } else if (savedDate.isNotEmpty()) {
            val prevSteps = prefs.getInt("steps_$savedDate", 0)
            if (prevSteps > 0) {
                saveHistoricalData(savedDate, prevSteps)
            }
            baselineStepsToday = 0
            Timber.d("Service: New day, starting fresh")
        }
    }
    
    private fun saveHistoricalData(date: String, steps: Int) {
        if (steps > 0) {
            val calories = (steps * 0.0005 * userWeightKg).toInt()
            val distance = (steps * (userHeightCm * 0.415) / 100).toInt()
            
            prefs.edit()
                .putInt("history_steps_$date", steps)
                .putInt("history_calories_$date", calories)
                .putInt("history_distance_$date", distance)
                .apply()
            
            Timber.d("Service: Saved history for $date: $steps steps")
        }
    }
}
