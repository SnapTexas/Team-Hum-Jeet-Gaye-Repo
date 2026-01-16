package com.healthtracker.data.repository

import android.content.Context
import com.healthtracker.data.local.dao.MedicalDao
import com.healthtracker.data.local.entity.HealthReminderEntity
import com.healthtracker.data.local.entity.MedicalRecordEntity
import com.healthtracker.data.local.entity.ScheduledReminderEntity
import com.healthtracker.data.security.EncryptionService
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.MedicalRecordsSummary
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.MedicalScheduledReminder
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.ReminderSchedule
import com.healthtracker.domain.model.RemindersSummary
import com.healthtracker.domain.model.RepeatType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.repository.MedicalRepository
import com.healthtracker.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MedicalRepository.
 * 
 * Handles secure storage of medical records with encryption
 * and management of health reminders.
 */
@Singleton
class MedicalRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicalDao: MedicalDao,
    private val encryptionService: EncryptionService,
    private val userRepository: UserRepository,
    private val json: Json
) : MedicalRepository {
    
    private val medicalFilesDir: File by lazy {
        File(context.filesDir, "medical_records").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private suspend fun getCurrentUserId(): String {
        return userRepository.getUser().first()?.id 
            ?: throw IllegalStateException("No user logged in")
    }
    
    // ==================== Medical Records ====================
    
    override fun getMedicalRecords(): Flow<List<MedicalRecord>> {
        return medicalDao.getMedicalRecords(getCurrentUserIdSync())
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override fun getMedicalRecordsByType(type: RecordType): Flow<List<MedicalRecord>> {
        return medicalDao.getMedicalRecordsByType(getCurrentUserIdSync(), type.name)
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override suspend fun getMedicalRecord(recordId: String): Result<MedicalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = medicalDao.getMedicalRecord(recordId)
                if (entity != null) {
                    Result.Success(entity.toDomainModel())
                } else {
                    Result.Error(AppException.UnknownException("Record not found"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get medical record")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun uploadRecord(
        title: String,
        description: String?,
        type: RecordType,
        fileInputStream: InputStream,
        fileName: String,
        mimeType: String
    ): Result<MedicalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId()
                val recordId = UUID.randomUUID().toString()
                
                // Read file content
                val fileContent = fileInputStream.readBytes()
                val fileSizeBytes = fileContent.size.toLong()
                
                // Encrypt the content
                val encryptedContent = encryptionService.encrypt(fileContent)
                
                // Save encrypted file
                val encryptedFile = File(medicalFilesDir, "$recordId.enc")
                encryptedFile.writeBytes(encryptedContent)
                
                val now = Instant.now()
                
                val entity = MedicalRecordEntity(
                    id = recordId,
                    userId = userId,
                    type = type.name,
                    title = title,
                    description = description,
                    fileUri = encryptedFile.absolutePath,
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    mimeType = mimeType,
                    uploadedAt = now.toEpochMilli(),
                    isEncrypted = true,
                    encryptedContentPath = encryptedFile.absolutePath,
                    needsSync = true
                )
                
                medicalDao.insertMedicalRecord(entity)
                
                Result.Success(entity.toDomainModel())
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload medical record")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun updateRecord(
        recordId: String,
        title: String,
        description: String?,
        type: RecordType
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val existing = medicalDao.getMedicalRecord(recordId)
                    ?: return@withContext Result.Error(AppException.UnknownException("Record not found"))
                
                val updated = existing.copy(
                    title = title,
                    description = description,
                    type = type.name,
                    needsSync = true
                )
                
                medicalDao.updateMedicalRecord(updated)
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update medical record")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun deleteRecord(recordId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val record = medicalDao.getMedicalRecord(recordId)
                if (record != null) {
                    // Delete encrypted file
                    record.encryptedContentPath?.let { path ->
                        File(path).delete()
                    }
                    medicalDao.deleteMedicalRecord(recordId)
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete medical record")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun getRecordContent(recordId: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                val record = medicalDao.getMedicalRecord(recordId)
                    ?: return@withContext Result.Error(AppException.UnknownException("Record not found"))
                
                val encryptedPath = record.encryptedContentPath
                    ?: return@withContext Result.Error(AppException.UnknownException("No encrypted content"))
                
                val encryptedFile = File(encryptedPath)
                if (!encryptedFile.exists()) {
                    return@withContext Result.Error(AppException.UnknownException("Encrypted file not found"))
                }
                
                val encryptedContent = encryptedFile.readBytes()
                val decryptedContent = encryptionService.decrypt(encryptedContent)
                
                Result.Success(decryptedContent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get record content")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun getMedicalRecordsSummary(): Result<MedicalRecordsSummary> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId()
                val totalRecords = medicalDao.getTotalRecordCount(userId)
                val recordsByType = medicalDao.getRecordCountsByType(userId)
                    .associate { RecordType.valueOf(it.type) to it.count }
                val lastUploadedAt = medicalDao.getLastUploadedAt(userId)
                    ?.let { Instant.ofEpochMilli(it) }
                val totalSize = medicalDao.getTotalRecordsSize(userId)
                
                Result.Success(
                    MedicalRecordsSummary(
                        totalRecords = totalRecords,
                        recordsByType = recordsByType,
                        lastUploadedAt = lastUploadedAt,
                        totalSizeBytes = totalSize
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get medical records summary")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    // ==================== Health Reminders ====================
    
    override fun getReminders(): Flow<List<HealthReminder>> {
        return medicalDao.getReminders(getCurrentUserIdSync())
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override fun getActiveReminders(): Flow<List<HealthReminder>> {
        return medicalDao.getActiveReminders(getCurrentUserIdSync())
            .map { entities -> entities.map { it.toDomainModel() } }
    }
    
    override suspend fun getReminder(reminderId: String): Result<HealthReminder> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = medicalDao.getReminder(reminderId)
                if (entity != null) {
                    Result.Success(entity.toDomainModel())
                } else {
                    Result.Error(AppException.UnknownException("Reminder not found"))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get reminder")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun createReminder(reminder: HealthReminder): Result<HealthReminder> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = reminder.toEntity()
                medicalDao.insertReminder(entity)
                Result.Success(reminder)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create reminder")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun updateReminder(reminder: HealthReminder): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val entity = reminder.toEntity()
                medicalDao.updateReminder(entity)
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update reminder")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun deleteReminder(reminderId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                medicalDao.deleteReminderWithDetails(reminderId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete reminder")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun setReminderEnabled(reminderId: String, enabled: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                medicalDao.setReminderEnabled(reminderId, enabled)
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set reminder enabled state")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun recordReminderTriggered(reminderId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                medicalDao.recordReminderTriggered(reminderId, Instant.now().toEpochMilli())
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to record reminder triggered")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun getUpcomingReminders(limit: Int): Result<List<MedicalScheduledReminder>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentTime = Instant.now().toEpochMilli()
                val entities = medicalDao.getUpcomingScheduledReminders(currentTime, limit)
                val reminders = entities.map { it.toDomainModel() }
                Result.Success(reminders)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get upcoming reminders")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    override suspend fun getRemindersSummary(): Result<RemindersSummary> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId()
                val totalReminders = medicalDao.getTotalReminderCount(userId)
                val activeReminders = medicalDao.getActiveReminderCount(userId)
                val remindersByType = medicalDao.getReminderCountsByType(userId)
                    .associate { MedicalReminderType.valueOf(it.type) to it.count }
                
                val upcomingResult = getUpcomingReminders(1)
                val nextScheduled = when (upcomingResult) {
                    is Result.Success -> upcomingResult.data.firstOrNull()
                    is Result.Error -> null
                }
                
                Result.Success(
                    RemindersSummary(
                        totalReminders = totalReminders,
                        activeReminders = activeReminders,
                        remindersByType = remindersByType,
                        nextScheduledReminder = nextScheduled
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to get reminders summary")
                Result.Error(AppException.UnknownException(e.message ?: "Unknown error", e))
            }
        }
    }
    
    // ==================== Helper Functions ====================
    
    private fun getCurrentUserIdSync(): String {
        // This is a simplified version - in production, use proper async handling
        return "current_user" // Placeholder - will be replaced with actual user ID
    }
    
    private fun MedicalRecordEntity.toDomainModel(): MedicalRecord {
        return MedicalRecord(
            id = id,
            userId = userId,
            type = RecordType.valueOf(type),
            title = title,
            description = description,
            fileUri = fileUri,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            uploadedAt = Instant.ofEpochMilli(uploadedAt),
            isEncrypted = isEncrypted
        )
    }
    
    private fun HealthReminderEntity.toDomainModel(): HealthReminder {
        val times = try {
            json.decodeFromString<List<String>>(timesJson)
                .map { LocalTime.parse(it) }
        } catch (e: Exception) {
            emptyList()
        }
        
        val daysOfWeek = daysOfWeekJson?.let {
            try {
                json.decodeFromString<List<String>>(it)
                    .map { day -> DayOfWeek.valueOf(day) }
                    .toSet()
            } catch (e: Exception) {
                null
            }
        }
        
        return HealthReminder(
            id = id,
            userId = userId,
            type = MedicalReminderType.valueOf(type),
            title = title,
            description = description,
            schedule = ReminderSchedule(
                times = times,
                daysOfWeek = daysOfWeek,
                startDate = LocalDate.ofEpochDay(startDate),
                endDate = endDate?.let { LocalDate.ofEpochDay(it) },
                repeatType = RepeatType.valueOf(repeatType)
            ),
            enabled = enabled,
            createdAt = Instant.ofEpochMilli(createdAt),
            lastTriggeredAt = lastTriggeredAt?.let { Instant.ofEpochMilli(it) }
        )
    }
    
    private fun HealthReminder.toEntity(): HealthReminderEntity {
        val timesJson = json.encodeToString(schedule.times.map { it.toString() })
        val daysOfWeekJson = schedule.daysOfWeek?.let {
            json.encodeToString(it.map { day -> day.name })
        }
        
        return HealthReminderEntity(
            id = id,
            userId = userId,
            type = type.name,
            title = title,
            description = description,
            timesJson = timesJson,
            daysOfWeekJson = daysOfWeekJson,
            startDate = schedule.startDate.toEpochDay(),
            endDate = schedule.endDate?.toEpochDay(),
            repeatType = schedule.repeatType.name,
            enabled = enabled,
            createdAt = createdAt.toEpochMilli(),
            lastTriggeredAt = lastTriggeredAt?.toEpochMilli(),
            needsSync = true
        )
    }
    
    private fun ScheduledReminderEntity.toDomainModel(): MedicalScheduledReminder {
        return MedicalScheduledReminder(
            reminderId = reminderId,
            scheduledTime = Instant.ofEpochMilli(scheduledTime),
            title = title,
            message = message,
            type = MedicalReminderType.valueOf(type)
        )
    }
}
