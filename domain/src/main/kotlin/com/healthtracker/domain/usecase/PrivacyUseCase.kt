package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.AuditLogEntry
import com.healthtracker.domain.model.AuditOperationType
import com.healthtracker.domain.model.DataCategory
import com.healthtracker.domain.model.ExportedUserData
import com.healthtracker.domain.model.PrivacySettings
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for privacy and data management operations.
 * 
 * Implements:
 * - Privacy toggle management (Requirement 14.3, 14.4)
 * - Data export functionality (Requirement 14.5)
 * - Data deletion functionality (Requirement 14.6)
 * - Audit trail logging (Requirement 14.8)
 */
interface PrivacyUseCase {
    
    // ==================== Privacy Settings ====================
    
    /**
     * Gets the current privacy settings as a Flow.
     * 
     * @return Flow emitting current privacy settings
     */
    fun getPrivacySettings(): Flow<PrivacySettings?>
    
    /**
     * Updates privacy settings and logs the change.
     * 
     * @param settings The new privacy settings
     * @return Result indicating success or failure
     */
    suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit>
    
    /**
     * Toggles a specific data category on/off.
     * When disabled, data collection for that category stops immediately.
     * 
     * @param category The data category to toggle
     * @param enabled Whether the category should be enabled
     * @return Result indicating success or failure
     */
    suspend fun toggleDataCategory(category: DataCategory, enabled: Boolean): Result<Unit>
    
    /**
     * Checks if a specific data category is enabled for collection.
     * 
     * @param category The data category to check
     * @return true if enabled, false otherwise
     */
    suspend fun isDataCollectionEnabled(category: DataCategory): Boolean
    
    // ==================== Data Export ====================
    
    /**
     * Exports all user data in a portable JSON format.
     * Includes: profile, metrics, records, gamification state.
     * 
     * @return Result containing the exported data or error
     */
    suspend fun exportAllData(): Result<ExportedUserData>
    
    /**
     * Exports user data to a file at the specified path.
     * 
     * @param filePath The path where the export file should be saved
     * @return Result containing the file path or error
     */
    suspend fun exportToFile(filePath: String): Result<String>
    
    // ==================== Data Deletion ====================
    
    /**
     * Deletes all user data from all stores (local and remote).
     * This implements the "right to be forgotten" (GDPR Article 17).
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteAllData(): Result<Unit>
    
    /**
     * Verifies that all user data has been deleted.
     * 
     * @return true if no data remains, false otherwise
     */
    suspend fun verifyDataDeletion(): Boolean
    
    // ==================== Audit Trail ====================
    
    /**
     * Gets audit log entries for the current user.
     * 
     * @param limit Maximum number of entries to return
     * @return Flow emitting audit log entries
     */
    fun getAuditLog(limit: Int = 100): Flow<List<AuditLogEntry>>
    
    /**
     * Logs a sensitive operation for audit purposes.
     * 
     * @param operationType The type of operation
     * @param details Additional details about the operation
     * @return Result indicating success or failure
     */
    suspend fun logSensitiveOperation(
        operationType: AuditOperationType,
        details: String
    ): Result<Unit>
}
