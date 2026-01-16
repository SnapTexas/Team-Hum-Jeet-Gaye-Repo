package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalyDetectionResult
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.UserBaseline
import kotlinx.coroutines.flow.Flow

/**
 * Use case for anomaly detection operations.
 * 
 * Handles detection of health anomalies using baseline comparison
 * and optional ML-based pattern recognition with defensive wrapper.
 */
interface AnomalyDetectionUseCase {
    
    /**
     * Analyzes health metrics for anomalies.
     * 
     * Uses 2 standard deviation threshold from user's baseline.
     * Falls back to rule-based detection if ML fails.
     * 
     * @param metrics The health metrics to analyze
     * @return AnomalyDetectionResult containing detected anomalies
     */
    suspend fun analyzeForAnomalies(metrics: HealthMetrics): AnomalyDetectionResult
    
    /**
     * Gets the user's baseline metrics.
     * 
     * @return Flow emitting the user baseline or null
     */
    fun getUserBaseline(): Flow<UserBaseline?>
    
    /**
     * Updates the user's baseline from historical metrics.
     * 
     * Requires at least 7 days of data.
     * 
     * @param metrics List of health metrics to calculate baseline from
     * @return Result indicating success or failure
     */
    suspend fun updateBaseline(metrics: List<HealthMetrics>): Result<UserBaseline?>
    
    /**
     * Gets unacknowledged anomalies.
     * 
     * @return Flow emitting list of unacknowledged anomalies
     */
    fun getUnacknowledgedAnomalies(): Flow<List<Anomaly>>
    
    /**
     * Acknowledges an anomaly.
     * 
     * @param anomalyId ID of the anomaly to acknowledge
     * @return Result indicating success or failure
     */
    suspend fun acknowledgeAnomaly(anomalyId: String): Result<Unit>
    
    /**
     * Checks if the user has a valid baseline.
     * 
     * @return True if baseline exists and has at least 7 days of data
     */
    suspend fun hasValidBaseline(): Boolean
}
