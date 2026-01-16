package com.healthtracker.domain.repository

import com.healthtracker.domain.model.AuditLogEntry
import com.healthtracker.domain.model.AuditOperationType
import com.healthtracker.domain.model.DataCategory
import com.healthtracker.domain.model.ExportedUserData
import com.healthtracker.domain.model.PrivacySettings
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for privacy and data management operations.
 * 
 * Handles:
 * - Privacy toggle management
 * - Data export functionality
 * - Data deletion (GDPR-style right to be forgotten)
 * - Audit trail logging
 */
interface PrivacyRepository {
    
    // ==================== Privacy Settings ====================
    
    /**
     * Gets the current privacy settings as a Flow.
     * 
     * @return Flow emitting current privacy settings
     */
    fun getPrivacySettings(): Flow<PrivacySettings?>
    
    /**
     * Gets the current privacy settings synchronously.
     * 
     * @return Result containing privacy settings or error
     */
    suspend fun getCurrentPrivacySettings(): Result<PrivacySettings>
    
    /**
     * Updates privacy settings.
     * 
     * @param settings The new privacy settings
     * @return Result indicating success or failure
     */
    suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit>
    
    /**
     * Toggles a specific data category on/off.
     * 
     * @param category The data category to toggle
     * @param enabled Whether the category should be enabled
     * @return Result indicating success or failure
     */
    suspend fun toggleDataCategory(category: DataCategory, enabled: Boolean): Result<Unit>
    
    /**
     * Checks if a specific data category is enabled.
     * 
     * @param category The data category to check
     * @return true if enabled, false otherwise
     */
    suspend fun isDataCategoryEnabled(category: DataCategory): Boolean
    
    // ==================== Data Export ====================
    
    /**
     * Exports all user data in a portable JSON format.
     * 
     * @return Result containing the exported data or error
     */
    suspend fun exportAllUserData(): Result<ExportedUserData>
    
    /**
     * Exports user data to a file.
     * 
     * @param filePath The path where the export file should be saved
     * @return Result containing the file path or error
     */
    suspend fun exportToFile(filePath: String): Result<String>
    
    // ==================== Data Deletion ====================
    
    /**
     * Deletes all user data from local storage.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllLocalData(): Result<Unit>
    
    /**
     * Deletes all user data from remote storage (Firebase).
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllRemoteData(): Result<Unit>
    
    /**
     * Deletes all user data from both local and remote storage.
     * This is the "right to be forgotten" implementation.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllData(): Result<Unit>
    
    // ==================== Audit Trail ====================
    
    /**
     * Logs a sensitive operation for audit purposes.
     * 
     * @param operationType The type of operation
     * @param details Additional details about the operation
     * @return Result indicating success or failure
     */
    suspend fun logAuditEntry(
        operationType: AuditOperationType,
        details: String
    ): Result<Unit>
    
    /**
     * Gets audit log entries for the current user.
     * 
     * @param limit Maximum number of entries to return
     * @return Flow emitting audit log entries
     */
    fun getAuditLog(limit: Int = 100): Flow<List<AuditLogEntry>>
    
    /**
     * Gets audit log entries synchronously.
     * 
     * @param limit Maximum number of entries to return
     * @return Result containing audit log entries or error
     */
    suspend fun getAuditLogEntries(limit: Int = 100): Result<List<AuditLogEntry>>
}
