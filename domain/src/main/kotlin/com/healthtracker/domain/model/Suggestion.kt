package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Domain model representing an AI-generated health suggestion.
 * 
 * Suggestions are generated daily at 12:00 AM based on the previous
 * 24 hours of health data analysis.
 * 
 * @property id Unique identifier for the suggestion
 * @property userId User who owns this suggestion
 * @property type Category of the suggestion
 * @property title Short title for the suggestion
 * @property description Detailed description with actionable advice
 * @property priority Priority level (1 = highest, 5 = lowest)
 * @property actionable Whether this suggestion has an associated action
 * @property action Optional action the user can take
 * @property generatedAt Timestamp when suggestion was generated
 * @property forDate The date this suggestion is for (next day)
 * @property dismissed Whether user has dismissed this suggestion
 * @property completed Whether user has completed the suggested action
 */
data class Suggestion(
    val id: String,
    val userId: String,
    val type: SuggestionType,
    val title: String,
    val description: String,
    val priority: Int,
    val actionable: Boolean,
    val action: SuggestionAction?,
    val generatedAt: Instant,
    val forDate: LocalDate,
    val dismissed: Boolean = false,
    val completed: Boolean = false
)

/**
 * Types of suggestions that can be generated.
 */
enum class SuggestionType {
    /** Activity-related suggestions (steps, exercise) */
    ACTIVITY,
    /** Sleep-related suggestions */
    SLEEP,
    /** Nutrition and diet suggestions */
    NUTRITION,
    /** Hydration suggestions */
    HYDRATION,
    /** Mental health and stress management suggestions */
    MENTAL_HEALTH,
    /** General wellness tips */
    GENERAL
}

/**
 * Actions that can be associated with suggestions.
 */
sealed class SuggestionAction {
    /**
     * Start a specific workout.
     * @property workoutId ID of the workout to start
     */
    data class StartWorkout(val workoutId: String) : SuggestionAction()
    
    /**
     * Log water intake.
     * @property targetMl Target amount in milliliters
     */
    data class LogWater(val targetMl: Int) : SuggestionAction()
    
    /**
     * Start a meditation session.
     * @property sessionId ID of the meditation session
     */
    data class StartMeditation(val sessionId: String) : SuggestionAction()
    
    /**
     * Open the diet tracker.
     */
    object OpenDietTracker : SuggestionAction()
    
    /**
     * Set a sleep reminder.
     * @property targetBedtime Target bedtime
     */
    data class SetSleepReminder(val targetBedtime: String) : SuggestionAction()
    
    /**
     * Open step tracking.
     */
    object OpenStepTracker : SuggestionAction()
}

/**
 * Result of suggestion generation.
 */
data class SuggestionGenerationResult(
    val suggestions: List<Suggestion>,
    val usedMLModel: Boolean,
    val fallbackReason: String? = null,
    val generatedAt: Instant = Instant.now()
)

/**
 * Input data for suggestion generation.
 */
data class SuggestionInput(
    val userId: String,
    val metrics: HealthMetrics?,
    val baseline: UserBaseline?,
    val recentAnomalies: List<Anomaly>,
    val userGoals: UserGoals
)

/**
 * User's health goals for suggestion personalization.
 */
data class UserGoals(
    val dailyStepsTarget: Int = 10000,
    val dailySleepMinutesTarget: Int = 480, // 8 hours
    val dailyWaterMlTarget: Int = 2500,
    val dailyCaloriesTarget: Int = 2000,
    val maxScreenTimeMinutes: Int = 120
)
