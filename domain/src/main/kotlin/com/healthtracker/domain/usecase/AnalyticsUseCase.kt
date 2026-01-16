package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.AnalyticsPeriod
import com.healthtracker.domain.model.DailyAnalytics
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Insight
import com.healthtracker.domain.model.MetricAverages
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.MonthlyAnalytics
import com.healthtracker.domain.model.TrendAnalysis
import com.healthtracker.domain.model.WeeklyAnalytics
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Use case for health analytics operations.
 * Handles aggregation, insight generation, and trend analysis.
 */
interface AnalyticsUseCase {
    
    /**
     * Gets analytics for the specified period.
     * 
     * @param period The analytics period (daily, weekly, or monthly)
     * @return Flow emitting the appropriate analytics type
     */
    fun getDailyAnalytics(date: LocalDate): Flow<DailyAnalytics>
    
    /**
     * Gets weekly analytics.
     */
    fun getWeeklyAnalytics(weekStart: LocalDate): Flow<WeeklyAnalytics>
    
    /**
     * Gets monthly analytics.
     */
    fun getMonthlyAnalytics(month: YearMonth): Flow<MonthlyAnalytics>
    
    /**
     * Generates insights from health metrics.
     * 
     * @param metrics The health metrics to analyze
     * @return List of generated insights
     */
    fun generateInsights(metrics: HealthMetrics): List<Insight>
    
    /**
     * Generates insights from a list of health metrics.
     * 
     * @param metricsList List of health metrics to analyze
     * @return List of generated insights
     */
    fun generateInsights(metricsList: List<HealthMetrics>): List<Insight>
    
    /**
     * Calculates metric averages from a list of health metrics.
     * 
     * @param metricsList List of health metrics
     * @return Calculated averages
     */
    fun calculateAverages(metricsList: List<HealthMetrics>): MetricAverages
    
    /**
     * Gets trend analysis for a specific metric.
     * 
     * @param metric The metric type to analyze
     * @param days Number of days to include
     * @return Flow emitting trend analysis
     */
    fun getTrendAnalysis(metric: MetricType, days: Int): Flow<TrendAnalysis>
}
