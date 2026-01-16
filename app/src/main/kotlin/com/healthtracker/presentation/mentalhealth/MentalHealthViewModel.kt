package com.healthtracker.presentation.mentalhealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.BreathingExercise
import com.healthtracker.domain.model.BreathingExerciseState
import com.healthtracker.domain.model.BreathingExercises
import com.healthtracker.domain.model.BreathingPhase
import com.healthtracker.domain.model.DailyStressSummary
import com.healthtracker.domain.model.MeditationCategory
import com.healthtracker.domain.model.MeditationSession
import com.healthtracker.domain.model.MeditationSessions
import com.healthtracker.domain.model.MeditationState
import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.MentalReminderType
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressTrend
import com.healthtracker.domain.model.WellnessActivity
import com.healthtracker.domain.usecase.MentalHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for mental health and stress management features.
 */
@HiltViewModel
class MentalHealthViewModel @Inject constructor(
    private val mentalHealthUseCase: MentalHealthUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "MentalHealthViewModel"
    }
    
    private val _uiState = MutableStateFlow(MentalHealthUiState())
    val uiState: StateFlow<MentalHealthUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<MentalHealthEvent>()
    val events: SharedFlow<MentalHealthEvent> = _events.asSharedFlow()
    
    private var breathingJob: Job? = null
    private var meditationJob: Job? = null
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        loadStressAssessment()
        loadStressTrend()
        loadTodayActivities()
        loadReminders()
    }
    
    // ============================================
    // STRESS ASSESSMENT
    // ============================================
    
    private fun loadStressAssessment() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingStress = true) }
                val assessment = mentalHealthUseCase.getCurrentStressAssessment()
                _uiState.update { 
                    it.copy(
                        currentStress = assessment,
                        isLoadingStress = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading stress assessment")
                _uiState.update { it.copy(isLoadingStress = false) }
            }
        }
    }
    
    private fun loadStressTrend() {
        viewModelScope.launch {
            try {
                val trend = mentalHealthUseCase.getStressTrend(7)
                _uiState.update { it.copy(stressTrend = trend) }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading stress trend")
            }
        }
    }
    
    fun refreshStressAssessment() {
        loadStressAssessment()
    }

    // ============================================
    // BREATHING EXERCISES
    // ============================================
    
    fun selectBreathingExercise(exercise: BreathingExercise) {
        _uiState.update { 
            it.copy(
                selectedBreathingExercise = exercise,
                showBreathingExercise = true
            )
        }
    }
    
    fun startBreathingExercise() {
        val exercise = _uiState.value.selectedBreathingExercise ?: return
        
        // Record stress before
        val stressBefore = _uiState.value.currentStress?.level
        
        breathingJob?.cancel()
        breathingJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isBreathingActive = true) }
                
                mentalHealthUseCase.startBreathingExercise(exercise).collect { state ->
                    _uiState.update { it.copy(breathingState = state) }
                    
                    if (state.currentPhase == BreathingPhase.COMPLETE) {
                        // Exercise completed
                        completeBreathingExercise(exercise, stressBefore)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error during breathing exercise")
                _uiState.update { it.copy(isBreathingActive = false) }
            }
        }
    }
    
    fun stopBreathingExercise() {
        breathingJob?.cancel()
        _uiState.update { 
            it.copy(
                isBreathingActive = false,
                breathingState = null
            )
        }
    }
    
    private suspend fun completeBreathingExercise(exercise: BreathingExercise, stressBefore: Int?) {
        try {
            // Get stress after
            val stressAfter = mentalHealthUseCase.getCurrentStressAssessment().level
            
            val activity = mentalHealthUseCase.completeBreathingExercise(
                exercise = exercise,
                durationSeconds = exercise.pattern.totalDurationSeconds,
                stressLevelBefore = stressBefore,
                stressLevelAfter = stressAfter
            )
            
            _uiState.update { 
                it.copy(
                    isBreathingActive = false,
                    showBreathingExercise = false
                )
            }
            
            _events.emit(MentalHealthEvent.ExerciseCompleted(activity))
            loadTodayActivities()
            loadStressAssessment()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error completing breathing exercise")
        }
    }
    
    fun dismissBreathingExercise() {
        stopBreathingExercise()
        _uiState.update { 
            it.copy(
                showBreathingExercise = false,
                selectedBreathingExercise = null
            )
        }
    }
    
    // ============================================
    // MEDITATION
    // ============================================
    
    fun selectMeditationSession(session: MeditationSession) {
        _uiState.update { 
            it.copy(
                selectedMeditationSession = session,
                showMeditation = true
            )
        }
    }
    
    fun startMeditationSession() {
        val session = _uiState.value.selectedMeditationSession ?: return
        
        val stressBefore = _uiState.value.currentStress?.level
        
        meditationJob?.cancel()
        meditationJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isMeditationActive = true) }
                
                mentalHealthUseCase.startMeditationSession(session).collect { state ->
                    _uiState.update { it.copy(meditationState = state) }
                    
                    if (!state.isPlaying && state.progress >= 1f) {
                        completeMeditationSession(session, state.elapsedSeconds, stressBefore)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error during meditation")
                _uiState.update { it.copy(isMeditationActive = false) }
            }
        }
    }
    
    fun stopMeditationSession() {
        meditationJob?.cancel()
        _uiState.update { 
            it.copy(
                isMeditationActive = false,
                meditationState = null
            )
        }
    }
    
    private suspend fun completeMeditationSession(
        session: MeditationSession,
        durationSeconds: Int,
        stressBefore: Int?
    ) {
        try {
            val stressAfter = mentalHealthUseCase.getCurrentStressAssessment().level
            
            val activity = mentalHealthUseCase.completeMeditationSession(
                session = session,
                durationSeconds = durationSeconds,
                stressLevelBefore = stressBefore,
                stressLevelAfter = stressAfter
            )
            
            _uiState.update { 
                it.copy(
                    isMeditationActive = false,
                    showMeditation = false
                )
            }
            
            _events.emit(MentalHealthEvent.ExerciseCompleted(activity))
            loadTodayActivities()
            loadStressAssessment()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error completing meditation")
        }
    }
    
    fun dismissMeditation() {
        stopMeditationSession()
        _uiState.update { 
            it.copy(
                showMeditation = false,
                selectedMeditationSession = null
            )
        }
    }

    // ============================================
    // WELLNESS ACTIVITIES
    // ============================================
    
    private fun loadTodayActivities() {
        viewModelScope.launch {
            try {
                val activities = mentalHealthUseCase.getTodayWellnessActivities()
                val totalMinutes = mentalHealthUseCase.getTodayWellnessMinutes()
                _uiState.update { 
                    it.copy(
                        todayActivities = activities,
                        todayWellnessMinutes = totalMinutes
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading activities")
            }
        }
    }
    
    // ============================================
    // REMINDERS
    // ============================================
    
    private fun loadReminders() {
        viewModelScope.launch {
            try {
                mentalHealthUseCase.getReminders().collect { reminderList ->
                    _uiState.update { it.copy(reminders = reminderList) }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading reminders")
            }
        }
    }
    
    fun createReminder(
        intervalMinutes: Int,
        startTime: LocalTime,
        endTime: LocalTime,
        daysOfWeek: Set<DayOfWeek>,
        type: MentalReminderType
    ) {
        viewModelScope.launch {
            try {
                val reminder = MindfulnessReminder(
                    id = "",
                    userId = "",
                    enabled = true,
                    intervalMinutes = intervalMinutes,
                    startTime = startTime,
                    endTime = endTime,
                    daysOfWeek = daysOfWeek,
                    reminderType = type
                )
                mentalHealthUseCase.createReminder(reminder)
                _events.emit(MentalHealthEvent.ReminderCreated)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error creating reminder")
            }
        }
    }
    
    fun toggleReminder(reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                mentalHealthUseCase.toggleReminder(reminderId, enabled)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error toggling reminder")
            }
        }
    }
    
    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                mentalHealthUseCase.deleteReminder(reminderId)
                _events.emit(MentalHealthEvent.ReminderDeleted)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error deleting reminder")
            }
        }
    }
    
    // ============================================
    // UI STATE
    // ============================================
    
    fun showReminderDialog() {
        _uiState.update { it.copy(showReminderDialog = true) }
    }
    
    fun dismissReminderDialog() {
        _uiState.update { it.copy(showReminderDialog = false) }
    }
    
    override fun onCleared() {
        super.onCleared()
        breathingJob?.cancel()
        meditationJob?.cancel()
    }
}

// ============================================
// UI STATE
// ============================================

data class MentalHealthUiState(
    val isLoadingStress: Boolean = false,
    val currentStress: StressAssessment? = null,
    val stressTrend: StressTrend? = null,
    val todayActivities: List<WellnessActivity> = emptyList(),
    val todayWellnessMinutes: Int = 0,
    val reminders: List<MindfulnessReminder> = emptyList(),
    
    // Breathing exercise
    val showBreathingExercise: Boolean = false,
    val selectedBreathingExercise: BreathingExercise? = null,
    val isBreathingActive: Boolean = false,
    val breathingState: BreathingExerciseState? = null,
    
    // Meditation
    val showMeditation: Boolean = false,
    val selectedMeditationSession: MeditationSession? = null,
    val isMeditationActive: Boolean = false,
    val meditationState: MeditationState? = null,
    
    // Dialogs
    val showReminderDialog: Boolean = false
) {
    val breathingExercises: List<BreathingExercise> = BreathingExercises.ALL
    val meditationSessions: List<MeditationSession> = MeditationSessions.ALL
}

// ============================================
// EVENTS
// ============================================

sealed class MentalHealthEvent {
    data class ExerciseCompleted(val activity: WellnessActivity) : MentalHealthEvent()
    data object ReminderCreated : MentalHealthEvent()
    data object ReminderDeleted : MentalHealthEvent()
}
