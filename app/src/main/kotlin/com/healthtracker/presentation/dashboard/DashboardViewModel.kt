package com.healthtracker.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.core.sensor.StepCounterManager
import com.healthtracker.data.health.HealthConnectService
import com.healthtracker.data.health.HealthConnectPermissionState
import com.healthtracker.data.repository.HealthDataRepositoryImpl
import com.healthtracker.domain.model.DailyAnalytics
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.MonthlyAnalytics
import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.TrendAnalysis
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.WeeklyAnalytics
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.AISuggestionUseCase
import com.healthtracker.domain.usecase.AnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.pow

/**
 * ViewModel for the Dashboard screen.
 * 
 * Uses phone's built-in step counter sensor (same as Xiaomi App Vault).
 * NO third-party app needed!
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsUseCase: AnalyticsUseCase,
    private val aiSuggestionUseCase: AISuggestionUseCase,
    private val featureFlagManager: FeatureFlagManager,
    private val userRepository: UserRepository,
    private val healthConnectService: HealthConnectService,
    private val healthDataRepository: HealthDataRepositoryImpl,
    private val stepCounterManager: StepCounterManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    
    init {
        loadUserProfile()
        checkFeatureAvailability()
        startStepCounter()
        loadRealTimeStats() // Load real weekly/monthly data
        loadDailyAnalytics()
        loadTodaySuggestions()
    }
    
    /**
     * Starts the step counter using phone's built-in sensor.
     * Same sensor that Xiaomi App Vault uses!
     */
    private fun startStepCounter() {
        // Check if sensor is available
        val sensorAvailable = stepCounterManager.isSensorAvailable
        _uiState.update { it.copy(
            sensorStatus = if (sensorAvailable) "Active" else "Not Available",
            hasSensorSupport = sensorAvailable
        )}
        
        if (!sensorAvailable) {
            Timber.w("Step counter sensor not available on this device")
            return
        }
        
        // Start the sensor
        stepCounterManager.start()
        
        // Collect live step updates
        viewModelScope.launch {
            stepCounterManager.steps.collect { steps ->
                _uiState.update { it.copy(liveStepCount = steps) }
            }
        }
        
        viewModelScope.launch {
            stepCounterManager.calories.collect { cal ->
                _uiState.update { it.copy(liveCalories = cal) }
            }
        }
        
        viewModelScope.launch {
            stepCounterManager.distance.collect { dist ->
                _uiState.update { it.copy(liveDistance = dist) }
            }
        }
        
        Timber.d("Step counter started!")
    }
    
    /**
     * Refreshes data.
     */
    fun refresh() {
        startStepCounter()
        loadRealTimeStats()
        when (_uiState.value.selectedTab) {
            AnalyticsTab.DAILY -> loadDailyAnalytics(_uiState.value.selectedDate)
            AnalyticsTab.WEEKLY -> loadWeeklyStats()
            AnalyticsTab.MONTHLY -> loadMonthlyStats()
        }
    }
    
    /**
     * Loads real weekly stats from step counter history.
     */
    private fun loadWeeklyStats() {
        val weeklyStats = stepCounterManager.getWeeklyStats()
        _uiState.update { it.copy(
            weeklyTotalSteps = weeklyStats.totalSteps,
            weeklyAvgSteps = weeklyStats.avgStepsPerDay,
            weeklyTotalCalories = weeklyStats.totalCalories,
            weeklyTotalDistance = weeklyStats.totalDistanceMeters,
            weeklyDaysWithData = weeklyStats.daysWithData
        )}
    }
    
    /**
     * Loads real monthly stats from step counter history.
     */
    private fun loadMonthlyStats() {
        val monthlyStats = stepCounterManager.getMonthlyStats()
        _uiState.update { it.copy(
            monthlyTotalSteps = monthlyStats.totalSteps,
            monthlyAvgSteps = monthlyStats.avgStepsPerDay,
            monthlyTotalCalories = monthlyStats.totalCalories,
            monthlyTotalDistance = monthlyStats.totalDistanceMeters,
            monthlyDaysWithData = monthlyStats.daysWithData,
            monthlyBestDaySteps = monthlyStats.bestDaySteps
        )}
    }
    
    /**
     * Loads real-time stats on init.
     */
    private fun loadRealTimeStats() {
        loadWeeklyStats()
        loadMonthlyStats()
    }
    
    /**
     * Loads user profile and calculates personalized metrics.
     */
    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getUser()
                .catch { /* Silently fail */ }
                .collect { user ->
                    user?.let {
                        val bmi = calculateBMI(it.profile.weight, it.profile.height)
                        val dailyCalorieGoal = calculateDailyCalorieGoal(it)
                        val dailyStepGoal = calculateDailyStepGoal(it)
                        val dailyWaterGoal = calculateDailyWaterGoal(it)
                        
                        // Update step counter with user data for accurate calorie/distance
                        stepCounterManager.setUserData(it.profile.weight, it.profile.height)
                        
                        _uiState.update { state ->
                            state.copy(
                                userName = it.profile.name,
                                userGoal = it.profile.goal,
                                bmi = bmi,
                                bmiCategory = getBMICategory(bmi),
                                dailyCalorieGoal = dailyCalorieGoal,
                                dailyStepGoal = dailyStepGoal,
                                dailyWaterGoal = dailyWaterGoal,
                                userAge = it.profile.age,
                                userWeight = it.profile.weight,
                                userHeight = it.profile.height
                            )
                        }
                    }
                }
        }
    }
    
    /**
     * Calculates BMI from weight (kg) and height (cm).
     */
    private fun calculateBMI(weightKg: Float, heightCm: Float): Float {
        if (heightCm <= 0) return 0f
        val heightM = heightCm / 100f
        return weightKg / (heightM.pow(2))
    }
    
    /**
     * Gets BMI category string.
     */
    private fun getBMICategory(bmi: Float): String {
        return when {
            bmi < 18.5f -> "Underweight"
            bmi < 25f -> "Normal"
            bmi < 30f -> "Overweight"
            else -> "Obese"
        }
    }
    
    /**
     * Calculates daily calorie goal based on user profile and goal.
     * Uses Mifflin-St Jeor equation.
     */
    private fun calculateDailyCalorieGoal(user: User): Int {
        val profile = user.profile
        // Base Metabolic Rate (simplified, assuming male for now)
        val bmr = (10 * profile.weight) + (6.25f * profile.height) - (5 * profile.age) + 5
        
        // Activity multiplier (moderate activity)
        val tdee = bmr * 1.55f
        
        // Adjust based on goal
        return when (profile.goal) {
            HealthGoal.WEIGHT_LOSS -> (tdee - 500).toInt() // 500 cal deficit
            HealthGoal.FITNESS -> (tdee + 200).toInt() // Slight surplus for muscle
            HealthGoal.GENERAL -> tdee.toInt()
        }
    }
    
    /**
     * Calculates daily step goal based on user goal.
     */
    private fun calculateDailyStepGoal(user: User): Int {
        return when (user.profile.goal) {
            HealthGoal.WEIGHT_LOSS -> 12000
            HealthGoal.FITNESS -> 10000
            HealthGoal.GENERAL -> 8000
        }
    }
    
    /**
     * Calculates daily water intake goal (ml) based on weight.
     */
    private fun calculateDailyWaterGoal(user: User): Int {
        // 30-35ml per kg of body weight
        return (user.profile.weight * 33).toInt()
    }
    
    private fun checkFeatureAvailability() {
        _uiState.update { it.copy(
            isAnomalyDetectionEnabled = featureFlagManager.isAnomalyDetectionEnabled()
        )}
    }
    
    /**
     * Switches to a different analytics tab.
     */
    fun selectTab(tab: AnalyticsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            AnalyticsTab.DAILY -> loadDailyAnalytics()
            AnalyticsTab.WEEKLY -> loadWeeklyStats()
            AnalyticsTab.MONTHLY -> loadMonthlyStats()
        }
    }
    
    /**
     * Loads today's AI suggestions, generates if none exist.
     */
    private fun loadTodaySuggestions() {
        viewModelScope.launch {
            // First try to generate suggestions if none exist
            try {
                aiSuggestionUseCase.generateDailySuggestions()
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate suggestions")
            }
            
            // Then collect suggestions
            aiSuggestionUseCase.getTodaySuggestions()
                .catch { /* Silently fail for suggestions */ }
                .collect { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions) }
                }
        }
    }
    
    /**
     * Dismisses a suggestion.
     */
    fun dismissSuggestion(suggestionId: String) {
        viewModelScope.launch {
            aiSuggestionUseCase.dismissSuggestion(suggestionId)
        }
    }
    
    /**
     * Marks a suggestion as completed.
     */
    fun completeSuggestion(suggestionId: String) {
        viewModelScope.launch {
            aiSuggestionUseCase.completeSuggestion(suggestionId)
        }
    }
    
    /**
     * Loads daily analytics for today.
     */
    fun loadDailyAnalytics(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            analyticsUseCase.getDailyAnalytics(date)
                .catch { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Failed to load daily analytics")
                    }
                }
                .collect { analytics ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            dailyAnalytics = analytics,
                            selectedDate = date
                        )
                    }
                }
        }
    }
    
    /**
     * Loads weekly analytics for the current week.
     */
    fun loadWeeklyAnalytics(weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            analyticsUseCase.getWeeklyAnalytics(weekStart)
                .catch { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Failed to load weekly analytics")
                    }
                }
                .collect { analytics ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            weeklyAnalytics = analytics,
                            selectedWeekStart = weekStart
                        )
                    }
                }
        }
    }
    
    /**
     * Loads monthly analytics for the current month.
     */
    fun loadMonthlyAnalytics(month: YearMonth = YearMonth.now()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            analyticsUseCase.getMonthlyAnalytics(month)
                .catch { e ->
                    _uiState.update { 
                        it.copy(isLoading = false, error = e.message ?: "Failed to load monthly analytics")
                    }
                }
                .collect { analytics ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            monthlyAnalytics = analytics,
                            selectedMonth = month
                        )
                    }
                }
        }
    }
    
    /**
     * Loads trend analysis for a specific metric.
     */
    fun loadTrendAnalysis(metric: MetricType, days: Int = 7) {
        viewModelScope.launch {
            analyticsUseCase.getTrendAnalysis(metric, days)
                .catch { /* Silently fail for trend analysis */ }
                .collect { trend ->
                    _uiState.update { state ->
                        state.copy(
                            trendAnalyses = state.trendAnalyses + (metric to trend)
                        )
                    }
                }
        }
    }
    
    /**
     * Navigates to the previous day/week/month.
     */
    fun navigatePrevious() {
        when (_uiState.value.selectedTab) {
            AnalyticsTab.DAILY -> {
                val newDate = _uiState.value.selectedDate.minusDays(1)
                loadDailyAnalytics(newDate)
            }
            AnalyticsTab.WEEKLY -> {
                val newWeekStart = _uiState.value.selectedWeekStart.minusWeeks(1)
                loadWeeklyAnalytics(newWeekStart)
            }
            AnalyticsTab.MONTHLY -> {
                val newMonth = _uiState.value.selectedMonth.minusMonths(1)
                loadMonthlyAnalytics(newMonth)
            }
        }
    }
    
    /**
     * Navigates to the next day/week/month.
     */
    fun navigateNext() {
        val today = LocalDate.now()
        when (_uiState.value.selectedTab) {
            AnalyticsTab.DAILY -> {
                val newDate = _uiState.value.selectedDate.plusDays(1)
                if (!newDate.isAfter(today)) {
                    loadDailyAnalytics(newDate)
                }
            }
            AnalyticsTab.WEEKLY -> {
                val newWeekStart = _uiState.value.selectedWeekStart.plusWeeks(1)
                if (!newWeekStart.isAfter(today)) {
                    loadWeeklyAnalytics(newWeekStart)
                }
            }
            AnalyticsTab.MONTHLY -> {
                val newMonth = _uiState.value.selectedMonth.plusMonths(1)
                if (!newMonth.isAfter(YearMonth.now())) {
                    loadMonthlyAnalytics(newMonth)
                }
            }
        }
    }
    
}

/**
 * UI state for the Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: AnalyticsTab = AnalyticsTab.DAILY,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val selectedMonth: YearMonth = YearMonth.now(),
    val dailyAnalytics: DailyAnalytics? = null,
    val weeklyAnalytics: WeeklyAnalytics? = null,
    val monthlyAnalytics: MonthlyAnalytics? = null,
    val trendAnalyses: Map<MetricType, TrendAnalysis> = emptyMap(),
    val suggestions: List<Suggestion> = emptyList(),
    val isAnomalyDetectionEnabled: Boolean = true,
    // User profile data for personalization
    val userName: String = "",
    val userGoal: HealthGoal = HealthGoal.GENERAL,
    val userAge: Int = 0,
    val userWeight: Float = 0f,
    val userHeight: Float = 0f,
    val bmi: Float = 0f,
    val bmiCategory: String = "",
    val dailyCalorieGoal: Int = 2000,
    val dailyStepGoal: Int = 10000,
    val dailyWaterGoal: Int = 2500,
    // Health Connect status
    val healthConnectStatus: String = "Checking...",
    val needsHealthConnectPermission: Boolean = false,
    // Live health data from device sensor
    val sensorStatus: String = "Checking...",
    val hasSensorSupport: Boolean = false,
    val liveStepCount: Int = 0,
    val liveCalories: Int = 0,
    val liveDistance: Double = 0.0,
    // Real weekly stats from history
    val weeklyTotalSteps: Int = 0,
    val weeklyAvgSteps: Int = 0,
    val weeklyTotalCalories: Int = 0,
    val weeklyTotalDistance: Double = 0.0,
    val weeklyDaysWithData: Int = 0,
    // Real monthly stats from history
    val monthlyTotalSteps: Int = 0,
    val monthlyAvgSteps: Int = 0,
    val monthlyTotalCalories: Int = 0,
    val monthlyTotalDistance: Double = 0.0,
    val monthlyDaysWithData: Int = 0,
    val monthlyBestDaySteps: Int = 0
)

/**
 * Analytics tab options.
 */
enum class AnalyticsTab {
    DAILY,
    WEEKLY,
    MONTHLY
}
