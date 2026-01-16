package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.DailyAnalytics
import com.healthtracker.domain.model.DataPoint
import com.healthtracker.domain.model.DefaultGoals
import com.healthtracker.domain.model.GoalProgress
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Insight
import com.healthtracker.domain.model.InsightPriority
import com.healthtracker.domain.model.InsightType
import com.healthtracker.domain.model.MetricAverages
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.MonthlyAnalytics
import com.healthtracker.domain.model.TrendAnalysis
import com.healthtracker.domain.model.TrendDirection
import com.healthtracker.domain.model.WeeklyAnalytics
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.usecase.AnalyticsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of AnalyticsUseCase.
 * Provides health data aggregation, insight generation, and trend analysis.
 */
class AnalyticsUseCaseImpl @Inject constructor(
    private val healthDataRepository: HealthDataRepository
) : AnalyticsUseCase {
    
    override fun getDailyAnalytics(date: LocalDate): Flow<DailyAnalytics> {
        return healthDataRepository.getHealthMetrics(date).map { metrics ->
            val insights = if (metrics != null) generateInsights(metrics) else emptyList()
            val goalProgress = if (metrics != null) calculateGoalProgress(metrics) else emptyList()
            
            DailyAnalytics(
                date = date,
                metrics = metrics,
                insights = insights,
                goalProgress = goalProgress
            )
        }
    }
    
    override fun getWeeklyAnalytics(weekStart: LocalDate): Flow<WeeklyAnalytics> {
        val weekEnd = weekStart.plusDays(6)
        return healthDataRepository.getHealthMetricsRange(weekStart, weekEnd).map { metricsList ->
            val averages = calculateAverages(metricsList)
            val trends = calculateTrends(metricsList)
            val insights = generateInsights(metricsList)
            
            WeeklyAnalytics(
                weekStart = weekStart,
                dailyMetrics = metricsList,
                averages = averages,
                trends = trends,
                insights = insights
            )
        }
    }
    
    override fun getMonthlyAnalytics(month: YearMonth): Flow<MonthlyAnalytics> {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        
        return healthDataRepository.getHealthMetricsRange(monthStart, monthEnd).map { metricsList ->
            val weeklyData = groupByWeek(metricsList, monthStart)
            val monthlyAverages = calculateAverages(metricsList)
            val trends = calculateTrends(metricsList)
            val insights = generateInsights(metricsList)
            
            MonthlyAnalytics(
                month = month,
                weeklyData = weeklyData,
                monthlyAverages = monthlyAverages,
                trends = trends,
                insights = insights
            )
        }
    }
    
    override fun generateInsights(metrics: HealthMetrics): List<Insight> {
        return generateInsights(listOf(metrics))
    }
    
    override fun generateInsights(metricsList: List<HealthMetrics>): List<Insight> {
        if (metricsList.isEmpty()) return emptyList()
        
        val insights = mutableListOf<Insight>()
        val averages = calculateAverages(metricsList)
        val latestMetrics = metricsList.maxByOrNull { it.date }
        
        // Step insights
        latestMetrics?.let { metrics ->
            val stepGoalPercent = (metrics.steps / DefaultGoals.DAILY_STEPS) * 100
            when {
                stepGoalPercent >= 100 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.ACHIEVEMENT,
                        message = "Great job! You've reached your daily step goal of ${DefaultGoals.DAILY_STEPS.toInt()} steps!",
                        priority = InsightPriority.HIGH,
                        relatedMetrics = listOf(MetricType.STEPS)
                    )
                )
                stepGoalPercent >= 75 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.SUGGESTION,
                        message = "You're ${(100 - stepGoalPercent).toInt()}% away from your step goal. A short walk could help!",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.STEPS)
                    )
                )
                stepGoalPercent < 50 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.WARNING,
                        message = "Your step count is below 50% of your daily goal. Try to move more today!",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.STEPS)
                    )
                )
                else -> { /* No insight for 50-75% range */ }
            }
        }
        
        // Sleep insights
        latestMetrics?.let { metrics ->
            val sleepHours = metrics.sleepDurationMinutes / 60.0
            when {
                sleepHours >= 7 && sleepHours <= 9 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.ACHIEVEMENT,
                        message = "Excellent sleep! You got ${String.format("%.1f", sleepHours)} hours of rest.",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.SLEEP)
                    )
                )
                sleepHours < 6 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.WARNING,
                        message = "You only slept ${String.format("%.1f", sleepHours)} hours. Aim for 7-9 hours for optimal health.",
                        priority = InsightPriority.HIGH,
                        relatedMetrics = listOf(MetricType.SLEEP)
                    )
                )
                sleepHours > 10 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.SUGGESTION,
                        message = "You slept ${String.format("%.1f", sleepHours)} hours. Oversleeping can affect energy levels.",
                        priority = InsightPriority.LOW,
                        relatedMetrics = listOf(MetricType.SLEEP)
                    )
                )
                else -> { /* No insight for 6-7 or 9-10 hour range */ }
            }
        }
        
        // Screen time insights
        latestMetrics?.let { metrics ->
            when {
                metrics.screenTimeMinutes > 240 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.WARNING,
                        message = "High screen time detected (${metrics.screenTimeMinutes / 60}h ${metrics.screenTimeMinutes % 60}m). Consider taking breaks.",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.SCREEN_TIME)
                    )
                )
                metrics.screenTimeMinutes <= 120 -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.ACHIEVEMENT,
                        message = "Great screen time management! You've kept it under 2 hours.",
                        priority = InsightPriority.LOW,
                        relatedMetrics = listOf(MetricType.SCREEN_TIME)
                    )
                )
                else -> { /* No insight for 120-240 minute range */ }
            }
        }
        
        // Heart rate insights
        latestMetrics?.heartRateSamples?.let { samples ->
            if (samples.isNotEmpty()) {
                val avgHr = samples.map { it.bpm }.average()
                when {
                    avgHr > 100 -> insights.add(
                        Insight(
                            id = UUID.randomUUID().toString(),
                            type = InsightType.WARNING,
                            message = "Your average heart rate (${avgHr.toInt()} bpm) is elevated. Consider relaxation techniques.",
                            priority = InsightPriority.HIGH,
                            relatedMetrics = listOf(MetricType.HEART_RATE)
                        )
                    )
                    avgHr in 60.0..80.0 -> insights.add(
                        Insight(
                            id = UUID.randomUUID().toString(),
                            type = InsightType.ACHIEVEMENT,
                            message = "Your heart rate is in a healthy range (${avgHr.toInt()} bpm average).",
                            priority = InsightPriority.LOW,
                            relatedMetrics = listOf(MetricType.HEART_RATE)
                        )
                    )
                }
            }
        }
        
        // Trend insights for weekly/monthly data
        if (metricsList.size >= 3) {
            val stepsTrend = calculateTrendDirection(metricsList.map { it.steps.toDouble() })
            when (stepsTrend) {
                TrendDirection.INCREASING -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.TREND,
                        message = "Your step count is trending upward! Keep up the momentum.",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.STEPS)
                    )
                )
                TrendDirection.DECREASING -> insights.add(
                    Insight(
                        id = UUID.randomUUID().toString(),
                        type = InsightType.TREND,
                        message = "Your step count has been declining. Try to stay active!",
                        priority = InsightPriority.MEDIUM,
                        relatedMetrics = listOf(MetricType.STEPS)
                    )
                )
                TrendDirection.STABLE -> {} // No insight for stable trends
            }
        }
        
        // Ensure at least one insight is generated for non-empty metrics
        if (insights.isEmpty() && metricsList.isNotEmpty()) {
            insights.add(
                Insight(
                    id = UUID.randomUUID().toString(),
                    type = InsightType.SUGGESTION,
                    message = "Keep tracking your health metrics for personalized insights!",
                    priority = InsightPriority.LOW,
                    relatedMetrics = listOf(MetricType.STEPS)
                )
            )
        }
        
        return insights
    }
    
    override fun calculateAverages(metricsList: List<HealthMetrics>): MetricAverages {
        if (metricsList.isEmpty()) {
            return MetricAverages(
                averageSteps = 0.0,
                averageDistance = 0.0,
                averageCalories = 0.0,
                averageSleepMinutes = 0.0,
                averageScreenTimeMinutes = 0.0,
                averageHeartRate = null,
                averageHrv = null
            )
        }
        
        val count = metricsList.size.toDouble()
        
        // Calculate heart rate average from all samples
        val allHeartRateSamples = metricsList.flatMap { it.heartRateSamples }
        val avgHeartRate = if (allHeartRateSamples.isNotEmpty()) {
            allHeartRateSamples.map { it.bpm }.average()
        } else null
        
        // Calculate HRV average from all samples
        val allHrvSamples = metricsList.flatMap { it.hrvSamples }
        val avgHrv = if (allHrvSamples.isNotEmpty()) {
            allHrvSamples.map { it.sdnn }.average()
        } else null
        
        return MetricAverages(
            averageSteps = metricsList.sumOf { it.steps } / count,
            averageDistance = metricsList.sumOf { it.distanceMeters } / count,
            averageCalories = metricsList.sumOf { it.caloriesBurned } / count,
            averageSleepMinutes = metricsList.sumOf { it.sleepDurationMinutes } / count,
            averageScreenTimeMinutes = metricsList.sumOf { it.screenTimeMinutes } / count,
            averageHeartRate = avgHeartRate,
            averageHrv = avgHrv
        )
    }
    
    override fun getTrendAnalysis(metric: MetricType, days: Int): Flow<TrendAnalysis> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        
        return healthDataRepository.getHealthMetricsRange(startDate, endDate).map { metricsList ->
            val dataPoints = metricsList.map { metrics ->
                DataPoint(
                    date = metrics.date,
                    value = getMetricValue(metrics, metric)
                )
            }.sortedBy { it.date }
            
            val values = dataPoints.map { it.value }
            val direction = calculateTrendDirection(values)
            val percentageChange = calculatePercentageChange(values)
            
            TrendAnalysis(
                metricType = metric,
                direction = direction,
                percentageChange = percentageChange,
                dataPoints = dataPoints
            )
        }
    }
    
    private fun calculateGoalProgress(metrics: HealthMetrics): List<GoalProgress> {
        return listOf(
            GoalProgress(
                goalType = MetricType.STEPS,
                target = DefaultGoals.DAILY_STEPS,
                current = metrics.steps.toDouble()
            ),
            GoalProgress(
                goalType = MetricType.CALORIES,
                target = DefaultGoals.DAILY_CALORIES,
                current = metrics.caloriesBurned
            ),
            GoalProgress(
                goalType = MetricType.SLEEP,
                target = DefaultGoals.DAILY_SLEEP_MINUTES,
                current = metrics.sleepDurationMinutes.toDouble()
            ),
            GoalProgress(
                goalType = MetricType.SCREEN_TIME,
                target = DefaultGoals.DAILY_SCREEN_TIME_MINUTES,
                current = metrics.screenTimeMinutes.toDouble()
            )
        )
    }
    
    private fun calculateTrends(metricsList: List<HealthMetrics>): List<TrendAnalysis> {
        if (metricsList.size < 2) return emptyList()
        
        return listOf(
            createTrendAnalysis(metricsList, MetricType.STEPS) { it.steps.toDouble() },
            createTrendAnalysis(metricsList, MetricType.DISTANCE) { it.distanceMeters },
            createTrendAnalysis(metricsList, MetricType.CALORIES) { it.caloriesBurned },
            createTrendAnalysis(metricsList, MetricType.SLEEP) { it.sleepDurationMinutes.toDouble() },
            createTrendAnalysis(metricsList, MetricType.SCREEN_TIME) { it.screenTimeMinutes.toDouble() }
        )
    }
    
    private fun createTrendAnalysis(
        metricsList: List<HealthMetrics>,
        metricType: MetricType,
        valueExtractor: (HealthMetrics) -> Double
    ): TrendAnalysis {
        val dataPoints = metricsList.map { metrics ->
            DataPoint(date = metrics.date, value = valueExtractor(metrics))
        }.sortedBy { it.date }
        
        val values = dataPoints.map { it.value }
        
        return TrendAnalysis(
            metricType = metricType,
            direction = calculateTrendDirection(values),
            percentageChange = calculatePercentageChange(values),
            dataPoints = dataPoints
        )
    }
    
    private fun calculateTrendDirection(values: List<Double>): TrendDirection {
        if (values.size < 2) return TrendDirection.STABLE
        
        val firstHalf = values.take(values.size / 2)
        val secondHalf = values.drop(values.size / 2)
        
        val firstAvg = if (firstHalf.isNotEmpty()) firstHalf.average() else 0.0
        val secondAvg = if (secondHalf.isNotEmpty()) secondHalf.average() else 0.0
        
        val changePercent = if (firstAvg != 0.0) {
            ((secondAvg - firstAvg) / firstAvg) * 100
        } else 0.0
        
        return when {
            changePercent > 5 -> TrendDirection.INCREASING
            changePercent < -5 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculatePercentageChange(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        
        val first = values.first()
        val last = values.last()
        
        return if (first != 0.0) {
            ((last - first) / first) * 100
        } else 0.0
    }
    
    private fun getMetricValue(metrics: HealthMetrics, metricType: MetricType): Double {
        return when (metricType) {
            MetricType.STEPS -> metrics.steps.toDouble()
            MetricType.DISTANCE -> metrics.distanceMeters
            MetricType.CALORIES -> metrics.caloriesBurned
            MetricType.SCREEN_TIME -> metrics.screenTimeMinutes.toDouble()
            MetricType.SLEEP -> metrics.sleepDurationMinutes.toDouble()
            MetricType.HEART_RATE -> metrics.heartRateSamples.map { it.bpm }.average().takeIf { !it.isNaN() } ?: 0.0
            MetricType.HRV -> metrics.hrvSamples.map { it.sdnn }.average().takeIf { !it.isNaN() } ?: 0.0
            MetricType.MOOD -> metrics.mood?.ordinal?.toDouble() ?: 2.0 // Default to NEUTRAL
        }
    }
    
    private fun groupByWeek(
        metricsList: List<HealthMetrics>,
        monthStart: LocalDate
    ): List<WeeklyAnalytics> {
        val weeks = mutableListOf<WeeklyAnalytics>()
        var currentWeekStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        
        while (currentWeekStart.isBefore(monthEnd) || currentWeekStart.isEqual(monthEnd)) {
            val weekEnd = currentWeekStart.plusDays(6)
            val weekMetrics = metricsList.filter { metrics ->
                !metrics.date.isBefore(currentWeekStart) && !metrics.date.isAfter(weekEnd)
            }
            
            if (weekMetrics.isNotEmpty()) {
                weeks.add(
                    WeeklyAnalytics(
                        weekStart = currentWeekStart,
                        dailyMetrics = weekMetrics,
                        averages = calculateAverages(weekMetrics),
                        trends = calculateTrends(weekMetrics),
                        insights = generateInsights(weekMetrics)
                    )
                )
            }
            
            currentWeekStart = currentWeekStart.plusWeeks(1)
        }
        
        return weeks
    }
}
