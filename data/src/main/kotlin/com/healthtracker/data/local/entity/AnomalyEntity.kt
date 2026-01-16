package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing detected anomalies.
 * 
 * @property id Unique identifier
 * @property userId User who owns this anomaly
 * @property type Anomaly type (LOW_ACTIVITY, EXCESSIVE_SCREEN_TIME, etc.)
 * @property severity Severity level (INFO, WARNING, ALERT)
 * @property detectedAt Timestamp when anomaly was detected (epoch millis)
 * @property metricType Type of metric that triggered the anomaly
 * @property actualValue The actual value that was detected
 * @property expectedMin Lower bound of expected range
 * @property expectedMax Upper bound of expected range
 * @property message Human-readable description of the anomaly
 * @property acknowledged Whether user has acknowledged this anomaly
 */
@Entity(
    tableName = "anomalies",
    indices = [
        Index(value = ["userId", "detectedAt"]),
        Index(value = ["acknowledged"])
    ]
)
data class AnomalyEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val severity: String,
    val detectedAt: Long,
    val metricType: String,
    val actualValue: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val message: String,
    val acknowledged: Boolean
)
