package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.AvatarQuery
import com.healthtracker.domain.model.AvatarQueryInput
import com.healthtracker.domain.model.AvatarQueryResult
import com.healthtracker.domain.model.AvatarResponse
import com.healthtracker.domain.model.AvatarState
import com.healthtracker.domain.model.HealthContext
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.MetricReference
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.QueryIntent
import com.healthtracker.domain.model.QueryType
import com.healthtracker.domain.model.ResponseType
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.usecase.AvatarUseCase
import com.healthtracker.ml.MLResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AvatarUseCase with MLResult wrapper for graceful fallback.
 * 
 * Uses rule-based intent recognition with ML enhancement when available.
 * All ML operations are wrapped in MLResult for safety.
 */
@Singleton
class AvatarUseCaseImpl @Inject constructor(
    private val healthDataRepository: HealthDataRepository
) : AvatarUseCase {
    
    private val _avatarState = MutableStateFlow(AvatarState.MINIMIZED)
    private val conversationHistory = mutableListOf<AvatarResponse>()
    
    companion object {
        private const val ML_TIMEOUT_MS = 200L
        private const val MAX_HISTORY_SIZE = 20
        
        // Intent recognition patterns (rule-based fallback)
        private val STEPS_PATTERNS = listOf(
            "step", "walk", "walking", "walked", "steps today", "how many steps"
        )
        private val CALORIES_PATTERNS = listOf(
            "calorie", "calories", "burned", "burn", "energy"
        )
        private val SLEEP_PATTERNS = listOf(
            "sleep", "slept", "rest", "sleeping", "hours of sleep"
        )
        private val HEART_RATE_PATTERNS = listOf(
            "heart", "heart rate", "pulse", "bpm", "heartbeat"
        )
        private val PROGRESS_PATTERNS = listOf(
            "progress", "goal", "goals", "target", "achievement"
        )
        private val ADVICE_PATTERNS = listOf(
            "advice", "suggest", "recommendation", "help", "what should", "how can"
        )
        private val GREETING_PATTERNS = listOf(
            "hello", "hi", "hey", "good morning", "good afternoon", "good evening"
        )
        private val OVERALL_PATTERNS = listOf(
            "health", "overall", "summary", "how am i", "status", "today"
        )
    }
    
    override suspend fun processQuery(input: AvatarQueryInput): AvatarQueryResult {
        return try {
            _avatarState.value = AvatarState.PROCESSING
            
            // Create query record
            val query = AvatarQuery(
                id = UUID.randomUUID().toString(),
                text = input.query,
                type = QueryType.TEXT,
                timestamp = Instant.now()
            )
            
            // Get health context
            val healthContext = getHealthContext(input.userId, input.currentDate)
            
            // Recognize intent with ML fallback to rule-based
            val intent = recognizeIntent(input.query)
            
            // Generate response based on intent
            val response = generateResponse(query, intent, healthContext)
            
            // Add to history
            addToHistory(response)
            
            _avatarState.value = AvatarState.RESPONDING
            AvatarQueryResult.Success(response)
        } catch (e: Exception) {
            _avatarState.value = AvatarState.IDLE
            AvatarQueryResult.Error("Failed to process query: ${e.message}", e)
        }
    }
    
    override suspend fun processVoiceInput(audioData: ByteArray, userId: String): AvatarQueryResult {
        // Voice processing would use speech-to-text
        // For now, return error as speech recognition requires platform integration
        return AvatarQueryResult.Error(
            "Voice input requires speech recognition setup. Please use text input."
        )
    }
    
    override fun getAvatarState(): Flow<AvatarState> = _avatarState.asStateFlow()
    
    override suspend fun updateAvatarState(state: AvatarState) {
        _avatarState.value = state
    }
    
    override suspend fun getConversationHistory(): List<AvatarResponse> {
        return conversationHistory.toList()
    }
    
    override suspend fun clearConversationHistory() {
        conversationHistory.clear()
    }
    
    /**
     * Recognizes the intent of a query using ML with rule-based fallback.
     */
    private suspend fun recognizeIntent(query: String): QueryIntent {
        // Try ML-based intent recognition with timeout
        val mlResult = MLResult.runWithTimeout(
            timeoutMs = ML_TIMEOUT_MS,
            fallbackReason = "Intent recognition timed out"
        ) {
            // ML model would go here
            // For now, use rule-based as primary
            recognizeIntentRuleBased(query)
        }
        
        return when (mlResult) {
            is MLResult.Success -> mlResult.data
            is MLResult.Fallback -> recognizeIntentRuleBased(query)
        }
    }
    
    /**
     * Rule-based intent recognition (fallback).
     */
    private fun recognizeIntentRuleBased(query: String): QueryIntent {
        val lowerQuery = query.lowercase()
        
        return when {
            GREETING_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.GREETING
            STEPS_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_STEPS
            CALORIES_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_CALORIES
            SLEEP_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_SLEEP
            HEART_RATE_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_HEART_RATE
            PROGRESS_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_PROGRESS
            ADVICE_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.REQUEST_ADVICE
            OVERALL_PATTERNS.any { lowerQuery.contains(it) } -> QueryIntent.QUERY_OVERALL
            else -> QueryIntent.UNKNOWN
        }
    }
    
    /**
     * Gets health context for response generation.
     */
    private suspend fun getHealthContext(userId: String, date: LocalDate): HealthContext {
        val todayMetrics = try {
            healthDataRepository.getHealthMetrics(date).firstOrNull()
        } catch (e: Exception) {
            null
        }
        
        val weeklyMetrics = try {
            val weekStart = date.minusDays(6)
            healthDataRepository.getHealthMetricsRange(weekStart, date).firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        val weeklyAverages = calculateWeeklyAverages(weeklyMetrics)
        
        return HealthContext(
            todayMetrics = todayMetrics,
            weeklyAverages = weeklyAverages,
            recentInsights = emptyList(),
            activeGoals = emptyList()
        )
    }
    
    /**
     * Calculates weekly averages from metrics list.
     */
    private fun calculateWeeklyAverages(metrics: List<HealthMetrics>): Map<MetricType, Double> {
        if (metrics.isEmpty()) return emptyMap()
        
        val result = mutableMapOf<MetricType, Double>()
        result[MetricType.STEPS] = metrics.map { it.steps.toDouble() }.average()
        result[MetricType.CALORIES] = metrics.map { it.caloriesBurned }.average()
        result[MetricType.SLEEP] = metrics.map { it.sleepDurationMinutes.toDouble() }.average()
        val heartRateAvg = metrics.flatMap { it.heartRateSamples }
            .map { it.bpm.toDouble() }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0
        result[MetricType.HEART_RATE] = heartRateAvg
        return result
    }
    
    /**
     * Generates a response based on intent and health context.
     */
    private fun generateResponse(
        query: AvatarQuery,
        intent: QueryIntent,
        context: HealthContext
    ): AvatarResponse {
        val responseData: Triple<String, ResponseType, List<MetricReference>?> = when (intent) {
            QueryIntent.GREETING -> generateGreetingResponse()
            QueryIntent.QUERY_STEPS -> generateStepsResponse(context)
            QueryIntent.QUERY_CALORIES -> generateCaloriesResponse(context)
            QueryIntent.QUERY_SLEEP -> generateSleepResponse(context)
            QueryIntent.QUERY_HEART_RATE -> generateHeartRateResponse(context)
            QueryIntent.QUERY_PROGRESS -> generateProgressResponse(context)
            QueryIntent.REQUEST_ADVICE -> generateAdviceResponse(context)
            QueryIntent.QUERY_OVERALL -> generateOverallResponse(context)
            QueryIntent.UNKNOWN -> generateUnknownResponse()
        }
        
        return AvatarResponse(
            id = UUID.randomUUID().toString(),
            queryId = query.id,
            text = responseData.first,
            type = responseData.second,
            metrics = responseData.third,
            suggestions = generateSuggestions(intent, context),
            timestamp = Instant.now()
        )
    }

    
    private fun generateGreetingResponse(): Triple<String, ResponseType, List<MetricReference>?> {
        val greetings = listOf(
            "Hello! I'm your health assistant. How can I help you today?",
            "Hi there! Ready to check on your health stats?",
            "Hey! What would you like to know about your health today?"
        )
        return Triple(greetings.random(), ResponseType.GREETING, null)
    }
    
    private fun generateStepsResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val todaySteps = context.todayMetrics?.steps ?: 0
        val weeklyAvg = context.weeklyAverages[MetricType.STEPS]?.toInt() ?: 0
        
        val text = if (todaySteps > 0) {
            val comparison = when {
                todaySteps > weeklyAvg * 1.2 -> "That's above your weekly average of $weeklyAvg steps! Great job! 🎉"
                todaySteps < weeklyAvg * 0.8 -> "That's below your weekly average of $weeklyAvg steps. Try to get moving!"
                else -> "That's right around your weekly average of $weeklyAvg steps."
            }
            "You've taken $todaySteps steps today. $comparison"
        } else {
            "I don't have step data for today yet. Make sure your health tracking is enabled!"
        }
        
        val metrics = if (todaySteps > 0) {
            listOf(
                MetricReference(
                    type = MetricType.STEPS,
                    value = todaySteps.toDouble(),
                    unit = "steps",
                    date = LocalDate.now(),
                    formattedValue = "$todaySteps steps"
                )
            )
        } else null
        
        return Triple(text, ResponseType.METRIC_DATA, metrics)
    }
    
    private fun generateCaloriesResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val todayCalories = context.todayMetrics?.caloriesBurned?.toInt() ?: 0
        val weeklyAvg = context.weeklyAverages[MetricType.CALORIES]?.toInt() ?: 0
        
        val text = if (todayCalories > 0) {
            "You've burned $todayCalories calories today. Your weekly average is $weeklyAvg calories."
        } else {
            "I don't have calorie data for today yet. Keep moving to burn those calories!"
        }
        
        val metrics = if (todayCalories > 0) {
            listOf(
                MetricReference(
                    type = MetricType.CALORIES,
                    value = todayCalories.toDouble(),
                    unit = "kcal",
                    date = LocalDate.now(),
                    formattedValue = "$todayCalories kcal"
                )
            )
        } else null
        
        return Triple(text, ResponseType.METRIC_DATA, metrics)
    }
    
    private fun generateSleepResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val sleepMinutes = context.todayMetrics?.sleepDurationMinutes ?: 0
        val sleepHours = sleepMinutes / 60
        val sleepMins = sleepMinutes % 60
        val weeklyAvgMinutes = context.weeklyAverages[MetricType.SLEEP]?.toInt() ?: 0
        val weeklyAvgHours = weeklyAvgMinutes / 60
        
        val text = if (sleepMinutes > 0) {
            val quality = context.todayMetrics?.sleepQuality?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
            val recommendation = when {
                sleepMinutes < 360 -> "Try to get at least 7-8 hours for optimal health."
                sleepMinutes > 540 -> "That's plenty of rest!"
                else -> "That's a healthy amount of sleep!"
            }
            "You slept ${sleepHours}h ${sleepMins}m last night with $quality quality. $recommendation Your weekly average is ${weeklyAvgHours}h."
        } else {
            "I don't have sleep data for last night. Make sure your sleep tracking is enabled!"
        }
        
        val metrics = if (sleepMinutes > 0) {
            listOf(
                MetricReference(
                    type = MetricType.SLEEP,
                    value = sleepMinutes.toDouble(),
                    unit = "minutes",
                    date = LocalDate.now(),
                    formattedValue = "${sleepHours}h ${sleepMins}m"
                )
            )
        } else null
        
        return Triple(text, ResponseType.METRIC_DATA, metrics)
    }
    
    private fun generateHeartRateResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val heartRateSamples = context.todayMetrics?.heartRateSamples ?: emptyList()
        
        val text = if (heartRateSamples.isNotEmpty()) {
            val avgHr = heartRateSamples.map { it.bpm }.average().toInt()
            val minHr = heartRateSamples.minOf { it.bpm }
            val maxHr = heartRateSamples.maxOf { it.bpm }
            
            val status = when {
                avgHr < 60 -> "Your resting heart rate is quite low, which can indicate good cardiovascular fitness."
                avgHr > 100 -> "Your heart rate is elevated. Consider relaxing or checking if you're stressed."
                else -> "Your heart rate is in a healthy range."
            }
            
            "Your average heart rate today is $avgHr bpm (range: $minHr-$maxHr bpm). $status"
        } else {
            "I don't have heart rate data for today. Connect a wearable device to track your heart rate!"
        }
        
        val metrics = if (heartRateSamples.isNotEmpty()) {
            val avgHr = heartRateSamples.map { it.bpm }.average()
            listOf(
                MetricReference(
                    type = MetricType.HEART_RATE,
                    value = avgHr,
                    unit = "bpm",
                    date = LocalDate.now(),
                    formattedValue = "${avgHr.toInt()} bpm"
                )
            )
        } else null
        
        return Triple(text, ResponseType.METRIC_DATA, metrics)
    }
    
    private fun generateProgressResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val steps = context.todayMetrics?.steps ?: 0
        val stepsGoal = 10000
        val stepsPercent = ((steps.toDouble() / stepsGoal) * 100).toInt().coerceAtMost(100)
        
        val text = "Here's your progress today:\n" +
            "• Steps: $steps / $stepsGoal ($stepsPercent%)\n" +
            "Keep going to reach your goals! 💪"
        
        return Triple(text, ResponseType.METRIC_DATA, null)
    }
    
    private fun generateAdviceResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val advice = mutableListOf<String>()
        
        context.todayMetrics?.let { metrics ->
            if (metrics.steps < 5000) {
                advice.add("Try to take a short walk to boost your step count.")
            }
            if (metrics.sleepDurationMinutes < 420) {
                advice.add("Consider going to bed earlier tonight for better rest.")
            }
            if (metrics.heartRateSamples.isNotEmpty()) {
                val avgHr = metrics.heartRateSamples.map { it.bpm }.average()
                if (avgHr > 80) {
                    advice.add("Try some deep breathing exercises to lower your heart rate.")
                }
            }
        }
        
        if (advice.isEmpty()) {
            advice.add("You're doing great! Keep maintaining your healthy habits.")
            advice.add("Stay hydrated and take regular breaks if you're sitting for long periods.")
        }
        
        val text = "Here's my advice for you:\n" + advice.joinToString("\n") { "• $it" }
        
        return Triple(text, ResponseType.ADVICE, null)
    }
    
    private fun generateOverallResponse(context: HealthContext): Triple<String, ResponseType, List<MetricReference>?> {
        val metrics = context.todayMetrics
        
        val text = if (metrics != null) {
            val sleepHours = metrics.sleepDurationMinutes / 60
            val sleepMins = metrics.sleepDurationMinutes % 60
            
            "Here's your health summary for today:\n" +
                "• Steps: ${metrics.steps}\n" +
                "• Calories burned: ${metrics.caloriesBurned.toInt()} kcal\n" +
                "• Sleep: ${sleepHours}h ${sleepMins}m\n" +
                if (metrics.heartRateSamples.isNotEmpty()) {
                    "• Avg heart rate: ${metrics.heartRateSamples.map { it.bpm }.average().toInt()} bpm"
                } else ""
        } else {
            "I don't have health data for today yet. Make sure your tracking is enabled and check back later!"
        }
        
        val metricRefs = metrics?.let {
            listOf(
                MetricReference(MetricType.STEPS, it.steps.toDouble(), "steps", LocalDate.now(), "${it.steps} steps"),
                MetricReference(MetricType.CALORIES, it.caloriesBurned, "kcal", LocalDate.now(), "${it.caloriesBurned.toInt()} kcal"),
                MetricReference(MetricType.SLEEP, it.sleepDurationMinutes.toDouble(), "min", LocalDate.now(), "${it.sleepDurationMinutes / 60}h ${it.sleepDurationMinutes % 60}m")
            )
        }
        
        return Triple(text, ResponseType.METRIC_DATA, metricRefs)
    }
    
    private fun generateUnknownResponse(): Triple<String, ResponseType, List<MetricReference>?> {
        val responses = listOf(
            "I'm not sure I understand. You can ask me about your steps, sleep, calories, or heart rate!",
            "Could you rephrase that? I can help with health metrics like steps, sleep, and calories.",
            "I didn't quite catch that. Try asking about your daily health stats!"
        )
        return Triple(responses.random(), ResponseType.CLARIFICATION, null)
    }
    
    private fun generateSuggestions(intent: QueryIntent, context: HealthContext): List<String> {
        return when (intent) {
            QueryIntent.QUERY_STEPS -> listOf("How many calories did I burn?", "What's my weekly average?")
            QueryIntent.QUERY_CALORIES -> listOf("How many steps today?", "Give me health advice")
            QueryIntent.QUERY_SLEEP -> listOf("How's my heart rate?", "Show my overall health")
            QueryIntent.QUERY_HEART_RATE -> listOf("How did I sleep?", "Give me health advice")
            QueryIntent.GREETING -> listOf("Show my health summary", "How many steps today?")
            else -> listOf("Show my health summary", "Give me health advice")
        }
    }
    
    private fun addToHistory(response: AvatarResponse) {
        conversationHistory.add(response)
        if (conversationHistory.size > MAX_HISTORY_SIZE) {
            conversationHistory.removeAt(0)
        }
    }
}
