package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * Daily analytics containing metrics, insights, and goal progress for a single day.
 */
data class DailyAnalytics(
    val date: LocalDate,
    val metrics: HealthMetrics?,
    val insights: List<Insight>,
    val goalProgress: List<GoalProgress>
)

/**
 * Weekly analytics with aggregated data across 7 days.
 */
data class WeeklyAnalytics(
    val weekStart: LocalDate,
    val dailyMetrics: List<HealthMetrics>,
    val averages: MetricAverages,
    val trends: List<TrendAnalysis>,
    val insights: List<Insight>
)

/**
 * Monthly analytics with aggregated data across a calendar month.
 */
data class MonthlyAnalytics(
    val month: YearMonth,
    val weeklyData: List<WeeklyAnalytics>,
    val monthlyAverages: MetricAverages,
    val trends: List<TrendAnalysis>,
    val insights: List<Insight>
)

/**
 * Trend analysis for a specific metric over time.
 */
data class TrendAnalysis(
    val metricType: MetricType,
    val direction: TrendDirection,
    val percentageChange: Double,
    val dataPoints: List<DataPoint>
)

/**
 * Direction of a metric trend.
 */
enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE
}

/**
 * A single data point for trend visualization.
 */
data class DataPoint(
    val date: LocalDate,
    val value: Double
)

/**
 * Aggregated metric averages.
 */
data class MetricAverages(
    val averageSteps: Double,
    val averageDistance: Double,
    val averageCalories: Double,
    val averageSleepMinutes: Double,
    val averageScreenTimeMinutes: Double,
    val averageHeartRate: Double?,
    val averageHrv: Double?
)

/**
 * An insight generated from health data analysis.
 */
data class Insight(
    val id: String,
    val type: InsightType,
    val message: String,
    val priority: InsightPriority,
    val relatedMetrics: List<MetricType>,
    val generatedAt: Instant = Instant.now()
)

/**
 * Type of insight.
 */
enum class InsightType {
    ACHIEVEMENT,
    WARNING,
    SUGGESTION,
    TREND
}

/**
 * Priority level of an insight.
 */
enum class InsightPriority {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Progress toward a specific goal.
 */
data class GoalProgress(
    val goalType: MetricType,
    val target: Double,
    val current: Double
) {
    /**
     * Percentage of goal completed (0-100+).
     * Can exceed 100 if user surpasses target.
     */
    val percentComplete: Float
        get() = if (target > 0) ((current / target) * 100).toFloat() else 0f
}

/**
 * Analytics period for querying.
 */
sealed class AnalyticsPeriod {
    data class Daily(val date: LocalDate) : AnalyticsPeriod()
    data class Weekly(val weekStart: LocalDate) : AnalyticsPeriod()
    data class Monthly(val month: YearMonth) : AnalyticsPeriod()
}

/**
 * Default health goals for users.
 */
object DefaultGoals {
    const val DAILY_STEPS = 10000.0
    const val DAILY_CALORIES = 2000.0
    const val DAILY_SLEEP_MINUTES = 480.0 // 8 hours
    const val DAILY_SCREEN_TIME_MINUTES = 120.0 // 2 hours max
    const val DAILY_WATER_ML = 2500.0
}
