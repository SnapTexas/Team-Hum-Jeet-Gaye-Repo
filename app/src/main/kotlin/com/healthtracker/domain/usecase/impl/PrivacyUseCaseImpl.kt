package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.AuditLogEntry
import com.healthtracker.domain.model.AuditOperationType
import com.healthtracker.domain.model.DataCategory
import com.healthtracker.domain.model.ExportedUserData
import com.healthtracker.domain.model.PrivacySettings
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.repository.PrivacyRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.PrivacyUseCase
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PrivacyUseCase.
 * 
 * Handles privacy settings, data export, data deletion, and audit logging.
 * Implements GDPR-style data rights (access, portability, erasure).
 */
@Singleton
class PrivacyUseCaseImpl @Inject constructor(
    private val privacyRepository: PrivacyRepository,
    private val userRepository: UserRepository
) : PrivacyUseCase {
    
    // ==================== Privacy Settings ====================
    
    override fun getPrivacySettings(): Flow<PrivacySettings?> {
        return privacyRepository.getPrivacySettings()
    }
    
    override suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit> {
        Timber.d("Updating privacy settings for user: ${settings.userId}")
        
        // Log the change before updating
        privacyRepository.logAuditEntry(
            AuditOperationType.PRIVACY_SETTING_CHANGE,
            "Privacy settings update requested"
        )
        
        return privacyRepository.updatePrivacySettings(settings)
    }
    
    override suspend fun toggleDataCategory(
        category: DataCategory, 
        enabled: Boolean
    ): Result<Unit> {
        Timber.d("Toggling data category $category to $enabled")
        
        // Log the change
        privacyRepository.logAuditEntry(
            AuditOperationType.PRIVACY_SETTING_CHANGE,
            "Data category ${category.name} toggled to $enabled"
        )
        
        return privacyRepository.toggleDataCategory(category, enabled)
    }
    
    override suspend fun isDataCollectionEnabled(category: DataCategory): Boolean {
        return privacyRepository.isDataCategoryEnabled(category)
    }
    
    // ==================== Data Export ====================
    
    override suspend fun exportAllData(): Result<ExportedUserData> {
        Timber.d("Exporting all user data")
        
        // Log the export request
        privacyRepository.logAuditEntry(
            AuditOperationType.DATA_EXPORT,
            "User data export requested"
        )
        
        return privacyRepository.exportAllUserData()
    }
    
    override suspend fun exportToFile(filePath: String): Result<String> {
        Timber.d("Exporting user data to file: $filePath")
        
        // Log the export request
        privacyRepository.logAuditEntry(
            AuditOperationType.DATA_EXPORT,
            "User data export to file requested: $filePath"
        )
        
        return privacyRepository.exportToFile(filePath)
    }
    
    // ==================== Data Deletion ====================
    
    override suspend fun deleteAllData(): Result<Unit> {
        Timber.d("Deleting all user data (right to be forgotten)")
        
        // Log the deletion request before deleting
        privacyRepository.logAuditEntry(
            AuditOperationType.DATA_DELETION,
            "Complete data deletion requested (right to be forgotten)"
        )
        
        // Delete all data
        val result = privacyRepository.deleteAllData()
        
        if (result is Result.Success) {
            Timber.d("All user data deleted successfully")
        } else {
            Timber.e("Failed to delete all user data")
        }
        
        return result
    }
    
    override suspend fun verifyDataDeletion(): Boolean {
        // Check if any user data remains
        return when (val userResult = userRepository.getCurrentUser()) {
            is Result.Success -> {
                // User still exists, deletion not complete
                Timber.w("User data still exists after deletion request")
                false
            }
            is Result.Error -> {
                // No user found, deletion successful
                Timber.d("Data deletion verified - no user data found")
                true
            }
        }
    }
    
    // ==================== Audit Trail ====================
    
    override fun getAuditLog(limit: Int): Flow<List<AuditLogEntry>> {
        return privacyRepository.getAuditLog(limit)
    }
    
    override suspend fun logSensitiveOperation(
        operationType: AuditOperationType,
        details: String
    ): Result<Unit> {
        Timber.d("Logging sensitive operation: ${operationType.name}")
        return privacyRepository.logAuditEntry(operationType, details)
    }
}
