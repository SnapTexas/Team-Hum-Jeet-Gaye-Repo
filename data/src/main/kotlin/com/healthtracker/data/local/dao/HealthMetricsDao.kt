package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.healthtracker.data.local.entity.HealthMetricsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for health metrics operations.
 */
@Dao
interface HealthMetricsDao {
    
    /**
     * Gets health metrics for a specific date.
     * 
     * @param userId User ID
     * @param date Date as epoch day
     * @return Flow emitting the metrics or null
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId AND date = :date")
    fun getMetricsByDate(userId: String, date: Long): Flow<HealthMetricsEntity?>
    
    /**
     * Gets health metrics for a date range.
     * 
     * @param userId User ID
     * @param startDate Start date as epoch day (inclusive)
     * @param endDate End date as epoch day (inclusive)
     * @return Flow emitting list of metrics
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getMetricsInRange(userId: String, startDate: Long, endDate: Long): Flow<List<HealthMetricsEntity>>
    
    /**
     * Gets health metrics for a specific date synchronously.
     * 
     * @param userId User ID
     * @param date Date as epoch day
     * @return The metrics or null
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId AND date = :date")
    suspend fun getMetricsByDateSync(userId: String, date: Long): HealthMetricsEntity?
    
    /**
     * Gets health metrics for a date range synchronously.
     * 
     * @param userId User ID
     * @param startDate Start date as epoch day (inclusive)
     * @param endDate End date as epoch day (inclusive)
     * @return List of metrics
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getMetricsInRangeSync(userId: String, startDate: Long, endDate: Long): List<HealthMetricsEntity>
    
    /**
     * Gets the latest health metrics for a user.
     * 
     * @param userId User ID
     * @return The most recent metrics or null
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLatestMetrics(userId: String): HealthMetricsEntity?
    
    /**
     * Gets all metrics that need to be synced.
     * 
     * @param userId User ID
     * @return List of metrics needing sync
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId AND needsSync = 1")
    suspend fun getMetricsNeedingSync(userId: String): List<HealthMetricsEntity>
    
    /**
     * Inserts or replaces health metrics.
     * 
     * @param metrics The metrics to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: HealthMetricsEntity)
    
    /**
     * Inserts multiple metrics records.
     * 
     * @param metrics List of metrics to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMetrics(metrics: List<HealthMetricsEntity>)
    
    /**
     * Updates existing metrics.
     * 
     * @param metrics The metrics to update
     */
    @Update
    suspend fun updateMetrics(metrics: HealthMetricsEntity)
    
    /**
     * Marks metrics as synced.
     * 
     * @param id Metrics ID
     * @param syncedAt Sync timestamp
     */
    @Query("UPDATE health_metrics SET needsSync = 0, syncedAt = :syncedAt WHERE id = :id")
    suspend fun markAsSynced(id: String, syncedAt: Long)
    
    /**
     * Deletes all metrics for a user.
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM health_metrics WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    /**
     * Gets count of metrics for a user.
     * 
     * @param userId User ID
     * @return Number of metrics records
     */
    @Query("SELECT COUNT(*) FROM health_metrics WHERE userId = :userId")
    suspend fun getMetricsCount(userId: String): Int
    
    /**
     * Gets all metrics for a user (for export).
     * 
     * @param userId User ID
     * @return List of all metrics
     */
    @Query("SELECT * FROM health_metrics WHERE userId = :userId ORDER BY date ASC")
    suspend fun getAllMetricsForUser(userId: String): List<HealthMetricsEntity>
    
    /**
     * Deletes all metrics for a user.
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM health_metrics WHERE userId = :userId")
    suspend fun deleteAllMetricsForUser(userId: String)
}
