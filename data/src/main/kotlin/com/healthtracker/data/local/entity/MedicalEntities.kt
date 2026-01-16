package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing medical records metadata.
 * 
 * The actual file content is stored encrypted in the file system.
 * This entity stores metadata and the path to the encrypted file.
 */
@Entity(
    tableName = "medical_records",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["type"]),
        Index(value = ["uploadedAt"])
    ]
)
data class MedicalRecordEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String?,
    val fileUri: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val uploadedAt: Long,
    val isEncrypted: Boolean,
    val encryptedContentPath: String?,
    val needsSync: Boolean = false,
    val syncedAt: Long? = null
)

/**
 * Room entity for storing health reminders.
 */
@Entity(
    tableName = "health_reminders",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["type"]),
        Index(value = ["enabled"]),
        Index(value = ["startDate"])
    ]
)
data class HealthReminderEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String?,
    val timesJson: String,
    val daysOfWeekJson: String?,
    val startDate: Long,
    val endDate: Long?,
    val repeatType: String,
    val enabled: Boolean,
    val createdAt: Long,
    val lastTriggeredAt: Long?,
    val needsSync: Boolean = false,
    val syncedAt: Long? = null
)

/**
 * Room entity for tracking scheduled reminder notifications.
 * 
 * Used to track which reminders have been scheduled with AlarmManager
 * and when they should fire.
 */
@Entity(
    tableName = "scheduled_reminders",
    indices = [
        Index(value = ["reminderId"]),
        Index(value = ["scheduledTime"]),
        Index(value = ["fired"])
    ]
)
data class ScheduledReminderEntity(
    @PrimaryKey
    val id: String,
    val reminderId: String,
    val scheduledTime: Long,
    val title: String,
    val message: String,
    val type: String,
    val alarmRequestCode: Int,
    val fired: Boolean = false,
    val firedAt: Long? = null
)

/**
 * Room entity for medicine-specific reminder details.
 */
@Entity(
    tableName = "medicine_reminder_details",
    indices = [Index(value = ["reminderId"])]
)
data class MedicineReminderDetailsEntity(
    @PrimaryKey
    val reminderId: String,
    val medicineName: String,
    val dosage: String,
    val instructions: String?,
    val refillDate: Long?
)

/**
 * Room entity for vaccination-specific reminder details.
 */
@Entity(
    tableName = "vaccination_reminder_details",
    indices = [Index(value = ["reminderId"])]
)
data class VaccinationReminderDetailsEntity(
    @PrimaryKey
    val reminderId: String,
    val vaccineName: String,
    val doseNumber: Int?,
    val provider: String?,
    val location: String?
)

/**
 * Room entity for appointment-specific reminder details.
 */
@Entity(
    tableName = "appointment_reminder_details",
    indices = [Index(value = ["reminderId"])]
)
data class AppointmentReminderDetailsEntity(
    @PrimaryKey
    val reminderId: String,
    val doctorName: String?,
    val specialty: String?,
    val location: String?,
    val notes: String?
)
