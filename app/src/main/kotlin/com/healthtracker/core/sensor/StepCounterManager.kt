package com.healthtracker.core.sensor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Step Counter Manager - Uses phone's built-in step counter sensor.
 * NOW WITH SMARTWATCH PRIORITY!
 * 
 * FEATURES:
 * - Real-time step counting (same sensor as Xiaomi App Vault)
 * - Historical data storage (last 30 days)
 * - Weekly/Monthly aggregation
 * - Calorie & distance calculation based on user profile
 * - SMARTWATCH DATA PRIORITY - If watch connected, use watch data
 */
@Singleton
class StepCounterManager @Inject constructor(
    private val context: Context,
    private val smartWatchManager: SmartWatchManager
) : SensorEventListener {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("step_history_v3", Context.MODE_PRIVATE)
    
    // Live step count
    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()
    
    // Calories (calculated from steps)
    private val _calories = MutableStateFlow(0)
    val calories: StateFlow<Int> = _calories.asStateFlow()
    
    // Distance in meters
    private val _distance = MutableStateFlow(0.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()
    
    // Heart rate (from smartwatch if available)
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()
    
    // Sensor status
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    // Data source indicator
    private val _dataSource = MutableStateFlow("Phone Sensor")
    val dataSource: StateFlow<String> = _dataSource.asStateFlow()
    
    // Tracking variables
    private var todayDate: String = LocalDate.now().toString()
    private var lastSensorValue: Int = -1
    private var baselineStepsToday: Int = 0
    
    // User data for calculations
    private var userWeightKg: Float = 70f
    private var userHeightCm: Float = 170f
    
    val isSensorAvailable: Boolean get() = stepSensor != null
    
    init {
        Timber.d("StepCounterManager init - Sensor available: $isSensorAvailable")
        loadTodayData()
        
        // Initialize smartwatch manager
        smartWatchManager.initialize()
        
        // Monitor smartwatch connection and data
        monitorSmartWatchData()
    }
    
    fun start() {
        // Check if smartwatch data should be prioritized
        if (smartWatchManager.shouldUseWatchData()) {
            Timber.d("Using smartwatch data (priority)")
            _dataSource.value = smartWatchManager.connectedWatchName.value ?: "SmartWatch (Cached)"
            updateFromSmartWatch()
            return
        }
        
        // Fallback to phone sensor
        if (stepSensor == null) {
            Timber.w("Step counter sensor not available!")
            _isActive.value = false
            return
        }
        
        val today = LocalDate.now().toString()
        if (today != todayDate) {
            // New day - save yesterday's data and reset
            saveHistoricalData(todayDate, baselineStepsToday)
            todayDate = today
            baselineStepsToday = 0
            lastSensorValue = -1
            _steps.value = 0
            saveTodayData()
        }
        
        val registered = sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        
        _isActive.value = registered
        _dataSource.value = "Phone Sensor"
        Timber.d("Step counter started: $registered, current steps: ${_steps.value}")
    }
    
    /**
     * Monitor smartwatch data and update when available
     * TEMPORARILY DISABLED FOR BUILD
     */
    private fun monitorSmartWatchData() {
        // TODO: Re-enable after fixing coroutine scope issues
        // scope.launch {
        //     smartWatchManager.isWatchConnected.collect { isConnected ->
        //         if (isConnected) {
        //             Timber.d("SmartWatch connected - switching to watch data")
        //             updateFromSmartWatch()
        //         } else {
        //             Timber.d("SmartWatch disconnected - using cached or phone sensor")
        //             if (smartWatchManager.shouldUseWatchData()) {
        //                 updateFromSmartWatch()
        //             }
        //         }
        //     }
        // }
        // 
        // scope.launch {
        //     smartWatchManager.watchSteps.collect { watchSteps ->
        //         if (smartWatchManager.shouldUseWatchData() && watchSteps > 0) {
        //             updateFromSmartWatch()
        //         }
        //     }
        // }
    }
    
    /**
     * Update data from smartwatch
     */
    private fun updateFromSmartWatch() {
        val watchData = smartWatchManager.getCurrentWatchData()
        watchData?.let { data ->
            _steps.value = data.steps
            _heartRate.value = data.heartRate
            
            // Calculate calories and distance from watch steps
            _calories.value = (data.steps * 0.0005 * userWeightKg).toInt()
            _distance.value = data.steps * (userHeightCm * 0.415) / 100
            
            _dataSource.value = smartWatchManager.connectedWatchName.value ?: "SmartWatch (Cached)"
            
            // Save to phone storage as backup
            baselineStepsToday = data.steps
            saveTodayData()
            
            Timber.d("Updated from smartwatch: ${data.steps} steps, ${data.heartRate} BPM")
        }
    }
    
    fun stop() {
        sensorManager.unregisterListener(this)
        _isActive.value = false
        saveTodayData()
        Timber.d("Step counter stopped, saved steps: ${_steps.value}")
    }
    
    fun setUserData(weightKg: Float, heightCm: Float) {
        userWeightKg = weightKg
        userHeightCm = heightCm
        prefs.edit()
            .putFloat("user_weight", weightKg)
            .putFloat("user_height", heightCm)
            .apply()
        updateCalculations()
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorValue = event.values[0].toInt()
            
            if (lastSensorValue < 0) {
                lastSensorValue = currentSensorValue
                Timber.d("First reading, setting baseline: $currentSensorValue")
            } else {
                val newSteps = currentSensorValue - lastSensorValue
                
                if (newSteps > 0) {
                    baselineStepsToday += newSteps
                    _steps.value = baselineStepsToday
                    lastSensorValue = currentSensorValue
                    
                    updateCalculations()
                    saveTodayData()
                    
                    Timber.d("New steps: +$newSteps, Total today: ${_steps.value}")
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Timber.d("Step sensor accuracy changed: $accuracy")
    }
    
    private fun updateCalculations() {
        val stepCount = _steps.value
        _calories.value = (stepCount * 0.0005 * userWeightKg).toInt()
        val strideLengthM = (userHeightCm * 0.415) / 100
        _distance.value = stepCount * strideLengthM
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
            _steps.value = baselineStepsToday
            updateCalculations()
            Timber.d("Restored steps for today: $baselineStepsToday")
        } else if (savedDate.isNotEmpty()) {
            // Save previous day's data to history
            val prevSteps = prefs.getInt("steps_$savedDate", 0)
            if (prevSteps > 0) {
                saveHistoricalData(savedDate, prevSteps)
            }
            baselineStepsToday = 0
            _steps.value = 0
            Timber.d("New day, starting fresh")
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
            
            Timber.d("Saved historical data for $date: $steps steps")
        }
    }
    
    // ============================================
    // HISTORICAL DATA ACCESS
    // ============================================
    
    /**
     * Get steps for a specific date.
     */
    fun getStepsForDate(date: LocalDate): Int {
        val dateStr = date.toString()
        return if (dateStr == todayDate) {
            baselineStepsToday
        } else {
            prefs.getInt("history_steps_$dateStr", 0)
        }
    }
    
    /**
     * Get weekly stats (last 7 days including today).
     */
    fun getWeeklyStats(): WeeklyStats {
        val today = LocalDate.now()
        var totalSteps = 0
        var totalCalories = 0
        var totalDistance = 0.0
        var daysWithData = 0
        val dailySteps = mutableListOf<DailyStepData>()
        
        for (i in 0 until 7) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()
            
            val steps = if (dateStr == todayDate) {
                baselineStepsToday
            } else {
                prefs.getInt("history_steps_$dateStr", 0)
            }
            
            if (steps > 0) {
                daysWithData++
                totalSteps += steps
                totalCalories += (steps * 0.0005 * userWeightKg).toInt()
                totalDistance += steps * (userHeightCm * 0.415) / 100
            }
            
            dailySteps.add(DailyStepData(date, steps))
        }
        
        val avgSteps = if (daysWithData > 0) totalSteps / daysWithData else 0
        
        return WeeklyStats(
            totalSteps = totalSteps,
            avgStepsPerDay = avgSteps,
            totalCalories = totalCalories,
            totalDistanceMeters = totalDistance,
            daysWithData = daysWithData,
            dailyData = dailySteps.reversed()
        )
    }
    
    /**
     * Get monthly stats (last 30 days including today).
     */
    fun getMonthlyStats(): MonthlyStats {
        val today = LocalDate.now()
        var totalSteps = 0
        var totalCalories = 0
        var totalDistance = 0.0
        var daysWithData = 0
        var bestDay = 0
        var bestDayDate: LocalDate = today
        
        for (i in 0 until 30) {
            val date = today.minusDays(i.toLong())
            val dateStr = date.toString()
            
            val steps = if (dateStr == todayDate) {
                baselineStepsToday
            } else {
                prefs.getInt("history_steps_$dateStr", 0)
            }
            
            if (steps > 0) {
                daysWithData++
                totalSteps += steps
                totalCalories += (steps * 0.0005 * userWeightKg).toInt()
                totalDistance += steps * (userHeightCm * 0.415) / 100
                
                if (steps > bestDay) {
                    bestDay = steps
                    bestDayDate = date
                }
            }
        }
        
        val avgSteps = if (daysWithData > 0) totalSteps / daysWithData else 0
        
        return MonthlyStats(
            totalSteps = totalSteps,
            avgStepsPerDay = avgSteps,
            totalCalories = totalCalories,
            totalDistanceMeters = totalDistance,
            daysWithData = daysWithData,
            bestDaySteps = bestDay,
            bestDayDate = bestDayDate
        )
    }
}

data class DailyStepData(
    val date: LocalDate,
    val steps: Int
)

data class WeeklyStats(
    val totalSteps: Int,
    val avgStepsPerDay: Int,
    val totalCalories: Int,
    val totalDistanceMeters: Double,
    val daysWithData: Int,
    val dailyData: List<DailyStepData>
)

data class MonthlyStats(
    val totalSteps: Int,
    val avgStepsPerDay: Int,
    val totalCalories: Int,
    val totalDistanceMeters: Double,
    val daysWithData: Int,
    val bestDaySteps: Int,
    val bestDayDate: LocalDate
)
