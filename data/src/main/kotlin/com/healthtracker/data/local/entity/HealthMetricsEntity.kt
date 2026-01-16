package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing daily health metrics.
 * 
 * @property id Unique identifier
 * @property userId User who owns these metrics
 * @property date Date as epoch day (days since 1970-01-01)
 * @property steps Total step count
 * @property distanceMeters Distance covered in meters
 * @property caloriesBurned Total calories burned
 * @property screenTimeMinutes Total screen time in minutes
 * @property sleepDurationMinutes Total sleep duration in minutes
 * @property sleepQuality Sleep quality as string (POOR, FAIR, GOOD, EXCELLENT)
 * @property heartRateSamplesJson JSON array of heart rate samples
 * @property hrvSamplesJson JSON array of HRV samples
 * @property mood Mood as string (VERY_SAD, SAD, NEUTRAL, HAPPY, VERY_HAPPY)
 * @property syncedAt Timestamp when data was last synced (epoch millis)
 * @property needsSync Whether this record needs to be synced to server
 * @property version Version number for conflict resolution
 */
@Entity(
    tableName = "health_metrics",
    indices = [
        Index(value = ["userId", "date"], unique = true),
        Index(value = ["needsSync"])
    ]
)
data class HealthMetricsEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val date: Long,
    val steps: Int,
    val distanceMeters: Double,
    val caloriesBurned: Double,
    val screenTimeMinutes: Int,
    val sleepDurationMinutes: Int,
    val sleepQuality: String?,
    val heartRateSamplesJson: String,
    val hrvSamplesJson: String,
    val mood: String?,
    val syncedAt: Long,
    val needsSync: Boolean = false,
    val version: Long = 1
)
