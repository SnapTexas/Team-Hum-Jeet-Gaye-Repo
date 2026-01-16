package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtracker.data.local.entity.AnomalyEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for anomaly operations.
 */
@Dao
interface AnomalyDao {
    
    /**
     * Gets anomalies for a specific date.
     * 
     * @param userId User ID
     * @param startOfDay Start of day timestamp (epoch millis)
     * @param endOfDay End of day timestamp (epoch millis)
     * @return Flow emitting list of anomalies
     */
    @Query("SELECT * FROM anomalies WHERE userId = :userId AND detectedAt BETWEEN :startOfDay AND :endOfDay ORDER BY detectedAt DESC")
    fun getAnomaliesByDate(userId: String, startOfDay: Long, endOfDay: Long): Flow<List<AnomalyEntity>>
    
    /**
     * Gets unacknowledged anomalies.
     * 
     * @param userId User ID
     * @return Flow emitting list of unacknowledged anomalies
     */
    @Query("SELECT * FROM anomalies WHERE userId = :userId AND acknowledged = 0 ORDER BY detectedAt DESC")
    fun getUnacknowledgedAnomalies(userId: String): Flow<List<AnomalyEntity>>
    
    /**
     * Gets recent anomalies.
     * 
     * @param userId User ID
     * @param limit Maximum number of anomalies to return
     * @return List of recent anomalies
     */
    @Query("SELECT * FROM anomalies WHERE userId = :userId ORDER BY detectedAt DESC LIMIT :limit")
    suspend fun getRecentAnomalies(userId: String, limit: Int): List<AnomalyEntity>
    
    /**
     * Inserts an anomaly.
     * 
     * @param anomaly The anomaly to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: AnomalyEntity)
    
    /**
     * Inserts multiple anomalies.
     * 
     * @param anomalies List of anomalies to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAnomalies(anomalies: List<AnomalyEntity>)
    
    /**
     * Acknowledges an anomaly.
     * 
     * @param id Anomaly ID
     */
    @Query("UPDATE anomalies SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledgeAnomaly(id: String)
    
    /**
     * Deletes all anomalies for a user.
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM anomalies WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    /**
     * Deletes all anomalies for a user (alias for data deletion).
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM anomalies WHERE userId = :userId")
    suspend fun deleteAllAnomaliesForUser(userId: String)
}
