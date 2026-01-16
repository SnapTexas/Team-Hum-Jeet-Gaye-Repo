package com.healthtracker.presentation.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.HydrationSummary
import com.healthtracker.domain.model.PlanGenerationResult
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.domain.usecase.PlanningUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Planning screen.
 * 
 * Manages workout plans, diet plans, and hydration schedules
 * with premium 3D futuristic UI state management.
 * 
 * Plans are auto-generated on first load based on user goals!
 */
@HiltViewModel
class PlanningViewModel @Inject constructor(
    private val planningUseCase: PlanningUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlanningUiState())
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()
    
    init {
        loadOrGeneratePlans()
    }
    
    /**
     * Loads plans, or generates them if they don't exist.
     */
    private fun loadOrGeneratePlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // First try to load existing plans
            combine(
                planningUseCase.getBothWorkoutPlans(),
                planningUseCase.getDietPlan(),
                planningUseCase.getTodayHydrationSummary()
            ) { workoutPlans, dietPlan, hydrationSummary ->
                Triple(workoutPlans, dietPlan, hydrationSummary)
            }
            .catch { e ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load plans"
                    )
                }
            }
            .collect { (workoutPlans, dietPlan, hydrationSummary) ->
                val (homePlan, gymPlan) = workoutPlans
                
                // If no plans exist, generate them automatically
                if (homePlan == null && gymPlan == null && dietPlan == null) {
                    generateAllPlans()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            homePlan = homePlan,
                            gymPlan = gymPlan,
                            dietPlan = dietPlan,
                            hydrationSummary = hydrationSummary,
                            error = null
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Generates all plans automatically.
     */
    private fun generateAllPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Generate all plans
                planningUseCase.regenerateAllPlans()
                
                // Reload plans after generation
                loadPlans()
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to generate plans: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Loads all plans from the use case.
     */
    private fun loadPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Combine all plan flows
            combine(
                planningUseCase.getBothWorkoutPlans(),
                planningUseCase.getDietPlan(),
                planningUseCase.getTodayHydrationSummary()
            ) { workoutPlans, dietPlan, hydrationSummary ->
                Triple(workoutPlans, dietPlan, hydrationSummary)
            }
            .catch { e ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load plans"
                    )
                }
            }
            .collect { (workoutPlans, dietPlan, hydrationSummary) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        homePlan = workoutPlans.first,
                        gymPlan = workoutPlans.second,
                        dietPlan = dietPlan,
                        hydrationSummary = hydrationSummary,
                        error = null
                    )
                }
            }
        }
    }
    
    /**
     * Selects a tab in the planning screen.
     */
    fun selectTab(tab: PlanningTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
    
    /**
     * Selects workout type (Home or Gym).
     */
    fun selectWorkoutType(type: WorkoutType) {
        _uiState.update { it.copy(selectedWorkoutType = type) }
    }
    
    /**
     * Logs water intake.
     */
    fun logWater(amountMl: Int) {
        viewModelScope.launch {
            try {
                planningUseCase.logWaterIntake(amountMl)
                // The flow will automatically update the UI
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Failed to log water: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Regenerates all plans.
     */
    fun regeneratePlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                when (val result = planningUseCase.regenerateAllPlans()) {
                    is PlanGenerationResult.Success -> {
                        // Plans will be updated via the flows
                        _uiState.update { it.copy(isLoading = false, error = null) }
                    }
                    is PlanGenerationResult.Error -> {
                        _uiState.update { 
                            it.copy(isLoading = false, error = result.message)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to regenerate plans: ${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Clears any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI state for the Planning screen.
 */
data class PlanningUiState(
    val isLoading: Boolean = false,
    val selectedTab: PlanningTab = PlanningTab.WORKOUT,
    val selectedWorkoutType: WorkoutType = WorkoutType.HOME,
    val homePlan: WorkoutPlan? = null,
    val gymPlan: WorkoutPlan? = null,
    val dietPlan: DietPlan? = null,
    val hydrationSummary: HydrationSummary? = null,
    val error: String? = null
)

/**
 * Tabs in the Planning screen.
 */
enum class PlanningTab {
    WORKOUT,
    DIET,
    HYDRATION
}
