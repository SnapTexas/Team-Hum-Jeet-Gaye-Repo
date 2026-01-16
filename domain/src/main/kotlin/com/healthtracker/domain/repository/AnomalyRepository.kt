package com.healthtracker.domain.repository

import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.UserBaseline
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for anomaly detection and management.
 */
interface AnomalyRepository {
    
    /**
     * Gets anomalies detected for a specific date.
     * 
     * @param date The date to get anomalies for
     * @return Flow emitting list of anomalies
     */
    fun getAnomalies(date: LocalDate): Flow<List<Anomaly>>
    
    /**
     * Gets all unacknowledged anomalies.
     * 
     * @return Flow emitting list of unacknowledged anomalies
     */
    fun getUnacknowledgedAnomalies(): Flow<List<Anomaly>>
    
    /**
     * Gets recent anomalies.
     * 
     * @param limit Maximum number of anomalies to return
     * @return List of recent anomalies
     */
    suspend fun getRecentAnomalies(limit: Int = 10): List<Anomaly>
    
    /**
     * Detects anomalies in the given health metrics.
     * 
     * @param metrics The health metrics to analyze
     * @return List of detected anomalies
     */
    suspend fun detectAnomalies(metrics: HealthMetrics): List<Anomaly>
    
    /**
     * Saves detected anomalies to the database.
     * 
     * @param anomalies List of anomalies to save
     */
    suspend fun saveAnomalies(anomalies: List<Anomaly>)
    
    /**
     * Acknowledges an anomaly.
     * 
     * @param anomalyId ID of the anomaly to acknowledge
     * @return Result indicating success or failure
     */
    suspend fun acknowledgeAnomaly(anomalyId: String): Result<Unit>
    
    /**
     * Gets the user's baseline metrics.
     * 
     * @return Flow emitting the user baseline or null
     */
    fun getUserBaseline(): Flow<UserBaseline?>
    
    /**
     * Gets the user's baseline synchronously.
     * 
     * @return The user baseline or null
     */
    suspend fun getUserBaselineSync(): UserBaseline?
    
    /**
     * Calculates and saves a new baseline from historical metrics.
     * 
     * @param metrics List of health metrics to calculate baseline from
     * @return The calculated baseline or null if insufficient data
     */
    suspend fun calculateAndSaveBaseline(metrics: List<HealthMetrics>): UserBaseline?
    
    /**
     * Deletes all anomalies for the current user.
     */
    suspend fun deleteAllAnomalies()
}
