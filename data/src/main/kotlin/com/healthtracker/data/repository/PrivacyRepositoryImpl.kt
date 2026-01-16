package com.healthtracker.data.repository

import android.content.Context
import android.os.Build
import com.healthtracker.data.local.dao.AnomalyDao
import com.healthtracker.data.local.dao.GamificationDao
import com.healthtracker.data.local.dao.HealthMetricsDao
import com.healthtracker.data.local.dao.MedicalDao
import com.healthtracker.data.local.dao.PrivacyDao
import com.healthtracker.data.local.dao.SocialDao
import com.healthtracker.data.local.dao.SuggestionDao
import com.healthtracker.data.local.dao.UserBaselineDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.local.entity.AuditLogEntity
import com.healthtracker.data.local.entity.PrivacySettingsEntity
import com.healthtracker.data.security.EncryptionService
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.AuditLogEntry
import com.healthtracker.domain.model.AuditOperationType
import com.healthtracker.domain.model.BadgeExport
import com.healthtracker.domain.model.CircleExport
import com.healthtracker.domain.model.DataCategory
import com.healthtracker.domain.model.ExportCategory
import com.healthtracker.domain.model.ExportedUserData
import com.healthtracker.domain.model.GamificationExport
import com.healthtracker.domain.model.HealthMetricsExport
import com.healthtracker.domain.model.MedicalRecordExport
import com.healthtracker.domain.model.PrivacySettings
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SocialExport
import com.healthtracker.domain.model.StreakExport
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.UserSettings
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.repository.PrivacyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PrivacyRepository.
 * 
 * Handles privacy settings, data export, data deletion, and audit logging.
 */
@Singleton
class PrivacyRepositoryImpl @Inject constructor(
    private val privacyDao: PrivacyDao,
    private val userDao: UserDao,
    private val healthMetricsDao: HealthMetricsDao,
    private val gamificationDao: GamificationDao,
    private val socialDao: SocialDao,
    private val medicalDao: MedicalDao,
    private val anomalyDao: AnomalyDao,
    private val suggestionDao: SuggestionDao,
    private val userBaselineDao: UserBaselineDao,
    private val encryptionService: EncryptionService,
    @ApplicationContext private val context: Context
) : PrivacyRepository {
    
    // ==================== Privacy Settings ====================
    
    override fun getPrivacySettings(): Flow<PrivacySettings?> {
        return privacyDao.getCurrentPrivacySettingsFlow().map { entity ->
            entity?.toDomain()
        }
    }
    
    override suspend fun getCurrentPrivacySettings(): Result<PrivacySettings> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser()
                if (user == null) {
                    return@withContext Result.Error(
                        AppException.AuthException("No user found")
                    )
                }
                
                val settings = privacyDao.getPrivacySettings(user.id)
                if (settings != null) {
                    Result.Success(settings.toDomain())
                } else {
                    // Create default settings if none exist
                    val defaultSettings = PrivacySettingsEntity(
                        userId = user.id,
                        updatedAt = System.currentTimeMillis()
                    )
                    privacyDao.insertPrivacySettings(defaultSettings)
                    Result.Success(defaultSettings.toDomain())
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get privacy settings")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to get privacy settings: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun updatePrivacySettings(settings: PrivacySettings): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val entity = settings.toEntity()
                privacyDao.insertPrivacySettings(entity)
                
                // Log the change
                logAuditEntry(
                    AuditOperationType.PRIVACY_SETTING_CHANGE,
                    "Privacy settings updated"
                )
                
                Timber.d("Privacy settings updated for user: ${settings.userId}")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update privacy settings")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to update privacy settings: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun toggleDataCategory(
        category: DataCategory, 
        enabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser()
            if (user == null) {
                return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )
            }
            
            val currentSettings = privacyDao.getPrivacySettings(user.id)
                ?: PrivacySettingsEntity(userId = user.id, updatedAt = System.currentTimeMillis())
            
            val updatedSettings = when (category) {
                DataCategory.HEALTH_METRICS -> currentSettings.copy(
                    healthMetricsEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.LOCATION -> currentSettings.copy(
                    locationEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.HEART_RATE -> currentSettings.copy(
                    heartRateEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.SLEEP_DATA -> currentSettings.copy(
                    sleepDataEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.MOOD_DATA -> currentSettings.copy(
                    moodDataEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.DIET_DATA -> currentSettings.copy(
                    dietDataEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.SOCIAL_SHARING -> currentSettings.copy(
                    socialSharingEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.ANALYTICS -> currentSettings.copy(
                    analyticsEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
                DataCategory.CRASH_REPORTING -> currentSettings.copy(
                    crashReportingEnabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )
            }
            
            privacyDao.insertPrivacySettings(updatedSettings)
            
            // Log the change
            logAuditEntry(
                AuditOperationType.PRIVACY_SETTING_CHANGE,
                "Data category ${category.name} set to $enabled"
            )
            
            Timber.d("Data category $category toggled to $enabled")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to toggle data category")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to toggle data category: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun isDataCategoryEnabled(category: DataCategory): Boolean = 
        withContext(Dispatchers.IO) {
            try {
                val settings = privacyDao.getCurrentPrivacySettings()
                    ?: return@withContext true // Default to enabled if no settings
                
                when (category) {
                    DataCategory.HEALTH_METRICS -> settings.healthMetricsEnabled
                    DataCategory.LOCATION -> settings.locationEnabled
                    DataCategory.HEART_RATE -> settings.heartRateEnabled
                    DataCategory.SLEEP_DATA -> settings.sleepDataEnabled
                    DataCategory.MOOD_DATA -> settings.moodDataEnabled
                    DataCategory.DIET_DATA -> settings.dietDataEnabled
                    DataCategory.SOCIAL_SHARING -> settings.socialSharingEnabled
                    DataCategory.ANALYTICS -> settings.analyticsEnabled
                    DataCategory.CRASH_REPORTING -> settings.crashReportingEnabled
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check data category status")
                true // Default to enabled on error
            }
        }
    
    // ==================== Data Export ====================
    
    override suspend fun exportAllUserData(): Result<ExportedUserData> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser()
                if (user == null) {
                    return@withContext Result.Error(
                        AppException.AuthException("No user found")
                    )
                }
                
                val exportedAt = Instant.now()
                
                // Export profile
                val profile = UserProfile(
                    name = user.name,
                    age = user.age,
                    weight = user.weightKg,
                    height = user.heightCm,
                    goal = HealthGoal.valueOf(user.goal),
                    dietPreference = user.dietPreference?.let { DietPreference.valueOf(it) }
                )
                
                // Export settings
                val settings = UserSettings(
                    notificationsEnabled = user.notificationsEnabled,
                    dataCollectionEnabled = user.dataCollectionEnabled,
                    sensitiveDataOptIn = user.sensitiveDataOptIn,
                    hydrationReminders = user.hydrationReminders,
                    mindfulnessReminders = user.mindfulnessReminders,
                    reminderIntervalMinutes = user.reminderIntervalMinutes
                )
                
                // Export privacy settings
                val privacyEntity = privacyDao.getPrivacySettings(user.id)
                val privacySettings = privacyEntity?.toDomain()
                
                // Export health metrics
                val metricsEntities = healthMetricsDao.getAllMetricsForUser(user.id)
                val healthMetrics = metricsEntities.map { entity ->
                    HealthMetricsExport(
                        date = LocalDate.ofEpochDay(entity.date).toString(),
                        steps = entity.steps,
                        distanceMeters = entity.distanceMeters,
                        caloriesBurned = entity.caloriesBurned,
                        screenTimeMinutes = entity.screenTimeMinutes,
                        sleepDurationMinutes = entity.sleepDurationMinutes,
                        sleepQuality = entity.sleepQuality,
                        mood = entity.mood
                    )
                }
                
                // Export gamification
                val progressEntity = gamificationDao.getUserProgress(user.id)
                val streakEntities = gamificationDao.getStreaks(user.id)
                val badgeEntities = gamificationDao.getUnlockedBadges(user.id)
                
                val gamification = if (progressEntity != null) {
                    GamificationExport(
                        totalPoints = progressEntity.totalPoints,
                        level = progressEntity.level,
                        streaks = streakEntities.map { streak ->
                            StreakExport(
                                type = streak.type,
                                currentCount = streak.currentCount,
                                longestCount = streak.longestCount,
                                lastUpdated = LocalDate.ofEpochDay(streak.lastUpdated).toString()
                            )
                        },
                        badges = badgeEntities.map { badge ->
                            BadgeExport(
                                id = badge.badgeId,
                                name = badge.badgeId, // Badge name would come from a badges table
                                unlockedAt = Instant.ofEpochMilli(badge.unlockedAt).toString()
                            )
                        }
                    )
                } else null
                
                // Export social data
                val circleMembers = socialDao.getCircleMembersForUser(user.id)
                val socialData = if (circleMembers.isNotEmpty()) {
                    SocialExport(
                        circles = circleMembers.map { member ->
                            CircleExport(
                                id = member.circleId,
                                name = member.circleId, // Circle name would come from circles table
                                role = member.role,
                                joinedAt = Instant.ofEpochMilli(member.joinedAt).toString()
                            )
                        }
                    )
                } else null
                
                // Export medical records (metadata only)
                val medicalEntities = medicalDao.getMedicalRecordsForUser(user.id)
                val medicalRecords = medicalEntities.map { record ->
                    MedicalRecordExport(
                        id = record.id,
                        type = record.type,
                        title = record.title,
                        uploadedAt = Instant.ofEpochMilli(record.uploadedAt).toString()
                    )
                }
                
                // Export audit log
                val auditEntities = privacyDao.getAuditLog(user.id, 1000)
                val auditLog = auditEntities.map { it.toDomain() }
                
                val exportedData = ExportedUserData(
                    exportedAt = exportedAt,
                    userId = user.id,
                    profile = profile,
                    settings = settings,
                    privacySettings = privacySettings,
                    healthMetrics = healthMetrics,
                    gamification = gamification,
                    socialData = socialData,
                    medicalRecords = medicalRecords,
                    auditLog = auditLog
                )
                
                // Log the export
                logAuditEntry(
                    AuditOperationType.DATA_EXPORT,
                    "User data exported"
                )
                
                Timber.d("User data exported successfully")
                Result.Success(exportedData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to export user data")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to export user data: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun exportToFile(filePath: String): Result<String> = 
        withContext(Dispatchers.IO) {
            try {
                val exportResult = exportAllUserData()
                when (exportResult) {
                    is Result.Success -> {
                        val json = exportedDataToJson(exportResult.data)
                        val file = java.io.File(filePath)
                        file.parentFile?.mkdirs()
                        file.writeText(json)
                        
                        Timber.d("User data exported to file: $filePath")
                        Result.Success(filePath)
                    }
                    is Result.Error -> exportResult
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export to file")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to export to file: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    // ==================== Data Deletion ====================
    
    override suspend fun deleteAllLocalData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser()
            val userId = user?.id
            
            // Delete all data from all tables
            if (userId != null) {
                healthMetricsDao.deleteAllMetricsForUser(userId)
                gamificationDao.deleteAllProgressForUser(userId)
                gamificationDao.deleteAllStreaksForUser(userId)
                gamificationDao.deleteAllBadgesForUser(userId)
                gamificationDao.deleteAllPointsHistoryForUser(userId)
                socialDao.deleteAllCircleMembersForUser(userId)
                medicalDao.deleteAllMedicalRecordsForUser(userId)
                medicalDao.deleteAllRemindersForUser(userId)
                anomalyDao.deleteAllAnomaliesForUser(userId)
                suggestionDao.deleteAllSuggestionsForUser(userId)
                userBaselineDao.deleteBaseline(userId)
                privacyDao.deletePrivacySettings(userId)
                privacyDao.deleteAuditLog(userId)
            }
            
            // Delete user last
            userDao.deleteAllUsers()
            
            Timber.d("All local data deleted")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete local data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to delete local data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun deleteAllRemoteData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement Firebase deletion
            // This would delete from:
            // - Firestore (user document and subcollections)
            // - Realtime Database (user data)
            // - Firebase Storage (medical records, images)
            
            Timber.d("Remote data deletion requested (Firebase integration pending)")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete remote data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to delete remote data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun deleteAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Log before deletion (this will be deleted too)
            val user = userDao.getUser()
            if (user != null) {
                logAuditEntry(
                    AuditOperationType.DATA_DELETION,
                    "User requested complete data deletion"
                )
            }
            
            // Delete remote data first
            val remoteResult = deleteAllRemoteData()
            if (remoteResult is Result.Error) {
                Timber.w("Remote deletion failed, continuing with local deletion")
            }
            
            // Delete local data
            val localResult = deleteAllLocalData()
            if (localResult is Result.Error) {
                return@withContext localResult
            }
            
            Timber.d("All user data deleted successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete all data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to delete all data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    // ==================== Audit Trail ====================
    
    override suspend fun logAuditEntry(
        operationType: AuditOperationType,
        details: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser()
            val userId = user?.id ?: "unknown"
            
            val entry = AuditLogEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                operationType = operationType.name,
                details = details,
                timestamp = System.currentTimeMillis(),
                deviceInfo = getDeviceInfo()
            )
            
            privacyDao.insertAuditLog(entry)
            
            Timber.d("Audit log entry created: ${operationType.name}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to log audit entry")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to log audit entry: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override fun getAuditLog(limit: Int): Flow<List<AuditLogEntry>> {
        return privacyDao.getCurrentAuditLogFlow(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getAuditLogEntries(limit: Int): Result<List<AuditLogEntry>> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser()
                if (user == null) {
                    return@withContext Result.Error(
                        AppException.AuthException("No user found")
                    )
                }
                
                val entries = privacyDao.getAuditLog(user.id, limit)
                Result.Success(entries.map { it.toDomain() })
            } catch (e: Exception) {
                Timber.e(e, "Failed to get audit log")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to get audit log: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    // ==================== Helper Functions ====================
    
    private fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
    
    private fun exportedDataToJson(data: ExportedUserData): String {
        // Simple JSON serialization (in production, use Gson or Moshi)
        return buildString {
            appendLine("{")
            appendLine("  \"exportVersion\": \"${data.exportVersion}\",")
            appendLine("  \"exportedAt\": \"${data.exportedAt}\",")
            appendLine("  \"userId\": \"${data.userId}\",")
            
            // Profile
            data.profile?.let { profile ->
                appendLine("  \"profile\": {")
                appendLine("    \"name\": \"${profile.name}\",")
                appendLine("    \"age\": ${profile.age},")
                appendLine("    \"weight\": ${profile.weight},")
                appendLine("    \"height\": ${profile.height},")
                appendLine("    \"goal\": \"${profile.goal}\"")
                appendLine("  },")
            }
            
            // Health metrics count
            appendLine("  \"healthMetricsCount\": ${data.healthMetrics.size},")
            appendLine("  \"medicalRecordsCount\": ${data.medicalRecords.size},")
            appendLine("  \"auditLogCount\": ${data.auditLog.size}")
            
            appendLine("}")
        }
    }
    
    // ==================== Entity Mappers ====================
    
    private fun PrivacySettingsEntity.toDomain(): PrivacySettings {
        return PrivacySettings(
            userId = userId,
            healthMetricsEnabled = healthMetricsEnabled,
            locationEnabled = locationEnabled,
            heartRateEnabled = heartRateEnabled,
            sleepDataEnabled = sleepDataEnabled,
            moodDataEnabled = moodDataEnabled,
            dietDataEnabled = dietDataEnabled,
            socialSharingEnabled = socialSharingEnabled,
            analyticsEnabled = analyticsEnabled,
            crashReportingEnabled = crashReportingEnabled,
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }
    
    private fun PrivacySettings.toEntity(): PrivacySettingsEntity {
        return PrivacySettingsEntity(
            userId = userId,
            healthMetricsEnabled = healthMetricsEnabled,
            locationEnabled = locationEnabled,
            heartRateEnabled = heartRateEnabled,
            sleepDataEnabled = sleepDataEnabled,
            moodDataEnabled = moodDataEnabled,
            dietDataEnabled = dietDataEnabled,
            socialSharingEnabled = socialSharingEnabled,
            analyticsEnabled = analyticsEnabled,
            crashReportingEnabled = crashReportingEnabled,
            updatedAt = updatedAt.toEpochMilli()
        )
    }
    
    private fun AuditLogEntity.toDomain(): AuditLogEntry {
        return AuditLogEntry(
            id = id,
            userId = userId,
            operationType = AuditOperationType.valueOf(operationType),
            details = details,
            timestamp = Instant.ofEpochMilli(timestamp),
            ipAddress = ipAddress,
            deviceInfo = deviceInfo
        )
    }
}
