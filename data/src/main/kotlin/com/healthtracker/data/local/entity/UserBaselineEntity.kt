package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing user baseline metrics.
 * 
 * Baselines are calculated after 7 days of data collection
 * and used for anomaly detection.
 * 
 * @property userId User who owns this baseline
 * @property averageSteps Average daily step count
 * @property averageSleepMinutes Average sleep duration in minutes
 * @property averageScreenTimeMinutes Average screen time in minutes
 * @property averageHeartRate Average heart rate in BPM
 * @property averageHrv Average HRV (SDNN) in milliseconds
 * @property standardDeviationsJson JSON map of metric type to standard deviation
 * @property calculatedAt Timestamp when baseline was calculated (epoch millis)
 * @property dataPointCount Number of days used to calculate baseline
 */
@Entity(tableName = "user_baseline")
data class UserBaselineEntity(
    @PrimaryKey
    val userId: String,
    val averageSteps: Double,
    val averageSleepMinutes: Double,
    val averageScreenTimeMinutes: Double,
    val averageHeartRate: Double,
    val averageHrv: Double,
    val standardDeviationsJson: String,
    val calculatedAt: Long,
    val dataPointCount: Int
)
