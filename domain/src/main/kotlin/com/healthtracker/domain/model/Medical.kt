package com.healthtracker.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents a medical record stored by the user.
 * 
 * Medical records are encrypted at rest and require authentication to view.
 */
data class MedicalRecord(
    val id: String,
    val userId: String,
    val type: RecordType,
    val title: String,
    val description: String? = null,
    val fileUri: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val uploadedAt: Instant,
    val isEncrypted: Boolean = true
)

/**
 * Types of medical records that can be stored.
 */
enum class RecordType {
    LAB_REPORT,
    PRESCRIPTION,
    IMAGING,
    VACCINATION,
    DISCHARGE_SUMMARY,
    INSURANCE,
    OTHER
}

/**
 * Represents a health reminder set by the user.
 */
data class HealthReminder(
    val id: String,
    val userId: String,
    val type: MedicalReminderType,
    val title: String,
    val description: String? = null,
    val schedule: ReminderSchedule,
    val enabled: Boolean = true,
    val createdAt: Instant,
    val lastTriggeredAt: Instant? = null
)

/**
 * Types of health reminders.
 */
enum class MedicalReminderType {
    MEDICINE,
    VACCINATION,
    APPOINTMENT,
    CHECKUP,
    CUSTOM
}

/**
 * Schedule configuration for a reminder.
 */
data class ReminderSchedule(
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val repeatType: RepeatType = RepeatType.DAILY
)

/**
 * Repeat pattern for reminders.
 */
enum class RepeatType {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

/**
 * Represents a scheduled reminder notification.
 */
data class MedicalScheduledReminder(
    val reminderId: String,
    val scheduledTime: Instant,
    val title: String,
    val message: String,
    val type: MedicalReminderType
)

/**
 * Result of uploading a medical record.
 */
data class RecordUploadResult(
    val record: MedicalRecord,
    val encryptionSuccessful: Boolean
)

/**
 * Medicine-specific reminder details.
 */
data class MedicineReminder(
    val reminder: HealthReminder,
    val medicineName: String,
    val dosage: String,
    val instructions: String? = null,
    val refillDate: LocalDate? = null
)

/**
 * Vaccination-specific reminder details.
 */
data class VaccinationReminder(
    val reminder: HealthReminder,
    val vaccineName: String,
    val doseNumber: Int? = null,
    val provider: String? = null,
    val location: String? = null
)

/**
 * Appointment-specific reminder details.
 */
data class AppointmentReminder(
    val reminder: HealthReminder,
    val doctorName: String? = null,
    val specialty: String? = null,
    val location: String? = null,
    val notes: String? = null
)

/**
 * Summary of medical records for display.
 */
data class MedicalRecordsSummary(
    val totalRecords: Int,
    val recordsByType: Map<RecordType, Int>,
    val lastUploadedAt: Instant?,
    val totalSizeBytes: Long
)

/**
 * Summary of active reminders.
 */
data class RemindersSummary(
    val totalReminders: Int,
    val activeReminders: Int,
    val remindersByType: Map<MedicalReminderType, Int>,
    val nextScheduledReminder: MedicalScheduledReminder?
)
