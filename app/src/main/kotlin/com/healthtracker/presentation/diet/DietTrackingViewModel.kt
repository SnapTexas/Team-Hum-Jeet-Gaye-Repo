package com.healthtracker.presentation.diet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.domain.model.DailyNutritionSummary
import com.healthtracker.domain.model.DietConstants
import com.healthtracker.domain.model.FoodAnalysisInput
import com.healthtracker.domain.model.FoodAnalysisResult
import com.healthtracker.domain.model.FoodItem
import com.healthtracker.domain.model.LogMealInput
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.domain.model.NutritionInfo
import com.healthtracker.domain.usecase.DietTrackingUseCase
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
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for diet tracking with computer vision.
 * 
 * Handles camera capture, ML classification, and meal logging.
 * CRITICAL: ML failure = graceful fallback to manual entry.
 */
@HiltViewModel
class DietTrackingViewModel @Inject constructor(
    private val dietTrackingUseCase: DietTrackingUseCase,
    private val featureFlagManager: FeatureFlagManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "DietTrackingViewModel"
    }
    
    private val _uiState = MutableStateFlow(DietTrackingUiState())
    val uiState: StateFlow<DietTrackingUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<DietTrackingEvent>()
    val events: SharedFlow<DietTrackingEvent> = _events.asSharedFlow()
    
    init {
        checkFeatureAvailability()
        loadTodayData()
    }
    
    private fun checkFeatureAvailability() {
        _uiState.update { it.copy(
            isCvFoodEnabled = featureFlagManager.isCvFoodEnabled()
        )}
    }

    // ============================================
    // DATA LOADING
    // ============================================
    
    private fun loadTodayData() {
        viewModelScope.launch {
            try {
                // Load today's nutrition summary
                dietTrackingUseCase.getTodayNutritionSummary().collect { summary ->
                    _uiState.update { it.copy(nutritionSummary = summary) }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading nutrition summary")
            }
        }
        
        viewModelScope.launch {
            try {
                // Load today's meals
                dietTrackingUseCase.getTodayMeals().collect { meals ->
                    _uiState.update { it.copy(todayMeals = meals) }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading meals")
            }
        }
        
        viewModelScope.launch {
            try {
                // Load recent foods for quick selection
                val recentFoods = dietTrackingUseCase.getRecentFoods()
                _uiState.update { it.copy(recentFoods = recentFoods) }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error loading recent foods")
            }
        }
    }
    
    // ============================================
    // CAMERA & CLASSIFICATION
    // ============================================
    
    fun onImageCaptured(imageUri: Uri) {
        if (!featureFlagManager.isCvFoodEnabled()) {
            _uiState.update { it.copy(
                error = "Computer vision food tracking is temporarily unavailable. Please use manual entry."
            )}
            viewModelScope.launch {
                _events.emit(DietTrackingEvent.ShowManualEntry("Feature temporarily disabled"))
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isAnalyzing = true,
                    capturedImageUri = imageUri.toString(),
                    analysisResult = null,
                    error = null
                )
            }
            
            try {
                val input = FoodAnalysisInput(
                    imageUri = imageUri.toString(),
                    userId = "current_user", // Will be resolved in use case
                    mealType = _uiState.value.selectedMealType
                )
                
                val result = dietTrackingUseCase.analyzeFood(input)
                
                _uiState.update { 
                    it.copy(
                        isAnalyzing = false,
                        analysisResult = result,
                        showResultDialog = true
                    )
                }
                
                // Handle result based on type
                when (result) {
                    is FoodAnalysisResult.Success -> {
                        Timber.d("$TAG: High confidence result: ${result.classification.foodName}")
                        _events.emit(DietTrackingEvent.ShowClassificationResult(result))
                    }
                    is FoodAnalysisResult.LowConfidence -> {
                        Timber.d("$TAG: Low confidence - needs confirmation")
                        _events.emit(DietTrackingEvent.ShowManualConfirmation(result))
                    }
                    is FoodAnalysisResult.ManualEntryRequired -> {
                        Timber.d("$TAG: Manual entry required: ${result.reason}")
                        _events.emit(DietTrackingEvent.ShowManualEntry(result.reason))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error analyzing food")
                _uiState.update { 
                    it.copy(
                        isAnalyzing = false,
                        error = "Failed to analyze food. Please try manual entry."
                    )
                }
                _events.emit(DietTrackingEvent.ShowManualEntry("Analysis failed"))
            }
        }
    }
    
    fun onRetakePhoto() {
        _uiState.update { 
            it.copy(
                capturedImageUri = null,
                analysisResult = null,
                showResultDialog = false,
                error = null
            )
        }
    }

    // ============================================
    // MEAL LOGGING
    // ============================================
    
    fun onLogManualMeal(
        foodName: String,
        nutrition: NutritionInfo,
        servings: Float,
        imageUri: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLogging = true) }
            
            try {
                val input = LogMealInput(
                    userId = "current_user",
                    date = LocalDate.now(),
                    mealType = _uiState.value.selectedMealType,
                    foodName = foodName,
                    servings = servings,
                    nutrition = nutrition,
                    imageUri = imageUri,
                    wasAutoClassified = false,
                    classificationConfidence = null
                )
                
                val meal = dietTrackingUseCase.logMealManually(input)
                
                _uiState.update { 
                    it.copy(
                        isLogging = false,
                        showManualEntry = false,
                        capturedImageUri = null,
                        analysisResult = null
                    )
                }
                
                _events.emit(DietTrackingEvent.MealLogged(meal))
                loadTodayData()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error logging manual meal")
                _uiState.update { 
                    it.copy(
                        isLogging = false,
                        error = "Failed to log meal"
                    )
                }
            }
        }
    }
    
    fun onDeleteMeal(mealId: String) {
        viewModelScope.launch {
            try {
                dietTrackingUseCase.deleteMeal(mealId)
                _events.emit(DietTrackingEvent.MealDeleted)
                loadTodayData()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error deleting meal")
                _uiState.update { it.copy(error = "Failed to delete meal") }
            }
        }
    }
    
    // ============================================
    // FOOD SELECTION FROM SUGGESTIONS
    // ============================================
    
    private var selectedFoodFromSuggestion: String? = null
    
    /**
     * Called when user selects a food from the suggestion list
     */
    fun onSelectFoodFromSuggestion(foodName: String) {
        selectedFoodFromSuggestion = foodName
        Timber.d("$TAG: User selected food: $foodName")
    }
    
    /**
     * Get nutrition info for a food name from the ML service database
     */
    fun getNutritionForFood(foodName: String): NutritionInfo? {
        val nutritionData = com.healthtracker.ml.FoodClassificationService.NUTRITION_DATABASE[foodName.lowercase().replace(" ", "_")]
            ?: com.healthtracker.ml.FoodClassificationService.NUTRITION_DATABASE[foodName.lowercase()]
        return nutritionData?.let {
            NutritionInfo(
                foodName = it.name,
                servingSize = it.servingSize,
                servingSizeGrams = 100f,
                calories = it.calories,
                protein = it.protein,
                carbs = it.carbs,
                fat = it.fat,
                fiber = 2f
            )
        }
    }
    
    /**
     * Override onConfirmMeal to use selected food if available
     * Now supports quantity multiplier for portion adjustment
     */
    fun onConfirmMeal(servings: Float = 1f) {
        val result = _uiState.value.analysisResult
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLogging = true) }
            
            try {
                // Get food name
                val foodName = selectedFoodFromSuggestion ?: when (result) {
                    is FoodAnalysisResult.Success -> result.classification.foodName
                    is FoodAnalysisResult.LowConfidence -> result.classification.foodName
                    else -> "Food"
                }
                
                // Get nutrition from result FIRST (has actual detected calories)
                val resultNutrition = when (result) {
                    is FoodAnalysisResult.Success -> result.nutrition
                    is FoodAnalysisResult.LowConfidence -> result.suggestedNutrition
                    else -> null
                }
                
                // Use result nutrition if available, otherwise lookup
                val baseNutrition = if (resultNutrition != null && resultNutrition.calories > 0) {
                    resultNutrition
                } else {
                    getNutritionForFood(foodName) ?: NutritionInfo(
                        foodName = foodName,
                        servingSize = "1 serving",
                        servingSizeGrams = 100f,
                        calories = 300, // Default fallback
                        protein = 12f,
                        carbs = 40f,
                        fat = 10f,
                        fiber = 2f
                    )
                }
                
                Timber.d("$TAG: Logging meal - Name: $foodName, Base Calories: ${baseNutrition.calories}, Servings: $servings")
                
                // Apply quantity multiplier to nutrition
                val adjustedNutrition = NutritionInfo(
                    foodName = foodName,
                    servingSize = if (servings != 1f) "${servings}x ${baseNutrition.servingSize}" else baseNutrition.servingSize,
                    servingSizeGrams = baseNutrition.servingSizeGrams * servings,
                    calories = (baseNutrition.calories * servings).toInt(),
                    protein = baseNutrition.protein * servings,
                    carbs = baseNutrition.carbs * servings,
                    fat = baseNutrition.fat * servings,
                    fiber = baseNutrition.fiber * servings
                )
                
                Timber.d("$TAG: Adjusted nutrition - Calories: ${adjustedNutrition.calories}")
                
                val input = LogMealInput(
                    userId = "current_user",
                    date = LocalDate.now(),
                    mealType = _uiState.value.selectedMealType,
                    foodName = foodName,
                    servings = servings,
                    nutrition = adjustedNutrition,
                    imageUri = _uiState.value.capturedImageUri,
                    wasAutoClassified = true,
                    classificationConfidence = when (result) {
                        is FoodAnalysisResult.Success -> result.classification.confidence
                        is FoodAnalysisResult.LowConfidence -> result.classification.confidence
                        else -> null
                    }
                )
                
                Timber.d("$TAG: Calling logMealManually...")
                val meal = dietTrackingUseCase.logMealManually(input)
                Timber.d("$TAG: Meal logged successfully - ID: ${meal.id}, Calories: ${meal.calories}")
                
                _uiState.update { 
                    it.copy(
                        isLogging = false,
                        showResultDialog = false,
                        capturedImageUri = null,
                        analysisResult = null,
                        showCamera = false
                    )
                }
                
                selectedFoodFromSuggestion = null
                _events.emit(DietTrackingEvent.MealLogged(meal))
                
                // Force refresh data
                loadTodayData()
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error logging meal")
                _uiState.update { 
                    it.copy(
                        isLogging = false,
                        error = "Failed to log meal: ${e.message}"
                    )
                }
            }
        }
    }

    // ============================================
    // FOOD SEARCH
    // ============================================
    
    fun onSearchFood(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            try {
                val results = dietTrackingUseCase.searchFood(query)
                _uiState.update { it.copy(searchResults = results) }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error searching food")
            }
        }
    }
    
    fun onSelectFood(food: FoodItem) {
        _uiState.update { 
            it.copy(
                selectedFood = food,
                showManualEntry = true,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }
    
    // ============================================
    // UI STATE MANAGEMENT
    // ============================================
    
    fun onSelectMealType(mealType: LoggedMealType) {
        _uiState.update { it.copy(selectedMealType = mealType) }
    }
    
    fun onShowCamera() {
        if (!featureFlagManager.isCvFoodEnabled()) {
            _uiState.update { it.copy(
                error = "Computer vision food tracking is temporarily unavailable. Please use manual entry.",
                showManualEntry = true
            )}
            return
        }
        _uiState.update { it.copy(showCamera = true) }
    }
    
    fun onHideCamera() {
        _uiState.update { it.copy(showCamera = false) }
    }
    
    fun onShowManualEntry() {
        _uiState.update { it.copy(showManualEntry = true) }
    }
    
    fun onHideManualEntry() {
        _uiState.update { 
            it.copy(
                showManualEntry = false,
                selectedFood = null,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }
    
    fun onDismissResultDialog() {
        _uiState.update { it.copy(showResultDialog = false) }
    }
    
    fun onClearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// ============================================
// UI STATE
// ============================================

data class DietTrackingUiState(
    val isAnalyzing: Boolean = false,
    val isLogging: Boolean = false,
    val showCamera: Boolean = false,
    val showManualEntry: Boolean = false,
    val showResultDialog: Boolean = false,
    val capturedImageUri: String? = null,
    val analysisResult: FoodAnalysisResult? = null,
    val selectedMealType: LoggedMealType = LoggedMealType.LUNCH,
    val nutritionSummary: DailyNutritionSummary? = null,
    val todayMeals: List<LoggedMeal> = emptyList(),
    val recentFoods: List<FoodItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<FoodItem> = emptyList(),
    val selectedFood: FoodItem? = null,
    val error: String? = null,
    val isCvFoodEnabled: Boolean = true
)

// ============================================
// EVENTS
// ============================================

sealed class DietTrackingEvent {
    data class ShowClassificationResult(val result: FoodAnalysisResult.Success) : DietTrackingEvent()
    data class ShowManualConfirmation(val result: FoodAnalysisResult.LowConfidence) : DietTrackingEvent()
    data class ShowManualEntry(val reason: String) : DietTrackingEvent()
    data class MealLogged(val meal: LoggedMeal) : DietTrackingEvent()
    data object MealDeleted : DietTrackingEvent()
}
