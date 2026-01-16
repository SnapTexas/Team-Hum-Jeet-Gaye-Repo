package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DailyNutritionSummary
import com.healthtracker.domain.model.FoodAnalysisInput
import com.healthtracker.domain.model.FoodAnalysisResult
import com.healthtracker.domain.model.FoodClassificationResult
import com.healthtracker.domain.model.FoodItem
import com.healthtracker.domain.model.LogMealInput
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.domain.model.NutritionInfo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Use case interface for diet tracking with computer vision.
 * 
 * Handles food image classification, nutrition lookup, and meal logging.
 * All ML operations are wrapped in MLResult for graceful fallback.
 * 
 * **CRITICAL**: ML failure = graceful fallback to manual entry, never crash.
 */
interface DietTrackingUseCase {
    
    // ============================================
    // FOOD CLASSIFICATION (ML)
    // ============================================
    
    /**
     * Analyzes a food image and returns classification with nutrition.
     * 
     * Uses ML Kit for on-device classification with MLResult wrapper.
     * If confidence < 0.7, returns LowConfidence result for manual confirmation.
     * If ML fails, returns ManualEntryRequired.
     * 
     * @param input The food analysis input with image URI
     * @return FoodAnalysisResult (Success, LowConfidence, or ManualEntryRequired)
     */
    suspend fun analyzeFood(input: FoodAnalysisInput): FoodAnalysisResult
    
    /**
     * Classifies a food image without nutrition lookup.
     * 
     * @param imageUri The image URI to classify
     * @return Classification result or null if ML fails
     */
    suspend fun classifyFoodImage(imageUri: String): FoodClassificationResult?
    
    // ============================================
    // MEAL LOGGING
    // ============================================
    
    /**
     * Logs a meal from analysis result.
     * 
     * @param result The food analysis result
     * @param mealType The meal type
     * @param servings Number of servings
     * @return The logged meal
     */
    suspend fun logMealFromAnalysis(
        result: FoodAnalysisResult.Success,
        mealType: LoggedMealType,
        servings: Float = 1f
    ): LoggedMeal
    
    /**
     * Logs a meal manually.
     * 
     * @param input The meal logging input
     * @return The logged meal
     */
    suspend fun logMealManually(input: LogMealInput): LoggedMeal
    
    /**
     * Deletes a logged meal.
     * 
     * @param mealId The meal ID to delete
     */
    suspend fun deleteMeal(mealId: String)
    
    // ============================================
    // NUTRITION DATA
    // ============================================
    
    /**
     * Gets today's nutrition summary.
     * 
     * @return Flow of daily nutrition summary
     */
    fun getTodayNutritionSummary(): Flow<DailyNutritionSummary>
    
    /**
     * Gets nutrition summary for a specific date.
     * 
     * @param date The date to query
     * @return Flow of daily nutrition summary
     */
    fun getNutritionSummary(date: LocalDate): Flow<DailyNutritionSummary>
    
    /**
     * Gets meals for today.
     * 
     * @return Flow of today's meals
     */
    fun getTodayMeals(): Flow<List<LoggedMeal>>
    
    /**
     * Gets meals for a specific date.
     * 
     * @param date The date to query
     * @return Flow of meals
     */
    fun getMeals(date: LocalDate): Flow<List<LoggedMeal>>
    
    // ============================================
    // FOOD SEARCH
    // ============================================
    
    /**
     * Searches for food items.
     * 
     * @param query The search query
     * @return List of matching food items
     */
    suspend fun searchFood(query: String): List<FoodItem>
    
    /**
     * Gets nutrition info for a food name.
     * 
     * @param foodName The food name
     * @return Nutrition info or null if not found
     */
    suspend fun getNutritionInfo(foodName: String): NutritionInfo?
    
    /**
     * Gets recent foods for quick selection.
     * 
     * @return List of recent food items
     */
    suspend fun getRecentFoods(): List<FoodItem>
    
    /**
     * Gets frequently logged foods.
     * 
     * @return List of frequent food items
     */
    suspend fun getFrequentFoods(): List<FoodItem>
}
