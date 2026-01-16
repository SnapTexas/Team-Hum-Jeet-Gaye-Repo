package com.healthtracker.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.error.CrashBoundary
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.usecase.OnboardingUseCase
import com.healthtracker.domain.usecase.ProfileValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the onboarding flow.
 * 
 * Manages:
 * - Multi-step onboarding state
 * - Form validation
 * - Profile creation
 * 
 * Target: Complete onboarding in <30 seconds (Requirement 1.4)
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingUseCase: OnboardingUseCase,
    private val crashBoundary: CrashBoundary
) : ViewModel() {
    
    /**
     * UI State for onboarding.
     */
    data class OnboardingUiState(
        val currentStep: OnboardingStep = OnboardingStep.WELCOME,
        val name: String = "",
        val age: String = "",
        val weight: String = "",
        val height: String = "",
        val selectedGoal: HealthGoal? = null,
        val errors: Map<String, String> = emptyMap(),
        val isLoading: Boolean = false,
        val canProceed: Boolean = false
    )
    
    /**
     * Onboarding steps.
     */
    enum class OnboardingStep {
        WELCOME,
        NAME,
        AGE,
        BODY_METRICS,
        GOAL,
        COMPLETE
    }
    
    /**
     * One-time events.
     */
    sealed class OnboardingEvent {
        object NavigateToDashboard : OnboardingEvent()
        data class ShowError(val message: String) : OnboardingEvent()
    }
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events: SharedFlow<OnboardingEvent> = _events.asSharedFlow()
    
    // ============================================
    // User Input Handlers
    // ============================================
    
    fun onNameChanged(name: String) {
        _uiState.update { state ->
            val errors = state.errors.toMutableMap()
            
            // Validate name
            when {
                name.isBlank() -> errors["name"] = "Name is required"
                name.length > 100 -> errors["name"] = "Name is too long"
                !name.matches(Regex("^[a-zA-Z\\s'-]*$")) -> 
                    errors["name"] = "Name contains invalid characters"
                else -> errors.remove("name")
            }
            
            state.copy(
                name = name,
                errors = errors,
                canProceed = name.isNotBlank() && !errors.containsKey("name")
            )
        }
    }
    
    fun onAgeChanged(age: String) {
        _uiState.update { state ->
            val errors = state.errors.toMutableMap()
            val ageInt = age.toIntOrNull()
            
            // Validate age
            when {
                age.isBlank() -> errors["age"] = "Age is required"
                ageInt == null -> errors["age"] = "Enter a valid number"
                ageInt < ProfileValidation.MIN_AGE -> 
                    errors["age"] = "You must be at least ${ProfileValidation.MIN_AGE} years old"
                ageInt > 120 -> errors["age"] = "Please enter a valid age"
                else -> errors.remove("age")
            }
            
            state.copy(
                age = age,
                errors = errors,
                canProceed = ageInt != null && !errors.containsKey("age")
            )
        }
    }
    
    fun onWeightChanged(weight: String) {
        _uiState.update { state ->
            val errors = state.errors.toMutableMap()
            val weightFloat = weight.toFloatOrNull()
            
            // Validate weight
            when {
                weight.isBlank() -> errors["weight"] = "Weight is required"
                weightFloat == null -> errors["weight"] = "Enter a valid number"
                weightFloat < ProfileValidation.MIN_WEIGHT_KG -> 
                    errors["weight"] = "Weight must be at least ${ProfileValidation.MIN_WEIGHT_KG.toInt()} kg"
                weightFloat > ProfileValidation.MAX_WEIGHT_KG -> 
                    errors["weight"] = "Weight must be less than ${ProfileValidation.MAX_WEIGHT_KG.toInt()} kg"
                else -> errors.remove("weight")
            }
            
            state.copy(
                weight = weight,
                errors = errors,
                canProceed = canProceedFromBodyMetrics(state.copy(weight = weight, errors = errors))
            )
        }
    }
    
    fun onHeightChanged(height: String) {
        _uiState.update { state ->
            val errors = state.errors.toMutableMap()
            val heightFloat = height.toFloatOrNull()
            
            // Validate height
            when {
                height.isBlank() -> errors["height"] = "Height is required"
                heightFloat == null -> errors["height"] = "Enter a valid number"
                heightFloat < ProfileValidation.MIN_HEIGHT_CM -> 
                    errors["height"] = "Height must be at least ${ProfileValidation.MIN_HEIGHT_CM.toInt()} cm"
                heightFloat > ProfileValidation.MAX_HEIGHT_CM -> 
                    errors["height"] = "Height must be less than ${ProfileValidation.MAX_HEIGHT_CM.toInt()} cm"
                else -> errors.remove("height")
            }
            
            state.copy(
                height = height,
                errors = errors,
                canProceed = canProceedFromBodyMetrics(state.copy(height = height, errors = errors))
            )
        }
    }
    
    fun onGoalSelected(goal: HealthGoal) {
        _uiState.update { state ->
            state.copy(
                selectedGoal = goal,
                canProceed = true
            )
        }
    }
    
    // ============================================
    // Navigation
    // ============================================
    
    fun onNextStep() {
        val currentState = _uiState.value
        
        val nextStep = when (currentState.currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.NAME
            OnboardingStep.NAME -> OnboardingStep.AGE
            OnboardingStep.AGE -> OnboardingStep.BODY_METRICS
            OnboardingStep.BODY_METRICS -> OnboardingStep.GOAL
            OnboardingStep.GOAL -> {
                // Complete onboarding
                completeOnboarding()
                return
            }
            OnboardingStep.COMPLETE -> return
        }
        
        _uiState.update { state ->
            state.copy(
                currentStep = nextStep,
                canProceed = calculateCanProceed(nextStep, state)
            )
        }
    }
    
    fun onPreviousStep() {
        val currentState = _uiState.value
        
        val previousStep = when (currentState.currentStep) {
            OnboardingStep.WELCOME -> return
            OnboardingStep.NAME -> OnboardingStep.WELCOME
            OnboardingStep.AGE -> OnboardingStep.NAME
            OnboardingStep.BODY_METRICS -> OnboardingStep.AGE
            OnboardingStep.GOAL -> OnboardingStep.BODY_METRICS
            OnboardingStep.COMPLETE -> OnboardingStep.GOAL
        }
        
        _uiState.update { state ->
            state.copy(
                currentStep = previousStep,
                canProceed = calculateCanProceed(previousStep, state)
            )
        }
    }
    
    fun skipToGetStarted() {
        _uiState.update { state ->
            state.copy(currentStep = OnboardingStep.NAME)
        }
    }
    
    // ============================================
    // Complete Onboarding
    // ============================================
    
    private fun completeOnboarding() {
        val state = _uiState.value
        
        // Build profile
        val profile = UserProfile(
            name = state.name.trim(),
            age = state.age.toIntOrNull() ?: 0,
            weight = state.weight.toFloatOrNull() ?: 0f,
            height = state.height.toFloatOrNull() ?: 0f,
            goal = state.selectedGoal ?: HealthGoal.GENERAL
        )
        
        viewModelScope.launch(crashBoundary.exceptionHandler) {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = onboardingUseCase.completeOnboarding(profile)
            
            result.onSuccess {
                Timber.d("Onboarding completed successfully")
                _uiState.update { it.copy(
                    isLoading = false,
                    currentStep = OnboardingStep.COMPLETE
                )}
                _events.emit(OnboardingEvent.NavigateToDashboard)
            }.onError { error ->
                Timber.e("Onboarding failed: ${error.message}")
                _uiState.update { it.copy(isLoading = false) }
                _events.emit(OnboardingEvent.ShowError(error.message ?: "Failed to complete onboarding"))
            }
        }
    }
    
    // ============================================
    // Helper Functions
    // ============================================
    
    private fun canProceedFromBodyMetrics(state: OnboardingUiState): Boolean {
        val weightValid = state.weight.toFloatOrNull()?.let { 
            it >= ProfileValidation.MIN_WEIGHT_KG && it <= ProfileValidation.MAX_WEIGHT_KG 
        } ?: false
        
        val heightValid = state.height.toFloatOrNull()?.let { 
            it >= ProfileValidation.MIN_HEIGHT_CM && it <= ProfileValidation.MAX_HEIGHT_CM 
        } ?: false
        
        return weightValid && heightValid && 
               !state.errors.containsKey("weight") && 
               !state.errors.containsKey("height")
    }
    
    private fun calculateCanProceed(step: OnboardingStep, state: OnboardingUiState): Boolean {
        return when (step) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.NAME -> state.name.isNotBlank() && !state.errors.containsKey("name")
            OnboardingStep.AGE -> state.age.toIntOrNull()?.let { it >= ProfileValidation.MIN_AGE } ?: false
            OnboardingStep.BODY_METRICS -> canProceedFromBodyMetrics(state)
            OnboardingStep.GOAL -> state.selectedGoal != null
            OnboardingStep.COMPLETE -> true
        }
    }
}
