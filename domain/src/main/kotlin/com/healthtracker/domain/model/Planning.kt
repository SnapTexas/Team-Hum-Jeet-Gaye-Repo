package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain models for Personalized Planning feature.
 */

// ============================================
// WORKOUT PLAN MODELS
// ============================================

/**
 * Represents a personalized workout plan.
 */
data class WorkoutPlan(
    val id: String,
    val userId: String,
    val type: WorkoutType,
    val goal: HealthGoal,
    val exercises: List<Exercise>,
    val durationMinutes: Int,
    val difficulty: Difficulty,
    val caloriesBurnEstimate: Int,
    val generatedAt: Instant,
    val expiresAt: Instant
)

/**
 * Type of workout environment.
 */
enum class WorkoutType {
    HOME,
    GYM
}

/**
 * Difficulty level for workouts.
 */
enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

/**
 * Represents a single exercise in a workout plan.
 */
data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val restSeconds: Int,
    val instructions: String,
    val targetMuscles: List<String>,
    val equipmentRequired: List<String>,
    val caloriesPerSet: Int,
    val imageUrl: String? = null
)

/**
 * Category of exercise.
 */
enum class ExerciseCategory {
    CARDIO,
    STRENGTH,
    FLEXIBILITY,
    BALANCE,
    HIIT,
    CORE,
    WARMUP,
    COOLDOWN
}

// ============================================
// DIET PLAN MODELS
// ============================================

/**
 * Represents a personalized diet plan.
 */
data class DietPlan(
    val id: String,
    val userId: String,
    val preference: DietPreference,
    val goal: HealthGoal,
    val dailyCalorieTarget: Int,
    val macroTargets: MacroTargets,
    val mealSuggestions: List<MealSuggestion>,
    val generatedAt: Instant,
    val expiresAt: Instant
)

/**
 * Daily macro nutrient targets.
 */
data class MacroTargets(
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val fiberGrams: Int,
    val sugarGrams: Int,
    val sodiumMg: Int
)

/**
 * A meal suggestion in the diet plan.
 */
data class MealSuggestion(
    val id: String,
    val name: String,
    val mealType: MealType,
    val calories: Int,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val ingredients: List<String>,
    val preparationTime: Int,
    val isVegetarian: Boolean,
    val imageUrl: String? = null
)

/**
 * Type of meal.
 */
enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK
}

// ============================================
// HYDRATION SCHEDULE MODELS
// ============================================

/**
 * Represents a personalized hydration schedule.
 */
data class HydrationSchedule(
    val id: String,
    val userId: String,
    val dailyTargetMl: Int,
    val baseTargetMl: Int,
    val reminders: List<HydrationReminder>,
    val adjustedForActivity: Boolean,
    val adjustedForWeather: Boolean,
    val activityMultiplier: Float,
    val weatherMultiplier: Float,
    val generatedAt: Instant
)

/**
 * A single hydration reminder.
 */
data class HydrationReminder(
    val id: String,
    val time: LocalTime,
    val amountMl: Int,
    val message: String
)

/**
 * Tracks daily water intake.
 */
data class WaterIntake(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val amountMl: Int,
    val loggedAt: Instant
)

/**
 * Daily hydration summary.
 */
data class HydrationSummary(
    val date: LocalDate,
    val targetMl: Int,
    val consumedMl: Int,
    val percentComplete: Float,
    val intakeLogs: List<WaterIntake>
)

// ============================================
// PLAN GENERATION INPUT/OUTPUT
// ============================================

/**
 * Input for generating personalized plans.
 */
data class PlanGenerationInput(
    val userId: String,
    val profile: UserProfile,
    val recentMetrics: List<HealthMetrics>,
    val currentWeather: WeatherCondition? = null
)

/**
 * Weather condition for hydration adjustment.
 */
data class WeatherCondition(
    val temperatureCelsius: Float,
    val humidity: Float,
    val isHot: Boolean = temperatureCelsius > 25,
    val isHumid: Boolean = humidity > 70
)

/**
 * Result of plan generation.
 */
sealed class PlanGenerationResult {
    data class Success(
        val workoutPlan: WorkoutPlan?,
        val dietPlan: DietPlan?,
        val hydrationSchedule: HydrationSchedule?
    ) : PlanGenerationResult()
    
    data class Error(val message: String) : PlanGenerationResult()
}

/**
 * Activity level for hydration calculation.
 */
enum class ActivityLevel {
    SEDENTARY,
    LIGHT,
    MODERATE,
    ACTIVE,
    VERY_ACTIVE
}

/**
 * Extension to calculate activity level from steps.
 */
fun Int.toActivityLevel(): ActivityLevel = when {
    this < 3000 -> ActivityLevel.SEDENTARY
    this < 5000 -> ActivityLevel.LIGHT
    this < 8000 -> ActivityLevel.MODERATE
    this < 12000 -> ActivityLevel.ACTIVE
    else -> ActivityLevel.VERY_ACTIVE
}
