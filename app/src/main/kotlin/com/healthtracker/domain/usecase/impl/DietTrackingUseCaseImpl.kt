package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.DailyNutritionSummary
import com.healthtracker.domain.model.FoodAnalysisInput
import com.healthtracker.domain.model.FoodAnalysisResult
import com.healthtracker.domain.model.FoodClassificationResult
import com.healthtracker.domain.model.FoodItem
import com.healthtracker.domain.model.LogMealInput
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.domain.model.NutritionInfo
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.repository.DietRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.DietTrackingUseCase
import com.healthtracker.ml.FoodClassificationService
import com.healthtracker.ml.MLResult
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DietTrackingUseCaseImpl @Inject constructor(
    private val dietRepository: DietRepository,
    private val userRepository: UserRepository,
    private val foodClassificationService: FoodClassificationService
) : DietTrackingUseCase {
    
    companion object {
        private const val TAG = "DietTrackingUseCase"
    }

    override suspend fun analyzeFood(input: FoodAnalysisInput): FoodAnalysisResult {
        Timber.d("$TAG: Analyzing food image: ${input.imageUri}")
        
        val mlResult = foodClassificationService.classifyFoodImage(input.imageUri)
        
        return when (mlResult) {
            is MLResult.Success -> {
                val classificationData = mlResult.data
                
                // Check if no food was detected
                if (!classificationData.isFoodDetected()) {
                    Timber.d("$TAG: No food detected in image")
                    return FoodAnalysisResult.ManualEntryRequired(
                        reason = "No meal detected. Please take a photo of food.",
                        imageUri = input.imageUri
                    )
                }
                
                val classification = FoodClassificationResult(
                    foodName = classificationData.foodName,
                    confidence = classificationData.confidence,
                    alternativeNames = classificationData.alternativeNames,
                    imageUri = input.imageUri,
                    classifiedAt = classificationData.classifiedAt
                )
                
                // Use nutrition from ML classification (already has real calories)
                val nutrition = NutritionInfo(
                    foodName = classificationData.foodName,
                    servingSize = classificationData.servingSize,
                    servingSizeGrams = 100f,
                    calories = classificationData.calories,
                    protein = classificationData.protein,
                    carbs = classificationData.carbs,
                    fat = classificationData.fat,
                    fiber = 2f  // Default fiber
                )
                
                Timber.d("$TAG: Food detected: ${classificationData.foodName}, Calories: ${classificationData.calories}")
                
                if (classification.isHighConfidence(0.5f)) {
                    FoodAnalysisResult.Success(classification, nutrition)
                } else {
                    FoodAnalysisResult.LowConfidence(
                        classification = classification,
                        suggestedNutrition = nutrition
                    )
                }
            }
            
            is MLResult.Fallback -> {
                FoodAnalysisResult.ManualEntryRequired(
                    reason = mlResult.reason,
                    imageUri = input.imageUri
                )
            }
        }
    }
    
    override suspend fun classifyFoodImage(imageUri: String): FoodClassificationResult? {
        val mlResult = foodClassificationService.classifyFoodImage(imageUri)
        
        return when (mlResult) {
            is MLResult.Success -> {
                val data = mlResult.data
                FoodClassificationResult(
                    foodName = data.foodName,
                    confidence = data.confidence,
                    alternativeNames = data.alternativeNames,
                    imageUri = imageUri,
                    classifiedAt = data.classifiedAt
                )
            }
            is MLResult.Fallback -> null
        }
    }

    override suspend fun logMealFromAnalysis(
        result: FoodAnalysisResult.Success,
        mealType: LoggedMealType,
        servings: Float
    ): LoggedMeal {
        val userId = getCurrentUserId()
        
        val input = LogMealInput(
            userId = userId,
            date = LocalDate.now(),
            mealType = mealType,
            foodName = result.classification.foodName,
            servings = servings,
            nutrition = result.nutrition,
            imageUri = result.classification.imageUri,
            wasAutoClassified = true,
            classificationConfidence = result.classification.confidence
        )
        
        return dietRepository.logMeal(input)
    }
    
    override suspend fun logMealManually(input: LogMealInput): LoggedMeal {
        return dietRepository.logMeal(input)
    }
    
    override suspend fun deleteMeal(mealId: String) {
        dietRepository.deleteMeal(mealId)
    }

    override fun getTodayNutritionSummary(): Flow<DailyNutritionSummary> {
        return dietRepository.getDailyNutritionSummary("current_user", LocalDate.now())
    }
    
    override fun getNutritionSummary(date: LocalDate): Flow<DailyNutritionSummary> {
        return dietRepository.getDailyNutritionSummary("current_user", date)
    }
    
    override fun getTodayMeals(): Flow<List<LoggedMeal>> {
        return dietRepository.getMeals("current_user", LocalDate.now())
    }
    
    override fun getMeals(date: LocalDate): Flow<List<LoggedMeal>> {
        return dietRepository.getMeals("current_user", date)
    }

    override suspend fun searchFood(query: String): List<FoodItem> {
        if (query.isBlank()) return emptyList()
        return dietRepository.searchFood(query)
    }
    
    override suspend fun getNutritionInfo(foodName: String): NutritionInfo? {
        return dietRepository.getNutritionInfo(foodName)
    }
    
    override suspend fun getRecentFoods(): List<FoodItem> {
        return dietRepository.getRecentFoods(getCurrentUserId())
    }
    
    override suspend fun getFrequentFoods(): List<FoodItem> {
        return dietRepository.getFrequentFoods(getCurrentUserId())
    }

    private suspend fun getCurrentUserId(): String {
        return when (val result = userRepository.getCurrentUser()) {
            is Result.Success -> result.data.id
            else -> "default"
        }
    }
}
