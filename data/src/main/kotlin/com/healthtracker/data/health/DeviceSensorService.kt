package com.healthtracker.data.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for reading health data directly from device sensors.
 * 
 * Uses Android's built-in sensors:
 * - TYPE_STEP_COUNTER: Total steps since last reboot (same as Digital Wellbeing uses)
 * - TYPE_STEP_DETECTOR: Detects individual steps
 * - TYPE_HEART_RATE: Heart rate (if available)
 * 
 * NO THIRD-PARTY APP REQUIRED - Uses same sensor as Xiaomi/Samsung built-in step counter!
 */
@Singleton
class DeviceSensorService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Step counter sensor (same as Digital Wellbeing)
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    
    // Current step count
    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps.asStateFlow()
    
    // Steps at midnight (to calculate daily steps)
    private var stepsAtMidnight: Int = 0
    private var lastKnownTotalSteps: Int = 0
    
    // Sensor availability
    val isStepCounterAvailable: Boolean get() = stepCounterSensor != null
    val isHeartRateAvailable: Boolean get() = heartRateSensor != null
    
    private var stepListener: SensorEventListener? = null
    
    init {
        Timber.d("DeviceSensorService initialized")
        Timber.d("Step Counter Sensor: ${if (stepCounterSensor != null) "Available" else "Not Available"}")
        Timber.d("Heart Rate Sensor: ${if (heartRateSensor != null) "Available" else "Not Available"}")
    }
    
    /**
     * Checks if device has required sensors.
     */
    fun checkSensorAvailability(): SensorAvailability {
        return SensorAvailability(
            hasStepCounter = stepCounterSensor != null,
            hasStepDetector = stepDetectorSensor != null,
            hasHeartRate = heartRateSensor != null,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
    
    /**
     * Starts listening to step counter sensor.
     * This is the SAME sensor that Xiaomi/Samsung Digital Wellbeing uses!
     */
    fun startStepCounting() {
        if (stepCounterSensor == null) {
            Timber.w("Step counter sensor not available on this device")
            return
        }
        
        // Remove existing listener if any
        stopStepCounting()
        
        stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val totalSteps = event.values[0].toInt()
                    lastKnownTotalSteps = totalSteps
                    
                    // Calculate daily steps
                    val dailySteps = if (stepsAtMidnight > 0) {
                        totalSteps - stepsAtMidnight
                    } else {
                        // First reading - save as midnight baseline
                        stepsAtMidnight = totalSteps
                        0
                    }
                    
                    _currentSteps.value = dailySteps.coerceAtLeast(0)
                    Timber.d("Steps updated: total=$totalSteps, daily=$dailySteps")
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                Timber.d("Step sensor accuracy changed: $accuracy")
            }
        }
        
        sensorManager.registerListener(
            stepListener,
            stepCounterSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        
        Timber.d("Step counting started")
    }
    
    /**
     * Stops listening to step counter sensor.
     */
    fun stopStepCounting() {
        stepListener?.let {
            sensorManager.unregisterListener(it)
            stepListener = null
            Timber.d("Step counting stopped")
        }
    }
    
    /**
     * Resets daily step count (call at midnight).
     */
    fun resetDailySteps() {
        stepsAtMidnight = lastKnownTotalSteps
        _currentSteps.value = 0
        Timber.d("Daily steps reset. Baseline: $stepsAtMidnight")
    }
    
    /**
     * Gets current step count as Flow for real-time updates.
     */
    fun getStepCountFlow(): Flow<Int> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val totalSteps = event.values[0].toInt()
                    val dailySteps = if (stepsAtMidnight > 0) {
                        (totalSteps - stepsAtMidnight).coerceAtLeast(0)
                    } else {
                        stepsAtMidnight = totalSteps
                        0
                    }
                    trySend(dailySteps)
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        
        stepCounterSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    /**
     * Gets heart rate reading (if sensor available).
     * Note: Most phones don't have heart rate sensor, only some Samsung phones do.
     */
    fun getHeartRateFlow(): Flow<Int?> = callbackFlow {
        if (heartRateSensor == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
                    val heartRate = event.values[0].toInt()
                    if (heartRate > 0) {
                        trySend(heartRate)
                    }
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
        
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    /**
     * Calculates calories burned based on steps.
     * Formula: Calories = Steps × 0.04 (average for walking)
     */
    fun calculateCaloriesFromSteps(steps: Int, weightKg: Float = 70f): Double {
        // More accurate formula based on weight
        // Calories = Steps × (0.57 × weight in kg) / 1000
        return steps * (0.57 * weightKg) / 1000
    }
    
    /**
     * Calculates distance from steps.
     * Average stride length: 0.762 meters (2.5 feet)
     */
    fun calculateDistanceFromSteps(steps: Int, heightCm: Float = 170f): Double {
        // Stride length is approximately 0.415 × height
        val strideLengthMeters = (0.415 * heightCm) / 100
        return steps * strideLengthMeters
    }
}

/**
 * Data class for sensor availability info.
 */
data class SensorAvailability(
    val hasStepCounter: Boolean,
    val hasStepDetector: Boolean,
    val hasHeartRate: Boolean,
    val deviceModel: String
)
