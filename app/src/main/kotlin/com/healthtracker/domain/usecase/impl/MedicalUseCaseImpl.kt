package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.AppException
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
import com.healthtracker.domain.repository.MedicalRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.FileValidationResult
import com.healthtracker.domain.usecase.MedicalUseCase
import com.healthtracker.domain.usecase.ScheduleValidationResult
import com.healthtracker.domain.usecase.SupportedFileTypes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicalUseCaseImpl @Inject constructor(
    private val medicalRepository: MedicalRepository,
    private val userRepository: UserRepository,
    private val notificationService: com.healthtracker.service.notification.MedicalReminderNotificationService
) : MedicalUseCase {
    
    override fun getMedicalRecords(): Flow<List<MedicalRecord>> {
        return medicalRepository.getMedicalRecords()
    }
    
    override fun getMedicalRecordsByType(type: RecordType): Flow<List<MedicalRecord>> {
        return medicalRepository.getMedicalRecordsByType(type)
    }
    
    override suspend fun getMedicalRecord(recordId: String): Result<MedicalRecord> {
        return medicalRepository.getMedicalRecord(recordId)
    }
    
    override suspend fun uploadMedicalRecord(
        title: String,
        description: String?,
        type: RecordType,
        fileInputStream: InputStream,
        fileName: String,
        mimeType: String
    ): Result<MedicalRecord> {
        if (title.isBlank()) {
            return Result.Error(AppException.ValidationException("validation", "Title cannot be empty"))
        }
        
        if (title.length > 200) {
            return Result.Error(AppException.ValidationException("validation", "Title too long (max 200 characters)"))
        }
        
        if (mimeType !in SupportedFileTypes.ALL) {
            return Result.Error(
                AppException.ValidationException("validation", "Unsupported file type: $mimeType")
            )
        }
        
        return medicalRepository.uploadRecord(
            title = title.trim(),
            description = description?.trim(),
            type = type,
            fileInputStream = fileInputStream,
            fileName = fileName,
            mimeType = mimeType
        )
    }
    
    override suspend fun updateMedicalRecord(
        recordId: String,
        title: String,
        description: String?,
        type: RecordType
    ): Result<Unit> {
        if (title.isBlank()) {
            return Result.Error(AppException.ValidationException("validation", "Title cannot be empty"))
        }
        
        return medicalRepository.updateRecord(
            recordId = recordId,
            title = title.trim(),
            description = description?.trim(),
            type = type
        )
    }
    
    override suspend fun deleteMedicalRecord(recordId: String): Result<Unit> {
        return medicalRepository.deleteRecord(recordId)
    }
    
    override suspend fun getRecordContent(recordId: String): Result<ByteArray> {
        return medicalRepository.getRecordContent(recordId)
    }
    
    override suspend fun getMedicalRecordsSummary(): Result<MedicalRecordsSummary> {
        return medicalRepository.getMedicalRecordsSummary()
    }
    
    override fun validateFileForUpload(
        fileName: String,
        fileSizeBytes: Long,
        mimeType: String
    ): FileValidationResult {
        if (fileSizeBytes > SupportedFileTypes.MAX_FILE_SIZE_BYTES) {
            val maxSizeMB = SupportedFileTypes.MAX_FILE_SIZE_BYTES / (1024 * 1024)
            return FileValidationResult.Invalid("File too large. Maximum size is ${maxSizeMB}MB")
        }
        
        if (fileSizeBytes <= 0) {
            return FileValidationResult.Invalid("File is empty")
        }
        
        if (mimeType !in SupportedFileTypes.ALL) {
            return FileValidationResult.Invalid("Unsupported file type")
        }
        
        if (fileName.isBlank()) {
            return FileValidationResult.Invalid("File name is required")
        }
        
        return FileValidationResult.Valid
    }

    override fun getReminders(): Flow<List<HealthReminder>> {
        return medicalRepository.getReminders()
    }
    
    override fun getActiveReminders(): Flow<List<HealthReminder>> {
        return medicalRepository.getActiveReminders()
    }
    
    override suspend fun getReminder(reminderId: String): Result<HealthReminder> {
        return medicalRepository.getReminder(reminderId)
    }
    
    override suspend fun createReminder(
        type: MedicalReminderType,
        title: String,
        description: String?,
        schedule: ReminderSchedule
    ): Result<HealthReminder> {
        if (title.isBlank()) {
            return Result.Error(AppException.ValidationException("validation", "Title cannot be empty"))
        }
        
        if (title.length > 100) {
            return Result.Error(AppException.ValidationException("validation", "Title too long (max 100 characters)"))
        }
        
        val scheduleValidation = validateReminderSchedule(schedule)
        if (scheduleValidation is ScheduleValidationResult.Invalid) {
            return Result.Error(AppException.ValidationException("validation", scheduleValidation.reason))
        }
        
        val reminder = HealthReminder(
            id = UUID.randomUUID().toString(),
            userId = "current_user", // Will be replaced by repository with actual user ID
            type = type,
            title = title.trim(),
            description = description?.trim(),
            schedule = schedule,
            enabled = true,
            createdAt = Instant.now(),
            lastTriggeredAt = null
        )
        
        val result = medicalRepository.createReminder(reminder)
        
        if (result is Result.Success) {
            scheduleReminderNotifications(reminder.id)
        }
        
        return result
    }
    
    override suspend fun updateReminder(reminder: HealthReminder): Result<Unit> {
        val scheduleValidation = validateReminderSchedule(reminder.schedule)
        if (scheduleValidation is ScheduleValidationResult.Invalid) {
            return Result.Error(AppException.ValidationException("validation", scheduleValidation.reason))
        }
        
        val result = medicalRepository.updateReminder(reminder)
        
        if (result is Result.Success && reminder.enabled) {
            cancelReminderNotifications(reminder.id)
            scheduleReminderNotifications(reminder.id)
        }
        
        return result
    }
    
    override suspend fun deleteReminder(reminderId: String): Result<Unit> {
        cancelReminderNotifications(reminderId)
        return medicalRepository.deleteReminder(reminderId)
    }
    
    override suspend fun setReminderEnabled(reminderId: String, enabled: Boolean): Result<Unit> {
        val result = medicalRepository.setReminderEnabled(reminderId, enabled)
        
        if (result is Result.Success) {
            if (enabled) {
                scheduleReminderNotifications(reminderId)
            } else {
                cancelReminderNotifications(reminderId)
            }
        }
        
        return result
    }
    
    override suspend fun getUpcomingReminders(limit: Int): Result<List<MedicalScheduledReminder>> {
        return medicalRepository.getUpcomingReminders(limit)
    }
    
    override suspend fun getRemindersSummary(): Result<RemindersSummary> {
        return medicalRepository.getRemindersSummary()
    }
    
    override suspend fun scheduleReminderNotifications(reminderId: String): Result<Int> {
        return when (val reminderResult = medicalRepository.getReminder(reminderId)) {
            is Result.Success -> {
                val count = notificationService.scheduleReminder(reminderResult.data)
                Result.Success(count)
            }
            is Result.Error -> Result.Error(reminderResult.exception)
        }
    }
    
    override suspend fun cancelReminderNotifications(reminderId: String): Result<Unit> {
        notificationService.cancelReminder(reminderId)
        return Result.Success(Unit)
    }
    
    override fun validateReminderSchedule(schedule: ReminderSchedule): ScheduleValidationResult {
        if (schedule.times.isEmpty()) {
            return ScheduleValidationResult.Invalid("At least one reminder time is required")
        }
        
        if (schedule.times.size > 10) {
            return ScheduleValidationResult.Invalid("Maximum 10 reminder times allowed")
        }
        
        if (schedule.startDate.isBefore(LocalDate.now().minusDays(1))) {
            return ScheduleValidationResult.Invalid("Start date cannot be in the past")
        }
        
        schedule.endDate?.let { endDate ->
            if (endDate.isBefore(schedule.startDate)) {
                return ScheduleValidationResult.Invalid("End date must be after start date")
            }
        }
        
        if (schedule.repeatType == RepeatType.WEEKLY && schedule.daysOfWeek.isNullOrEmpty()) {
            return ScheduleValidationResult.Invalid("Days of week required for weekly reminders")
        }
        
        return ScheduleValidationResult.Valid
    }
}
