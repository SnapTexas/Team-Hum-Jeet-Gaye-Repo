package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Domain models for the AI Avatar feature.
 */

/**
 * Represents the current state of the AI Avatar.
 */
enum class AvatarState {
    /** Avatar is minimized as a floating button */
    MINIMIZED,
    /** Avatar is expanded and ready for input */
    EXPANDED,
    /** Avatar is listening for voice input */
    LISTENING,
    /** Avatar is processing a query */
    PROCESSING,
    /** Avatar is speaking/displaying response */
    RESPONDING,
    /** Avatar is in idle animation state */
    IDLE
}

/**
 * Represents a query to the AI Avatar.
 */
data class AvatarQuery(
    val id: String,
    val text: String,
    val type: QueryType,
    val timestamp: Instant
)

/**
 * Type of query input.
 */
enum class QueryType {
    TEXT,
    VOICE
}

/**
 * Response from the AI Avatar.
 */
data class AvatarResponse(
    val id: String,
    val queryId: String,
    val text: String,
    val type: ResponseType,
    val metrics: List<MetricReference>?,
    val suggestions: List<String>?,
    val timestamp: Instant
)

/**
 * Type of avatar response.
 */
enum class ResponseType {
    /** General greeting or acknowledgment */
    GREETING,
    /** Response containing health metric data */
    METRIC_DATA,
    /** Health advice or suggestion */
    ADVICE,
    /** Error or unable to process */
    ERROR,
    /** Clarification request */
    CLARIFICATION
}

/**
 * Reference to a specific health metric in a response.
 */
data class MetricReference(
    val type: MetricType,
    val value: Double,
    val unit: String,
    val date: LocalDate,
    val formattedValue: String
)

/**
 * Input for avatar query processing.
 */
data class AvatarQueryInput(
    val query: String,
    val userId: String,
    val currentDate: LocalDate = LocalDate.now()
)

/**
 * Result of avatar query processing.
 */
sealed class AvatarQueryResult {
    data class Success(val response: AvatarResponse) : AvatarQueryResult()
    data class Error(val message: String, val exception: Throwable? = null) : AvatarQueryResult()
}

/**
 * Health context for avatar responses.
 */
data class HealthContext(
    val todayMetrics: HealthMetrics?,
    val weeklyAverages: Map<MetricType, Double>,
    val recentInsights: List<String>,
    val activeGoals: List<GoalProgress>
)

/**
 * Recognized health query intent.
 */
enum class QueryIntent {
    /** User asking about steps */
    QUERY_STEPS,
    /** User asking about calories */
    QUERY_CALORIES,
    /** User asking about sleep */
    QUERY_SLEEP,
    /** User asking about heart rate */
    QUERY_HEART_RATE,
    /** User asking about overall health */
    QUERY_OVERALL,
    /** User asking for advice */
    REQUEST_ADVICE,
    /** User greeting the avatar */
    GREETING,
    /** User asking about goals/progress */
    QUERY_PROGRESS,
    /** Unknown intent */
    UNKNOWN
}
