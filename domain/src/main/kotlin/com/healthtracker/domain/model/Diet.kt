package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Domain models for Computer Vision Diet Tracking feature.
 */

// ============================================
// FOOD CLASSIFICATION MODELS
// ============================================

/**
 * Result of food image classification.
 */
data class FoodClassificationResult(
    val foodName: String,
    val confidence: Float,
    val alternativeNames: List<String> = emptyList(),
    val imageUri: String? = null,
    val classifiedAt: Instant = Instant.now()
) {
    /**
     * Returns true if confidence is above the threshold for auto-logging.
     */
    fun isHighConfidence(threshold: Float = 0.7f): Boolean = confidence >= threshold
    
    /**
     * Returns true if this result requires manual confirmation.
     */
    fun requiresManualConfirmation(threshold: Float = 0.7f): Boolean = confidence < threshold
}

/**
 * Nutrition information for a food item.
 */
data class NutritionInfo(
    val foodName: String,
    val servingSize: String,
    val servingSizeGrams: Float,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val sugar: Float = 0f,
    val sodium: Float = 0f
) {
    companion object {
        /**
         * Creates an empty nutrition info for manual entry.
         */
        fun empty(foodName: String = ""): NutritionInfo = NutritionInfo(
            foodName = foodName,
            servingSize = "1 serving",
            servingSizeGrams = 100f,
            calories = 0,
            protein = 0f,
            carbs = 0f,
            fat = 0f,
            fiber = 0f
        )
    }
}

// ============================================
// MEAL LOGGING MODELS
// ============================================

/**
 * A logged meal entry.
 */
data class LoggedMeal(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val mealType: LoggedMealType,
    val foodName: String,
    val servings: Float,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val imageUri: String? = null,
    val wasAutoClassified: Boolean,
    val classificationConfidence: Float? = null,
    val loggedAt: Instant
)

/**
 * Type of logged meal.
 */
enum class LoggedMealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}

/**
 * Daily nutrition summary.
 */
data class DailyNutritionSummary(
    val date: LocalDate,
    val totalCalories: Int,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val totalFiber: Float,
    val meals: List<LoggedMeal>,
    val calorieTarget: Int,
    val proteinTarget: Float,
    val carbsTarget: Float,
    val fatTarget: Float
) {
    val caloriesRemaining: Int get() = calorieTarget - totalCalories
    val caloriesPercentage: Float get() = (totalCalories.toFloat() / calorieTarget * 100).coerceAtMost(100f)
}

// ============================================
// FOOD ANALYSIS INPUT/OUTPUT
// ============================================

/**
 * Input for food analysis.
 */
data class FoodAnalysisInput(
    val imageUri: String,
    val userId: String,
    val mealType: LoggedMealType? = null
)

/**
 * Result of food analysis.
 */
sealed class FoodAnalysisResult {
    /**
     * Food was successfully classified with high confidence.
     */
    data class Success(
        val classification: FoodClassificationResult,
        val nutrition: NutritionInfo
    ) : FoodAnalysisResult()
    
    /**
     * Food was classified but with low confidence - needs manual confirmation.
     */
    data class LowConfidence(
        val classification: FoodClassificationResult,
        val suggestedNutrition: NutritionInfo?
    ) : FoodAnalysisResult()
    
    /**
     * Classification failed - manual entry required.
     */
    data class ManualEntryRequired(
        val reason: String,
        val imageUri: String? = null
    ) : FoodAnalysisResult()
}

/**
 * Input for logging a meal.
 */
data class LogMealInput(
    val userId: String,
    val date: LocalDate,
    val mealType: LoggedMealType,
    val foodName: String,
    val servings: Float,
    val nutrition: NutritionInfo,
    val imageUri: String? = null,
    val wasAutoClassified: Boolean = false,
    val classificationConfidence: Float? = null
)

// ============================================
// FOOD DATABASE
// ============================================

/**
 * A food item in the database.
 */
data class FoodItem(
    val id: String,
    val name: String,
    val category: FoodCategory,
    val nutrition: NutritionInfo,
    val aliases: List<String> = emptyList()
)

/**
 * Category of food.
 */
enum class FoodCategory {
    FRUITS,
    VEGETABLES,
    GRAINS,
    PROTEIN,
    DAIRY,
    FATS,
    SWEETS,
    BEVERAGES,
    MIXED,
    OTHER
}

// ============================================
// CONSTANTS
// ============================================

/**
 * Constants for diet tracking.
 */
object DietConstants {
    const val CONFIDENCE_THRESHOLD = 0.7f
    const val ML_TIMEOUT_MS = 200L
    const val DEFAULT_SERVING_SIZE_GRAMS = 100f
}
