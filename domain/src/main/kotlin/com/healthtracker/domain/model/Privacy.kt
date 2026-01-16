package com.healthtracker.domain.model

import java.time.Instant

/**
 * Privacy settings for controlling data collection and sharing.
 * 
 * Each toggle controls a specific category of data collection.
 * When disabled, the corresponding data type is not collected or synced.
 */
data class PrivacySettings(
    val userId: String,
    val healthMetricsEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val heartRateEnabled: Boolean = true,
    val sleepDataEnabled: Boolean = true,
    val moodDataEnabled: Boolean = true,
    val dietDataEnabled: Boolean = true,
    val socialSharingEnabled: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val crashReportingEnabled: Boolean = true,
    val updatedAt: Instant = Instant.now()
)

/**
 * Represents a category of data that can be toggled on/off.
 */
enum class DataCategory {
    HEALTH_METRICS,
    LOCATION,
    HEART_RATE,
    SLEEP_DATA,
    MOOD_DATA,
    DIET_DATA,
    SOCIAL_SHARING,
    ANALYTICS,
    CRASH_REPORTING
}

/**
 * Audit log entry for tracking sensitive operations.
 * 
 * Used for compliance and security monitoring.
 */
data class AuditLogEntry(
    val id: String,
    val userId: String,
    val operationType: AuditOperationType,
    val details: String,
    val timestamp: Instant,
    val ipAddress: String? = null,
    val deviceInfo: String? = null
)

/**
 * Types of operations that are logged for audit purposes.
 */
enum class AuditOperationType {
    DATA_EXPORT,
    DATA_DELETION,
    PRIVACY_SETTING_CHANGE,
    MEDICAL_RECORD_ACCESS,
    MEDICAL_RECORD_UPLOAD,
    MEDICAL_RECORD_DELETE,
    LOGIN,
    LOGOUT,
    PROFILE_UPDATE,
    SENSITIVE_DATA_ACCESS
}

/**
 * Result of a data export operation.
 */
data class DataExportResult(
    val userId: String,
    val exportedAt: Instant,
    val filePath: String,
    val fileSize: Long,
    val includedCategories: List<ExportCategory>
)

/**
 * Categories of data included in export.
 */
enum class ExportCategory {
    PROFILE,
    HEALTH_METRICS,
    MEDICAL_RECORDS,
    GAMIFICATION,
    SOCIAL,
    SUGGESTIONS,
    ANOMALIES,
    SETTINGS
}

/**
 * Exported user data in a portable format.
 */
data class ExportedUserData(
    val exportVersion: String = "1.0",
    val exportedAt: Instant,
    val userId: String,
    val profile: UserProfile?,
    val settings: UserSettings?,
    val privacySettings: PrivacySettings?,
    val healthMetrics: List<HealthMetricsExport>,
    val gamification: GamificationExport?,
    val socialData: SocialExport?,
    val medicalRecords: List<MedicalRecordExport>,
    val auditLog: List<AuditLogEntry>
)

/**
 * Health metrics in export format.
 */
data class HealthMetricsExport(
    val date: String,
    val steps: Int,
    val distanceMeters: Double,
    val caloriesBurned: Double,
    val screenTimeMinutes: Int,
    val sleepDurationMinutes: Int,
    val sleepQuality: String?,
    val mood: String?
)

/**
 * Gamification data in export format.
 */
data class GamificationExport(
    val totalPoints: Int,
    val level: Int,
    val streaks: List<StreakExport>,
    val badges: List<BadgeExport>
)

/**
 * Streak data in export format.
 */
data class StreakExport(
    val type: String,
    val currentCount: Int,
    val longestCount: Int,
    val lastUpdated: String
)

/**
 * Badge data in export format.
 */
data class BadgeExport(
    val id: String,
    val name: String,
    val unlockedAt: String?
)

/**
 * Social data in export format.
 */
data class SocialExport(
    val circles: List<CircleExport>
)

/**
 * Circle data in export format.
 */
data class CircleExport(
    val id: String,
    val name: String,
    val role: String,
    val joinedAt: String
)

/**
 * Medical record in export format (metadata only, not encrypted content).
 */
data class MedicalRecordExport(
    val id: String,
    val type: String,
    val title: String,
    val uploadedAt: String
)
