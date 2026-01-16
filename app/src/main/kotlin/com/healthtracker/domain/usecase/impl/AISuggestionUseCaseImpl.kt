package com.healthtracker.domain.usecase.impl

import android.util.Log
import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.domain.model.DefaultGoals
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionAction
import com.healthtracker.domain.model.SuggestionGenerationResult
import com.healthtracker.domain.model.SuggestionInput
import com.healthtracker.domain.model.SuggestionType
import com.healthtracker.domain.model.UserBaseline
import com.healthtracker.domain.model.UserGoals
import com.healthtracker.domain.repository.AnomalyRepository
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.repository.SuggestionRepository
import com.healthtracker.domain.usecase.AISuggestionUseCase
import com.healthtracker.ml.MLResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of AISuggestionUseCase.
 * 
 * Uses defensive ML wrapper pattern - all ML operations are wrapped
 * in MLResult to ensure graceful fallback on failure.
 * 
 * Rule: ML failure = graceful fallback to rule-based suggestions,
 * never block suggestion generation on ML availability.
 */
class AISuggestionUseCaseImpl @Inject constructor(
    private val suggestionRepository: SuggestionRepository,
    private val healthDataRepository: HealthDataRepository,
    private val anomalyRepository: AnomalyRepository
) : AISuggestionUseCase {
    
    companion object {
        private const val TAG = "AISuggestion"
        private const val ML_TIMEOUT_MS = 200L
    }
    
    override suspend fun generateDailySuggestions(): SuggestionGenerationResult = 
        withContext(Dispatchers.Default) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            
            // Check if suggestions already generated for tomorrow
            if (suggestionRepository.hasSuggestionsForDate(tomorrow)) {
                Log.d(TAG, "Suggestions already generated for $tomorrow")
                val existing = suggestionRepository.getSuggestionsSync(tomorrow)
                return@withContext SuggestionGenerationResult(
                    suggestions = existing,
                    usedMLModel = false,
                    fallbackReason = "Suggestions already exist"
                )
            }
            
            // Gather input data
            val input = gatherSuggestionInput()
            
            // Try ML-based generation with timeout
            val mlResult = tryMLGeneration(input, tomorrow)
            
            when (mlResult) {
                is MLResult.Success -> {
                    val suggestions = mlResult.data
                    suggestionRepository.saveSuggestions(suggestions)
                    SuggestionGenerationResult(
                        suggestions = suggestions,
                        usedMLModel = true
                    )
                }
                is MLResult.Fallback -> {
                    Log.w(TAG, "ML generation failed: ${mlResult.reason}, using rule-based fallback")
                    val suggestions = generateRuleBasedSuggestions(input, tomorrow)
                    suggestionRepository.saveSuggestions(suggestions)
                    SuggestionGenerationResult(
                        suggestions = suggestions,
                        usedMLModel = false,
                        fallbackReason = mlResult.reason
                    )
                }
            }
        }
    
    /**
     * Gathers input data for suggestion generation.
     */
    private suspend fun gatherSuggestionInput(): SuggestionInput {
        val today = LocalDate.now()
        val metrics = healthDataRepository.getMetricsForDateSync(today)
        val baseline = anomalyRepository.getUserBaselineSync()
        val recentAnomalies = anomalyRepository.getRecentAnomalies(5)
        
        return SuggestionInput(
            userId = metrics?.userId ?: "anonymous",
            metrics = metrics,
            baseline = baseline,
            recentAnomalies = recentAnomalies,
            userGoals = UserGoals() // Default goals, could be personalized
        )
    }
    
    /**
     * Attempts ML-based suggestion generation with timeout.
     * 
     * CRITICAL: All ML operations must be wrapped in MLResult.
     * Max timeout: 200ms to prevent blocking.
     */
    private suspend fun tryMLGeneration(
        input: SuggestionInput,
        forDate: LocalDate
    ): MLResult<List<Suggestion>> {
        return MLResult.runWithTimeout(
            timeoutMs = ML_TIMEOUT_MS,
            fallbackReason = "ML inference timed out after ${ML_TIMEOUT_MS}ms"
        ) {
            // For now, use rule-based generation as ML model placeholder
            // In production, this would call TFLite model
            generateRuleBasedSuggestions(input, forDate)
        }
    }
    
    /**
     * Rule-based suggestion generation.
     * 
     * This is the fallback when ML fails and also serves as the
     * baseline implementation until ML model is integrated.
     * 
     * Generates suggestions covering: ACTIVITY, SLEEP, NUTRITION, HYDRATION
     */
    private fun generateRuleBasedSuggestions(
        input: SuggestionInput,
        forDate: LocalDate
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val now = Instant.now()
        val userId = input.userId
        val metrics = input.metrics
        val goals = input.userGoals
        
        // If no metrics available, provide generic wellness tips
        if (metrics == null) {
            return generateGenericSuggestions(userId, forDate, now)
        }
        
        // Generate ACTIVITY suggestion
        suggestions.add(generateActivitySuggestion(userId, metrics, goals, forDate, now))
        
        // Generate SLEEP suggestion
        suggestions.add(generateSleepSuggestion(userId, metrics, goals, forDate, now))
        
        // Generate NUTRITION suggestion
        suggestions.add(generateNutritionSuggestion(userId, metrics, forDate, now))
        
        // Generate HYDRATION suggestion
        suggestions.add(generateHydrationSuggestion(userId, goals, forDate, now))
        
        // Add anomaly-based suggestions if any
        input.recentAnomalies.forEach { anomaly ->
            generateAnomalySuggestion(userId, anomaly, forDate, now)?.let {
                suggestions.add(it)
            }
        }
        
        return suggestions.sortedBy { it.priority }
    }
    
    private fun generateActivitySuggestion(
        userId: String,
        metrics: HealthMetrics,
        goals: UserGoals,
        forDate: LocalDate,
        now: Instant
    ): Suggestion {
        val stepsPercent = (metrics.steps.toDouble() / goals.dailyStepsTarget) * 100
        
        val (title, description, priority) = when {
            stepsPercent < 50 -> Triple(
                "Boost Your Activity",
                "You reached ${metrics.steps} steps yesterday. Try to hit ${goals.dailyStepsTarget} steps today! A 30-minute walk can help you get there.",
                1
            )
            stepsPercent < 80 -> Triple(
                "Keep Moving",
                "Great progress with ${metrics.steps} steps! You're close to your goal. A short evening walk could help you reach ${goals.dailyStepsTarget} steps.",
                2
            )
            stepsPercent < 100 -> Triple(
                "Almost There",
                "Excellent! ${metrics.steps} steps yesterday. Just a bit more to hit your ${goals.dailyStepsTarget} step goal today.",
                3
            )
            else -> Triple(
                "Maintain Your Momentum",
                "Amazing! You exceeded your step goal with ${metrics.steps} steps. Keep up the great work today!",
                4
            )
        }
        
        return Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.ACTIVITY,
            title = title,
            description = description,
            priority = priority,
            actionable = true,
            action = SuggestionAction.OpenStepTracker,
            generatedAt = now,
            forDate = forDate
        )
    }
    
    private fun generateSleepSuggestion(
        userId: String,
        metrics: HealthMetrics,
        goals: UserGoals,
        forDate: LocalDate,
        now: Instant
    ): Suggestion {
        val sleepHours = metrics.sleepDurationMinutes / 60.0
        val targetHours = goals.dailySleepMinutesTarget / 60.0
        
        val (title, description, priority, bedtime) = when {
            sleepHours < 6 -> Quadruple(
                "Prioritize Sleep Tonight",
                "You only got ${String.format("%.1f", sleepHours)} hours of sleep. Aim for ${targetHours.toInt()} hours tonight. Try going to bed by 10:00 PM.",
                1,
                "22:00"
            )
            sleepHours < 7 -> Quadruple(
                "Get More Rest",
                "You slept ${String.format("%.1f", sleepHours)} hours. Try to get ${targetHours.toInt()} hours tonight for better recovery.",
                2,
                "22:30"
            )
            sleepHours < 8 -> Quadruple(
                "Optimize Your Sleep",
                "Good sleep at ${String.format("%.1f", sleepHours)} hours! For optimal health, aim for ${targetHours.toInt()} hours.",
                3,
                "23:00"
            )
            else -> Quadruple(
                "Great Sleep Pattern",
                "Excellent! ${String.format("%.1f", sleepHours)} hours of sleep. Maintain this healthy pattern tonight.",
                4,
                "23:00"
            )
        }
        
        return Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.SLEEP,
            title = title,
            description = description,
            priority = priority,
            actionable = true,
            action = SuggestionAction.SetSleepReminder(bedtime),
            generatedAt = now,
            forDate = forDate
        )
    }
    
    private fun generateNutritionSuggestion(
        userId: String,
        metrics: HealthMetrics,
        forDate: LocalDate,
        now: Instant
    ): Suggestion {
        // Base nutrition suggestion on activity level
        val isHighActivity = metrics.steps > 8000
        
        val (title, description) = if (isHighActivity) {
            "Fuel Your Active Day" to "With ${metrics.steps} steps yesterday, make sure to eat enough protein and complex carbs today to support your activity level."
        } else {
            "Balanced Nutrition" to "Focus on whole foods today - vegetables, lean proteins, and whole grains. Track your meals to stay on target."
        }
        
        return Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.NUTRITION,
            title = title,
            description = description,
            priority = 3,
            actionable = true,
            action = SuggestionAction.OpenDietTracker,
            generatedAt = now,
            forDate = forDate
        )
    }
    
    private fun generateHydrationSuggestion(
        userId: String,
        goals: UserGoals,
        forDate: LocalDate,
        now: Instant
    ): Suggestion {
        val targetLiters = goals.dailyWaterMlTarget / 1000.0
        
        return Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.HYDRATION,
            title = "Stay Hydrated",
            description = "Aim for ${String.format("%.1f", targetLiters)} liters of water today. Start your morning with a glass of water and keep a bottle nearby.",
            priority = 2,
            actionable = true,
            action = SuggestionAction.LogWater(goals.dailyWaterMlTarget),
            generatedAt = now,
            forDate = forDate
        )
    }
    
    private fun generateAnomalySuggestion(
        userId: String,
        anomaly: Anomaly,
        forDate: LocalDate,
        now: Instant
    ): Suggestion? {
        return when (anomaly.type) {
            AnomalyType.LOW_ACTIVITY -> Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.ACTIVITY,
                title = "Address Low Activity",
                description = "Your activity has been below normal recently. Try scheduling short walks throughout the day.",
                priority = 1,
                actionable = true,
                action = SuggestionAction.OpenStepTracker,
                generatedAt = now,
                forDate = forDate
            )
            AnomalyType.IRREGULAR_SLEEP -> Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.SLEEP,
                title = "Improve Sleep Consistency",
                description = "Your sleep pattern has been irregular. Try maintaining a consistent bedtime this week.",
                priority = 1,
                actionable = true,
                action = SuggestionAction.SetSleepReminder("22:00"),
                generatedAt = now,
                forDate = forDate
            )
            AnomalyType.HIGH_STRESS -> Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.MENTAL_HEALTH,
                title = "Manage Stress",
                description = "Your stress levels have been elevated. Consider a short meditation session today.",
                priority = 1,
                actionable = true,
                action = SuggestionAction.StartMeditation("breathing-5min"),
                generatedAt = now,
                forDate = forDate
            )
            else -> null
        }
    }
    
    private fun generateGenericSuggestions(
        userId: String,
        forDate: LocalDate,
        now: Instant
    ): List<Suggestion> {
        return listOf(
            Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.ACTIVITY,
                title = "Start Moving",
                description = "Aim for 10,000 steps today. Every step counts towards better health!",
                priority = 2,
                actionable = true,
                action = SuggestionAction.OpenStepTracker,
                generatedAt = now,
                forDate = forDate
            ),
            Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.SLEEP,
                title = "Prioritize Rest",
                description = "Aim for 7-8 hours of quality sleep tonight. Good sleep is essential for health.",
                priority = 2,
                actionable = true,
                action = SuggestionAction.SetSleepReminder("22:30"),
                generatedAt = now,
                forDate = forDate
            ),
            Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.NUTRITION,
                title = "Eat Well",
                description = "Focus on balanced meals with plenty of vegetables, lean protein, and whole grains.",
                priority = 3,
                actionable = true,
                action = SuggestionAction.OpenDietTracker,
                generatedAt = now,
                forDate = forDate
            ),
            Suggestion(
                id = UUID.randomUUID().toString(),
                userId = userId,
                type = SuggestionType.HYDRATION,
                title = "Stay Hydrated",
                description = "Drink at least 2.5 liters of water today. Keep a water bottle with you!",
                priority = 2,
                actionable = true,
                action = SuggestionAction.LogWater(2500),
                generatedAt = now,
                forDate = forDate
            )
        )
    }
    
    override fun getTodaySuggestions(): Flow<List<Suggestion>> {
        return suggestionRepository.getTodaySuggestions()
    }
    
    override fun getSuggestions(date: LocalDate): Flow<List<Suggestion>> {
        return suggestionRepository.getSuggestions(date)
    }
    
    override suspend fun dismissSuggestion(suggestionId: String): Result<Unit> {
        return suggestionRepository.dismissSuggestion(suggestionId)
    }
    
    override suspend fun completeSuggestion(suggestionId: String): Result<Unit> {
        return suggestionRepository.completeSuggestion(suggestionId)
    }
    
    override suspend fun hasTodaySuggestions(): Boolean {
        return suggestionRepository.hasSuggestionsForDate(LocalDate.now())
    }
    
    override suspend fun cleanupOldSuggestions(daysToKeep: Int) {
        suggestionRepository.deleteOldSuggestions(daysToKeep)
    }
}

/**
 * Helper data class for quadruple values.
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
