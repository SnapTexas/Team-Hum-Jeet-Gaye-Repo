package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.MedicalRecordsSummary
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.MedicalScheduledReminder
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.ReminderSchedule
import com.healthtracker.domain.model.RemindersSummary
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Use case interface for medical records and health reminders.
 * 
 * Provides business logic for:
 * - Secure medical record storage with encryption
 * - Health reminder management and scheduling
 * - Authentication requirements for sensitive operations
 */
interface MedicalUseCase {
    
    // ==================== Medical Records ====================
    
    /**
     * Gets all medical records for the current user.
     */
    fun getMedicalRecords(): Flow<List<MedicalRecord>>
    
    /**
     * Gets medical records filtered by type.
     */
    fun getMedicalRecordsByType(type: RecordType): Flow<List<MedicalRecord>>
    
    /**
     * Gets a specific medical record.
     * Requires authentication.
     */
    suspend fun getMedicalRecord(recordId: String): Result<MedicalRecord>
    
    /**
     * Uploads a new medical record with encryption.
     * 
     * @param title Title for the record
     * @param description Optional description
     * @param type Type of medical record
     * @param fileInputStream Input stream of the file
     * @param fileName Original file name
     * @param mimeType MIME type of the file
     * @return The created record or error
     */
    suspend fun uploadMedicalRecord(
        title: String,
        description: String?,
        type: RecordType,
        fileInputStream: InputStream,
        fileName: String,
        mimeType: String
    ): Result<MedicalRecord>
    
    /**
     * Updates a medical record's metadata.
     */
    suspend fun updateMedicalRecord(
        recordId: String,
        title: String,
        description: String?,
        type: RecordType
    ): Result<Unit>
    
    /**
     * Deletes a medical record.
     * Requires authentication.
     */
    suspend fun deleteMedicalRecord(recordId: String): Result<Unit>
    
    /**
     * Gets the decrypted content of a medical record.
     * Requires authentication.
     */
    suspend fun getRecordContent(recordId: String): Result<ByteArray>
    
    /**
     * Gets a summary of all medical records.
     */
    suspend fun getMedicalRecordsSummary(): Result<MedicalRecordsSummary>
    
    /**
     * Validates a file for upload.
     * 
     * @param fileName File name
     * @param fileSizeBytes File size in bytes
     * @param mimeType MIME type
     * @return Validation result
     */
    fun validateFileForUpload(
        fileName: String,
        fileSizeBytes: Long,
        mimeType: String
    ): FileValidationResult
    
    // ==================== Health Reminders ====================
    
    /**
     * Gets all health reminders.
     */
    fun getReminders(): Flow<List<HealthReminder>>
    
    /**
     * Gets only active reminders.
     */
    fun getActiveReminders(): Flow<List<HealthReminder>>
    
    /**
     * Gets a specific reminder.
     */
    suspend fun getReminder(reminderId: String): Result<HealthReminder>
    
    /**
     * Creates a new health reminder.
     */
    suspend fun createReminder(
        type: MedicalReminderType,
        title: String,
        description: String?,
        schedule: ReminderSchedule
    ): Result<HealthReminder>
    
    /**
     * Updates an existing reminder.
     */
    suspend fun updateReminder(reminder: HealthReminder): Result<Unit>
    
    /**
     * Deletes a reminder.
     */
    suspend fun deleteReminder(reminderId: String): Result<Unit>
    
    /**
     * Enables or disables a reminder.
     */
    suspend fun setReminderEnabled(reminderId: String, enabled: Boolean): Result<Unit>
    
    /**
     * Gets upcoming scheduled reminders.
     */
    suspend fun getUpcomingReminders(limit: Int = 10): Result<List<MedicalScheduledReminder>>
    
    /**
     * Gets a summary of all reminders.
     */
    suspend fun getRemindersSummary(): Result<RemindersSummary>
    
    /**
     * Schedules notifications for a reminder.
     */
    suspend fun scheduleReminderNotifications(reminderId: String): Result<Int>
    
    /**
     * Cancels scheduled notifications for a reminder.
     */
    suspend fun cancelReminderNotifications(reminderId: String): Result<Unit>
    
    /**
     * Validates a reminder schedule.
     */
    fun validateReminderSchedule(schedule: ReminderSchedule): ScheduleValidationResult
}

/**
 * Result of file validation for upload.
 */
sealed class FileValidationResult {
    object Valid : FileValidationResult()
    data class Invalid(val reason: String) : FileValidationResult()
}

/**
 * Result of reminder schedule validation.
 */
sealed class ScheduleValidationResult {
    object Valid : ScheduleValidationResult()
    data class Invalid(val reason: String) : ScheduleValidationResult()
}

/**
 * Supported file types for medical records.
 */
object SupportedFileTypes {
    val IMAGES = setOf("image/jpeg", "image/png", "image/webp")
    val DOCUMENTS = setOf("application/pdf")
    val ALL = IMAGES + DOCUMENTS
    
    const val MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
}
