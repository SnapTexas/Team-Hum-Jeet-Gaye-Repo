package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing privacy settings.
 * 
 * Each toggle controls a specific category of data collection.
 * When disabled, the corresponding data type is not collected or synced.
 */
@Entity(
    tableName = "privacy_settings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class PrivacySettingsEntity(
    @PrimaryKey
    val userId: String,
    
    // Data collection toggles
    val healthMetricsEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val heartRateEnabled: Boolean = true,
    val sleepDataEnabled: Boolean = true,
    val moodDataEnabled: Boolean = true,
    val dietDataEnabled: Boolean = true,
    val socialSharingEnabled: Boolean = true,
    val analyticsEnabled: Boolean = true,
    val crashReportingEnabled: Boolean = true,
    
    // Timestamps
    val updatedAt: Long // Epoch millis
)

/**
 * Room entity for storing audit log entries.
 * 
 * Tracks sensitive operations for compliance and security monitoring.
 */
@Entity(
    tableName = "audit_log",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index("timestamp"),
        Index("operationType")
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val id: String,
    
    val userId: String,
    val operationType: String, // AuditOperationType enum as string
    val details: String,
    val timestamp: Long, // Epoch millis
    val ipAddress: String? = null,
    val deviceInfo: String? = null
)
