package com.healthtracker.presentation.progress

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.sensor.StepCounterManager
import com.healthtracker.domain.repository.SocialRepository
import com.healthtracker.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stepCounterManager: StepCounterManager,
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository
) : ViewModel() {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("progress_settings", Context.MODE_PRIVATE)
    private val stepPrefs: SharedPreferences = context.getSharedPreferences("step_history_v3", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()
    
    init {
        loadUserData()
        observeSteps()
        loadProgressData()
        loadFriendsProgress()
    }
    
    private fun loadUserData() {
        viewModelScope.launch {
            try {
                // Use getUser() which returns Flow<User?>
                userRepository.getUser().collect { user ->
                    user?.let { currentUser ->
                        // Calculate goals based on user profile
                        val stepGoal = when (currentUser.profile.goal) {
                            com.healthtracker.domain.model.HealthGoal.WEIGHT_LOSS -> 12000
                            com.healthtracker.domain.model.HealthGoal.FITNESS -> 15000
                            com.healthtracker.domain.model.HealthGoal.GENERAL -> 10000
                        }
                        val calorieGoal = when (currentUser.profile.goal) {
                            com.healthtracker.domain.model.HealthGoal.WEIGHT_LOSS -> 1800
                            com.healthtracker.domain.model.HealthGoal.FITNESS -> 2500
                            com.healthtracker.domain.model.HealthGoal.GENERAL -> 2000
                        }
                        _uiState.update { state ->
                            state.copy(
                                stepGoal = stepGoal,
                                calorieGoal = calorieGoal,
                                distanceGoal = (stepGoal * 0.7).toInt() // Approx meters
                            )
                        }
                        loadProgressData()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load user data")
            }
        }
        
        // Load visibility setting
        val isPublic = prefs.getBoolean("is_public", false)
        _uiState.update { it.copy(isPublic = isPublic) }
    }

    private fun observeSteps() {
        viewModelScope.launch {
            stepCounterManager.steps.collect { steps ->
                _uiState.update { state ->
                    val progress = steps.toFloat() / state.stepGoal.coerceAtLeast(1)
                    state.copy(
                        todaySteps = steps,
                        stepsProgress = progress.coerceIn(0f, 1f)
                    )
                }
            }
        }
        
        viewModelScope.launch {
            stepCounterManager.calories.collect { calories ->
                _uiState.update { state ->
                    val progress = calories.toFloat() / state.calorieGoal.coerceAtLeast(1)
                    state.copy(
                        todayCalories = calories,
                        caloriesProgress = progress.coerceIn(0f, 1f)
                    )
                }
            }
        }
        
        viewModelScope.launch {
            stepCounterManager.distance.collect { distance ->
                _uiState.update { state ->
                    val progress = distance.toFloat() / state.distanceGoal.coerceAtLeast(1)
                    state.copy(
                        todayDistance = distance,
                        distanceProgress = progress.coerceIn(0f, 1f)
                    )
                }
            }
        }
    }
    
    fun selectPeriod(period: ProgressPeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadProgressData()
    }
    
    fun togglePublicVisibility() {
        val newValue = !_uiState.value.isPublic
        prefs.edit().putBoolean("is_public", newValue).apply()
        _uiState.update { it.copy(isPublic = newValue) }
        
        // Sync to Firebase if public
        if (newValue) {
            syncProgressToFirebase()
        }
    }
    
    private fun loadProgressData() {
        viewModelScope.launch {
            val period = _uiState.value.selectedPeriod
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            
            val stepsData = mutableListOf<ChartDataPoint>()
            val caloriesData = mutableListOf<ChartDataPoint>()
            val distanceData = mutableListOf<ChartDataPoint>()
            
            var totalSteps = 0
            var totalCalories = 0
            var totalDistance = 0.0
            var activeDays = 0
            var bestDay = 0
            
            for (i in (period.days - 1) downTo 0) {
                val date = today.minusDays(i.toLong())
                val dateStr = date.toString()
                val label = date.format(formatter)
                
                val steps = if (dateStr == today.toString()) {
                    _uiState.value.todaySteps
                } else {
                    stepPrefs.getInt("history_steps_$dateStr", 0).also {
                        if (it == 0) {
                            // Try alternate key
                            stepPrefs.getInt("steps_$dateStr", 0)
                        }
                    }
                }
                
                val calories = if (dateStr == today.toString()) {
                    _uiState.value.todayCalories
                } else {
                    stepPrefs.getInt("history_calories_$dateStr", 0)
                }
                
                val distance = if (dateStr == today.toString()) {
                    _uiState.value.todayDistance
                } else {
                    stepPrefs.getInt("history_distance_$dateStr", 0).toDouble()
                }
                
                stepsData.add(ChartDataPoint(label, steps.toFloat()))
                caloriesData.add(ChartDataPoint(label, calories.toFloat()))
                distanceData.add(ChartDataPoint(label, (distance / 1000).toFloat()))
                
                if (steps > 0) {
                    totalSteps += steps
                    totalCalories += calories
                    totalDistance += distance
                    activeDays++
                    if (steps > bestDay) bestDay = steps
                }
            }
            
            val avgSteps = if (activeDays > 0) totalSteps / activeDays else 0
            val avgCalories = if (activeDays > 0) totalCalories / activeDays else 0
            
            _uiState.update { state ->
                state.copy(
                    stepsData = stepsData,
                    caloriesData = caloriesData,
                    distanceData = distanceData,
                    avgSteps = avgSteps,
                    avgCalories = avgCalories,
                    totalDistance = totalDistance,
                    activeDays = activeDays,
                    bestDaySteps = bestDay
                )
            }
        }
    }

    private fun loadFriendsProgress() {
        viewModelScope.launch {
            try {
                socialRepository.getCircles().collect { circles ->
                    val friends = mutableListOf<FriendProgress>()
                    
                    circles.forEach { circle ->
                        circle.members.forEach { member ->
                            // Get friend's progress from Firebase (if they made it public)
                            // For now, using placeholder - in production, fetch from Firestore
                            friends.add(
                                FriendProgress(
                                    id = member.userId,
                                    name = member.displayName,
                                    todaySteps = 0, // Will be fetched from Firebase
                                    stepGoal = 10000
                                )
                            )
                        }
                    }
                    
                    _uiState.update { state ->
                        state.copy(
                            friendsProgress = friends.distinctBy { friend -> friend.id }.take(10),
                            connectedFriendsCount = friends.distinctBy { friend -> friend.id }.size
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load friends progress")
            }
        }
    }
    
    private fun syncProgressToFirebase() {
        viewModelScope.launch {
            try {
                // Sync current progress to Firebase for friends to see
                val state = _uiState.value
                // In production, save to Firestore:
                // firestore.collection("user_progress").document(userId).set(progressData)
                Timber.d("Syncing progress to Firebase: ${state.todaySteps} steps")
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync progress")
            }
        }
    }
}

data class ProgressUiState(
    val selectedPeriod: ProgressPeriod = ProgressPeriod.WEEK,
    val isPublic: Boolean = false,
    val connectedFriendsCount: Int = 0,
    
    // Today's data
    val todaySteps: Int = 0,
    val todayCalories: Int = 0,
    val todayDistance: Double = 0.0,
    
    // Goals
    val stepGoal: Int = 10000,
    val calorieGoal: Int = 2000,
    val distanceGoal: Int = 7000, // meters
    
    // Progress percentages
    val stepsProgress: Float = 0f,
    val caloriesProgress: Float = 0f,
    val distanceProgress: Float = 0f,
    
    // Chart data
    val stepsData: List<ChartDataPoint> = emptyList(),
    val caloriesData: List<ChartDataPoint> = emptyList(),
    val distanceData: List<ChartDataPoint> = emptyList(),
    
    // Summary stats
    val avgSteps: Int = 0,
    val avgCalories: Int = 0,
    val totalDistance: Double = 0.0,
    val activeDays: Int = 0,
    val bestDaySteps: Int = 0,
    
    // Friends
    val friendsProgress: List<FriendProgress> = emptyList()
)
