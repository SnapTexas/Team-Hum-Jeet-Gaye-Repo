package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.healthtracker.data.local.entity.AppointmentReminderDetailsEntity
import com.healthtracker.data.local.entity.HealthReminderEntity
import com.healthtracker.data.local.entity.MedicalRecordEntity
import com.healthtracker.data.local.entity.MedicineReminderDetailsEntity
import com.healthtracker.data.local.entity.ScheduledReminderEntity
import com.healthtracker.data.local.entity.VaccinationReminderDetailsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for medical records and health reminders.
 */
@Dao
interface MedicalDao {
    
    // ==================== Medical Records ====================
    
    /**
     * Gets all medical records for a user.
     */
    @Query("SELECT * FROM medical_records WHERE userId = :userId ORDER BY uploadedAt DESC")
    fun getMedicalRecords(userId: String): Flow<List<MedicalRecordEntity>>
    
    /**
     * Gets medical records by type.
     */
    @Query("SELECT * FROM medical_records WHERE userId = :userId AND type = :type ORDER BY uploadedAt DESC")
    fun getMedicalRecordsByType(userId: String, type: String): Flow<List<MedicalRecordEntity>>
    
    /**
     * Gets a specific medical record.
     */
    @Query("SELECT * FROM medical_records WHERE id = :recordId")
    suspend fun getMedicalRecord(recordId: String): MedicalRecordEntity?
    
    /**
     * Inserts a medical record.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalRecord(record: MedicalRecordEntity)
    
    /**
     * Updates a medical record.
     */
    @Update
    suspend fun updateMedicalRecord(record: MedicalRecordEntity)
    
    /**
     * Deletes a medical record.
     */
    @Query("DELETE FROM medical_records WHERE id = :recordId")
    suspend fun deleteMedicalRecord(recordId: String)
    
    /**
     * Gets count of records by type.
     */
    @Query("SELECT type, COUNT(*) as count FROM medical_records WHERE userId = :userId GROUP BY type")
    suspend fun getRecordCountsByType(userId: String): List<RecordTypeCount>
    
    /**
     * Gets total size of all records.
     */
    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM medical_records WHERE userId = :userId")
    suspend fun getTotalRecordsSize(userId: String): Long
    
    /**
     * Gets the most recent upload timestamp.
     */
    @Query("SELECT MAX(uploadedAt) FROM medical_records WHERE userId = :userId")
    suspend fun getLastUploadedAt(userId: String): Long?
    
    /**
     * Gets total record count.
     */
    @Query("SELECT COUNT(*) FROM medical_records WHERE userId = :userId")
    suspend fun getTotalRecordCount(userId: String): Int
    
    /**
     * Deletes all records for a user.
     */
    @Query("DELETE FROM medical_records WHERE userId = :userId")
    suspend fun deleteAllRecordsForUser(userId: String)
    
    /**
     * Gets all medical records for a user (for export).
     */
    @Query("SELECT * FROM medical_records WHERE userId = :userId ORDER BY uploadedAt DESC")
    suspend fun getMedicalRecordsForUser(userId: String): List<MedicalRecordEntity>
    
    /**
     * Deletes all medical records for a user (alias for data deletion).
     */
    @Query("DELETE FROM medical_records WHERE userId = :userId")
    suspend fun deleteAllMedicalRecordsForUser(userId: String)
    
    // ==================== Health Reminders ====================
    
    /**
     * Gets all reminders for a user.
     */
    @Query("SELECT * FROM health_reminders WHERE userId = :userId ORDER BY createdAt DESC")
    fun getReminders(userId: String): Flow<List<HealthReminderEntity>>
    
    /**
     * Gets active (enabled) reminders.
     */
    @Query("SELECT * FROM health_reminders WHERE userId = :userId AND enabled = 1 ORDER BY createdAt DESC")
    fun getActiveReminders(userId: String): Flow<List<HealthReminderEntity>>
    
    /**
     * Gets a specific reminder.
     */
    @Query("SELECT * FROM health_reminders WHERE id = :reminderId")
    suspend fun getReminder(reminderId: String): HealthReminderEntity?
    
    /**
     * Inserts a reminder.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: HealthReminderEntity)
    
    /**
     * Updates a reminder.
     */
    @Update
    suspend fun updateReminder(reminder: HealthReminderEntity)
    
    /**
     * Deletes a reminder.
     */
    @Query("DELETE FROM health_reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: String)
    
    /**
     * Sets reminder enabled state.
     */
    @Query("UPDATE health_reminders SET enabled = :enabled WHERE id = :reminderId")
    suspend fun setReminderEnabled(reminderId: String, enabled: Boolean)
    
    /**
     * Records reminder triggered time.
     */
    @Query("UPDATE health_reminders SET lastTriggeredAt = :triggeredAt WHERE id = :reminderId")
    suspend fun recordReminderTriggered(reminderId: String, triggeredAt: Long)
    
    /**
     * Gets count of reminders by type.
     */
    @Query("SELECT type, COUNT(*) as count FROM health_reminders WHERE userId = :userId GROUP BY type")
    suspend fun getReminderCountsByType(userId: String): List<ReminderTypeCount>
    
    /**
     * Gets count of active reminders.
     */
    @Query("SELECT COUNT(*) FROM health_reminders WHERE userId = :userId AND enabled = 1")
    suspend fun getActiveReminderCount(userId: String): Int
    
    /**
     * Gets total reminder count.
     */
    @Query("SELECT COUNT(*) FROM health_reminders WHERE userId = :userId")
    suspend fun getTotalReminderCount(userId: String): Int
    
    /**
     * Deletes all reminders for a user.
     */
    @Query("DELETE FROM health_reminders WHERE userId = :userId")
    suspend fun deleteAllRemindersForUser(userId: String)
    
    // ==================== Scheduled Reminders ====================
    
    /**
     * Gets upcoming scheduled reminders.
     */
    @Query("SELECT * FROM scheduled_reminders WHERE scheduledTime > :currentTime AND fired = 0 ORDER BY scheduledTime ASC LIMIT :limit")
    suspend fun getUpcomingScheduledReminders(currentTime: Long, limit: Int): List<ScheduledReminderEntity>
    
    /**
     * Gets scheduled reminders for a specific reminder.
     */
    @Query("SELECT * FROM scheduled_reminders WHERE reminderId = :reminderId AND fired = 0 ORDER BY scheduledTime ASC")
    suspend fun getScheduledRemindersForReminder(reminderId: String): List<ScheduledReminderEntity>
    
    /**
     * Inserts a scheduled reminder.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledReminder(scheduledReminder: ScheduledReminderEntity)
    
    /**
     * Inserts multiple scheduled reminders.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledReminders(scheduledReminders: List<ScheduledReminderEntity>)
    
    /**
     * Marks a scheduled reminder as fired.
     */
    @Query("UPDATE scheduled_reminders SET fired = 1, firedAt = :firedAt WHERE id = :id")
    suspend fun markScheduledReminderFired(id: String, firedAt: Long)
    
    /**
     * Deletes scheduled reminders for a reminder.
     */
    @Query("DELETE FROM scheduled_reminders WHERE reminderId = :reminderId")
    suspend fun deleteScheduledRemindersForReminder(reminderId: String)
    
    /**
     * Deletes old fired scheduled reminders.
     */
    @Query("DELETE FROM scheduled_reminders WHERE fired = 1 AND firedAt < :cutoffTime")
    suspend fun deleteOldFiredReminders(cutoffTime: Long)
    
    // ==================== Medicine Reminder Details ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineDetails(details: MedicineReminderDetailsEntity)
    
    @Query("SELECT * FROM medicine_reminder_details WHERE reminderId = :reminderId")
    suspend fun getMedicineDetails(reminderId: String): MedicineReminderDetailsEntity?
    
    @Query("DELETE FROM medicine_reminder_details WHERE reminderId = :reminderId")
    suspend fun deleteMedicineDetails(reminderId: String)
    
    // ==================== Vaccination Reminder Details ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaccinationDetails(details: VaccinationReminderDetailsEntity)
    
    @Query("SELECT * FROM vaccination_reminder_details WHERE reminderId = :reminderId")
    suspend fun getVaccinationDetails(reminderId: String): VaccinationReminderDetailsEntity?
    
    @Query("DELETE FROM vaccination_reminder_details WHERE reminderId = :reminderId")
    suspend fun deleteVaccinationDetails(reminderId: String)
    
    // ==================== Appointment Reminder Details ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointmentDetails(details: AppointmentReminderDetailsEntity)
    
    @Query("SELECT * FROM appointment_reminder_details WHERE reminderId = :reminderId")
    suspend fun getAppointmentDetails(reminderId: String): AppointmentReminderDetailsEntity?
    
    @Query("DELETE FROM appointment_reminder_details WHERE reminderId = :reminderId")
    suspend fun deleteAppointmentDetails(reminderId: String)
    
    // ==================== Transactions ====================
    
    /**
     * Deletes a reminder and all its associated data.
     */
    @Transaction
    suspend fun deleteReminderWithDetails(reminderId: String) {
        deleteScheduledRemindersForReminder(reminderId)
        deleteMedicineDetails(reminderId)
        deleteVaccinationDetails(reminderId)
        deleteAppointmentDetails(reminderId)
        deleteReminder(reminderId)
    }
}

/**
 * Helper class for record type counts.
 */
data class RecordTypeCount(
    val type: String,
    val count: Int
)

/**
 * Helper class for reminder type counts.
 */
data class ReminderTypeCount(
    val type: String,
    val count: Int
)
