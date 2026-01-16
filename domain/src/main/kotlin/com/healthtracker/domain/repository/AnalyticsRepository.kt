package com.healthtracker.domain.repository

import com.healthtracker.domain.model.DailyAnalytics
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.MonthlyAnalytics
import com.healthtracker.domain.model.TrendAnalysis
import com.healthtracker.domain.model.WeeklyAnalytics
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

/**
 * Repository interface for health analytics data.
 */
interface AnalyticsRepository {
    
    /**
     * Gets daily analytics for a specific date.
     * 
     * @param date The date to get analytics for
     * @return Flow emitting daily analytics
     */
    fun getDailyAnalytics(date: LocalDate): Flow<DailyAnalytics>
    
    /**
     * Gets weekly analytics starting from a specific date.
     * 
     * @param weekStart The start date of the week (typically Monday)
     * @return Flow emitting weekly analytics
     */
    fun getWeeklyAnalytics(weekStart: LocalDate): Flow<WeeklyAnalytics>
    
    /**
     * Gets monthly analytics for a specific month.
     * 
     * @param month The year-month to get analytics for
     * @return Flow emitting monthly analytics
     */
    fun getMonthlyAnalytics(month: YearMonth): Flow<MonthlyAnalytics>
    
    /**
     * Gets trend analysis for a specific metric over a number of days.
     * 
     * @param metric The type of metric to analyze
     * @param days Number of days to include in trend analysis
     * @return Flow emitting trend analysis
     */
    fun getTrendAnalysis(metric: MetricType, days: Int): Flow<TrendAnalysis>
}
