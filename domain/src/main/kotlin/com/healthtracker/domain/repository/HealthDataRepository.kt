package com.healthtracker.domain.repository

import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for health data operations.
 * 
 * Implements local-first caching strategy with Firebase sync.
 * All data is encrypted before storage.
 */
interface HealthDataRepository {
    
    /**
     * Gets health metrics for a specific date.
     * 
     * @param date The date to get metrics for
     * @return Flow emitting health metrics for the date
     */
    fun getHealthMetrics(date: LocalDate): Flow<HealthMetrics?>
    
    /**
     * Gets health metrics for a specific date synchronously.
     * 
     * @param date The date to get metrics for
     * @return Health metrics for the date or null
     */
    suspend fun getMetricsForDateSync(date: LocalDate): HealthMetrics?
    
    /**
     * Gets health metrics for a date range.
     * 
     * @param start Start date (inclusive)
     * @param end End date (inclusive)
     * @return Flow emitting list of health metrics
     */
    fun getHealthMetricsRange(start: LocalDate, end: LocalDate): Flow<List<HealthMetrics>>
    
    /**
     * Gets health metrics for a date range synchronously.
     * 
     * @param start Start date (inclusive)
     * @param end End date (inclusive)
     * @return List of health metrics
     */
    suspend fun getMetricsRangeSync(start: LocalDate, end: LocalDate): List<HealthMetrics>
    
    /**
     * Syncs local health data with Firebase.
     * 
     * @return Result indicating success or failure
     */
    suspend fun syncHealthData(): Result<Unit>
    
    /**
     * Inserts or updates health metrics.
     * 
     * @param metrics The metrics to insert/update
     * @return Result indicating success or failure
     */
    suspend fun insertMetrics(metrics: HealthMetrics): Result<Unit>
    
    /**
     * Gets the latest health metrics.
     * 
     * @return Result containing the latest metrics or an error
     */
    suspend fun getLatestMetrics(): Result<HealthMetrics?>
    
    /**
     * Deletes all health data for the current user.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllData(): Result<Unit>
}
