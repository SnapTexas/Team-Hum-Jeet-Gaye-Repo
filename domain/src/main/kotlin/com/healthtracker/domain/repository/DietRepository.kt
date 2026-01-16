package com.healthtracker.domain.repository

import com.healthtracker.domain.model.DailyNutritionSummary
import com.healthtracker.domain.model.FoodItem
import com.healthtracker.domain.model.LogMealInput
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.domain.model.NutritionInfo
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for diet tracking operations.
 * 
 * Handles meal logging, nutrition data retrieval, and food database queries.
 */
interface DietRepository {
    
    // ============================================
    // MEAL LOGGING
    // ============================================
    
    /**
     * Logs a meal entry.
     * 
     * @param input The meal logging input
     * @return The logged meal
     */
    suspend fun logMeal(input: LogMealInput): LoggedMeal
    
    /**
     * Gets all meals for a specific date.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return Flow of meals for the date
     */
    fun getMeals(userId: String, date: LocalDate): Flow<List<LoggedMeal>>
    
    /**
     * Gets meals by type for a specific date.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @param mealType The meal type to filter
     * @return Flow of meals matching the criteria
     */
    fun getMealsByType(userId: String, date: LocalDate, mealType: LoggedMealType): Flow<List<LoggedMeal>>
    
    /**
     * Deletes a logged meal.
     * 
     * @param mealId The meal ID to delete
     */
    suspend fun deleteMeal(mealId: String)
    
    /**
     * Updates a logged meal.
     * 
     * @param meal The updated meal
     */
    suspend fun updateMeal(meal: LoggedMeal)
    
    // ============================================
    // NUTRITION SUMMARY
    // ============================================
    
    /**
     * Gets the daily nutrition summary.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return Flow of daily nutrition summary
     */
    fun getDailyNutritionSummary(userId: String, date: LocalDate): Flow<DailyNutritionSummary>
    
    /**
     * Gets nutrition summaries for a date range.
     * 
     * @param userId The user ID
     * @param startDate Start of the range
     * @param endDate End of the range
     * @return Flow of daily summaries
     */
    fun getNutritionSummaryRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyNutritionSummary>>
    
    // ============================================
    // FOOD DATABASE
    // ============================================
    
    /**
     * Searches for food items by name.
     * 
     * @param query The search query
     * @return List of matching food items
     */
    suspend fun searchFood(query: String): List<FoodItem>
    
    /**
     * Gets nutrition info for a food item.
     * 
     * @param foodName The food name
     * @return Nutrition info if found
     */
    suspend fun getNutritionInfo(foodName: String): NutritionInfo?
    
    /**
     * Gets recently logged foods for quick selection.
     * 
     * @param userId The user ID
     * @param limit Maximum number of items
     * @return List of recent food items
     */
    suspend fun getRecentFoods(userId: String, limit: Int = 10): List<FoodItem>
    
    /**
     * Gets frequently logged foods.
     * 
     * @param userId The user ID
     * @param limit Maximum number of items
     * @return List of frequent food items
     */
    suspend fun getFrequentFoods(userId: String, limit: Int = 10): List<FoodItem>
}
