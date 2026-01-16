package com.healthtracker.domain.repository

import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.MedicalRecordsSummary
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.MedicalScheduledReminder
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.RemindersSummary
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Repository interface for medical records and health reminders.
 * 
 * Handles secure storage of medical documents and scheduling of health reminders.
 * All medical records are encrypted before storage.
 */
interface MedicalRepository {
    
    // ==================== Medical Records ====================
    
    /**
     * Gets all medical records for the current user.
     * 
     * @return Flow emitting list of medical records
     */
    fun getMedicalRecords(): Flow<List<MedicalRecord>>
    
    /**
     * Gets medical records filtered by type.
     * 
     * @param type The record type to filter by
     * @return Flow emitting filtered list of records
     */
    fun getMedicalRecordsByType(type: RecordType): Flow<List<MedicalRecord>>
    
    /**
     * Gets a specific medical record by ID.
     * 
     * @param recordId The record's unique identifier
     * @return The medical record or error
     */
    suspend fun getMedicalRecord(recordId: String): Result<MedicalRecord>
    
    /**
     * Uploads and encrypts a new medical record.
     * 
     * @param title Title for the record
     * @param description Optional description
     * @param type Type of medical record
     * @param fileInputStream Input stream of the file to upload
     * @param fileName Original file name
     * @param mimeType MIME type of the file
     * @return The created record or error
     */
    suspend fun uploadRecord(
        title: String,
        description: String?,
        type: RecordType,
        fileInputStream: InputStream,
        fileName: String,
        mimeType: String
    ): Result<MedicalRecord>
    
    /**
     * Updates an existing medical record's metadata.
     * 
     * @param recordId The record's ID
     * @param title New title
     * @param description New description
     * @param type New type
     * @return Success or error
     */
    suspend fun updateRecord(
        recordId: String,
        title: String,
        description: String?,
        type: RecordType
    ): Result<Unit>
    
    /**
     * Deletes a medical record.
     * 
     * @param recordId The record's unique identifier
     * @return Success or error
     */
    suspend fun deleteRecord(recordId: String): Result<Unit>
    
    /**
     * Gets the decrypted content of a medical record.
     * Requires authentication before calling.
     * 
     * @param recordId The record's unique identifier
     * @return Decrypted file content as ByteArray or error
     */
    suspend fun getRecordContent(recordId: String): Result<ByteArray>
    
    /**
     * Gets a summary of all medical records.
     * 
     * @return Summary statistics
     */
    suspend fun getMedicalRecordsSummary(): Result<MedicalRecordsSummary>
    
    // ==================== Health Reminders ====================
    
    /**
     * Gets all health reminders for the current user.
     * 
     * @return Flow emitting list of reminders
     */
    fun getReminders(): Flow<List<HealthReminder>>
    
    /**
     * Gets only active (enabled) reminders.
     * 
     * @return Flow emitting list of active reminders
     */
    fun getActiveReminders(): Flow<List<HealthReminder>>
    
    /**
     * Gets a specific reminder by ID.
     * 
     * @param reminderId The reminder's unique identifier
     * @return The reminder or error
     */
    suspend fun getReminder(reminderId: String): Result<HealthReminder>
    
    /**
     * Creates a new health reminder.
     * 
     * @param reminder The reminder to create
     * @return The created reminder or error
     */
    suspend fun createReminder(reminder: HealthReminder): Result<HealthReminder>
    
    /**
     * Updates an existing reminder.
     * 
     * @param reminder The updated reminder
     * @return Success or error
     */
    suspend fun updateReminder(reminder: HealthReminder): Result<Unit>
    
    /**
     * Deletes a reminder.
     * 
     * @param reminderId The reminder's unique identifier
     * @return Success or error
     */
    suspend fun deleteReminder(reminderId: String): Result<Unit>
    
    /**
     * Enables or disables a reminder.
     * 
     * @param reminderId The reminder's ID
     * @param enabled Whether the reminder should be enabled
     * @return Success or error
     */
    suspend fun setReminderEnabled(reminderId: String, enabled: Boolean): Result<Unit>
    
    /**
     * Records that a reminder was triggered.
     * 
     * @param reminderId The reminder's ID
     * @return Success or error
     */
    suspend fun recordReminderTriggered(reminderId: String): Result<Unit>
    
    /**
     * Gets the next scheduled reminders.
     * 
     * @param limit Maximum number of reminders to return
     * @return List of upcoming scheduled reminders
     */
    suspend fun getUpcomingReminders(limit: Int = 10): Result<List<MedicalScheduledReminder>>
    
    /**
     * Gets a summary of all reminders.
     * 
     * @return Summary statistics
     */
    suspend fun getRemindersSummary(): Result<RemindersSummary>
}
