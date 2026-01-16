package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.healthtracker.data.local.entity.AuditLogEntity
import com.healthtracker.data.local.entity.PrivacySettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for privacy and audit operations.
 */
@Dao
interface PrivacyDao {
    
    // ==================== Privacy Settings ====================
    
    /**
     * Inserts or replaces privacy settings.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacySettings(settings: PrivacySettingsEntity)
    
    /**
     * Updates existing privacy settings.
     */
    @Update
    suspend fun updatePrivacySettings(settings: PrivacySettingsEntity)
    
    /**
     * Gets privacy settings for a user as a Flow.
     */
    @Query("SELECT * FROM privacy_settings WHERE userId = :userId")
    fun getPrivacySettingsFlow(userId: String): Flow<PrivacySettingsEntity?>
    
    /**
     * Gets privacy settings for a user synchronously.
     */
    @Query("SELECT * FROM privacy_settings WHERE userId = :userId")
    suspend fun getPrivacySettings(userId: String): PrivacySettingsEntity?
    
    /**
     * Gets privacy settings for the current user (assumes single user).
     */
    @Query("SELECT * FROM privacy_settings LIMIT 1")
    fun getCurrentPrivacySettingsFlow(): Flow<PrivacySettingsEntity?>
    
    /**
     * Gets privacy settings for the current user synchronously.
     */
    @Query("SELECT * FROM privacy_settings LIMIT 1")
    suspend fun getCurrentPrivacySettings(): PrivacySettingsEntity?
    
    /**
     * Deletes privacy settings for a user.
     */
    @Query("DELETE FROM privacy_settings WHERE userId = :userId")
    suspend fun deletePrivacySettings(userId: String)
    
    /**
     * Deletes all privacy settings.
     */
    @Query("DELETE FROM privacy_settings")
    suspend fun deleteAllPrivacySettings()
    
    // ==================== Audit Log ====================
    
    /**
     * Inserts an audit log entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entry: AuditLogEntity)
    
    /**
     * Gets audit log entries for a user, ordered by timestamp descending.
     */
    @Query("SELECT * FROM audit_log WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditLogFlow(userId: String, limit: Int): Flow<List<AuditLogEntity>>
    
    /**
     * Gets audit log entries for a user synchronously.
     */
    @Query("SELECT * FROM audit_log WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAuditLog(userId: String, limit: Int): List<AuditLogEntity>
    
    /**
     * Gets audit log entries for the current user (assumes single user).
     */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun getCurrentAuditLogFlow(limit: Int): Flow<List<AuditLogEntity>>
    
    /**
     * Gets audit log entries for the current user synchronously.
     */
    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getCurrentAuditLog(limit: Int): List<AuditLogEntity>
    
    /**
     * Gets audit log entries by operation type.
     */
    @Query("SELECT * FROM audit_log WHERE userId = :userId AND operationType = :operationType ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAuditLogByType(userId: String, operationType: String, limit: Int): List<AuditLogEntity>
    
    /**
     * Deletes audit log entries for a user.
     */
    @Query("DELETE FROM audit_log WHERE userId = :userId")
    suspend fun deleteAuditLog(userId: String)
    
    /**
     * Deletes all audit log entries.
     */
    @Query("DELETE FROM audit_log")
    suspend fun deleteAllAuditLogs()
    
    /**
     * Gets the count of audit log entries for a user.
     */
    @Query("SELECT COUNT(*) FROM audit_log WHERE userId = :userId")
    suspend fun getAuditLogCount(userId: String): Int
}
