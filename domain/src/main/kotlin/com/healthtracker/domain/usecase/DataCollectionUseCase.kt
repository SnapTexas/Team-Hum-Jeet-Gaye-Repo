package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Permission state for data collection.
 * 
 * CRITICAL: Every data read must be gated behind permission check.
 * Rule: No permission → no repository call → no crash.
 */
enum class DataCollectionPermissionState {
    /** All required permissions granted */
    GRANTED,
    /** Permission explicitly denied by user */
    DENIED,
    /** Some permissions granted, some denied */
    PARTIALLY_GRANTED,
    /** User selected "Don't ask again" */
    PERMANENTLY_DENIED,
    /** Permission not yet requested */
    NOT_REQUESTED,
    /** Health Connect not available on device */
    NOT_AVAILABLE
}

/**
 * Result of data collection operation.
 */
sealed class DataCollectionResult {
    data class Success(val metrics: HealthMetrics) : DataCollectionResult()
    data class PartialSuccess(val metrics: HealthMetrics, val missingPermissions: List<String>) : DataCollectionResult()
    data class PermissionRequired(val permissions: List<String>) : DataCollectionResult()
    data class Error(val message: String) : DataCollectionResult()
}

/**
 * Use case for collecting health data.
 * 
 * Handles:
 * - Permission state management
 * - Health Connect data collection
 * - Graceful degradation when permissions missing
 */
interface DataCollectionUseCase {
    
    /**
     * Gets current permission state for data collection.
     */
    suspend fun getPermissionState(): DataCollectionPermissionState
    
    /**
     * Collects health data for a specific date.
     * 
     * CRITICAL: Checks permissions before any data access.
     * Returns appropriate result based on permission state.
     */
    suspend fun collectHealthData(date: LocalDate): DataCollectionResult
    
    /**
     * Gets health metrics for a date as a Flow.
     * Returns null if permissions not granted.
     */
    fun getHealthMetrics(date: LocalDate): Flow<HealthMetrics?>
    
    /**
     * Gets health metrics for a date range.
     */
    fun getHealthMetricsRange(start: LocalDate, end: LocalDate): Flow<List<HealthMetrics>>
    
    /**
     * Triggers manual sync of health data.
     */
    suspend fun syncHealthData(): Result<Unit>
    
    /**
     * Gets list of missing permissions.
     */
    suspend fun getMissingPermissions(): List<String>
    
    /**
     * Checks if Health Connect is available on device.
     */
    fun isHealthConnectAvailable(): Boolean
    
    /**
     * Schedules background health data collection.
     */
    fun scheduleBackgroundCollection()
    
    /**
     * Cancels background health data collection.
     */
    fun cancelBackgroundCollection()
}
