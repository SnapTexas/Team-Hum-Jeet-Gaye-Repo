package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * Domain model representing daily health metrics.
 * 
 * @property id Unique identifier for the metrics record
 * @property userId User who owns these metrics
 * @property date Date for which metrics were collected
 * @property steps Total step count for the day
 * @property distanceMeters Distance covered in meters (derived from steps)
 * @property caloriesBurned Total calories burned
 * @property screenTimeMinutes Total screen time in minutes
 * @property sleepDurationMinutes Total sleep duration in minutes
 * @property sleepQuality Quality assessment of sleep
 * @property heartRateSamples Heart rate measurements throughout the day
 * @property hrvSamples Heart rate variability measurements
 * @property mood User's reported mood
 * @property syncedAt Timestamp when data was last synced
 */
data class HealthMetrics(
    val id: String,
    val userId: String,
    val date: LocalDate,
    val steps: Int,
    val distanceMeters: Double,
    val caloriesBurned: Double,
    val screenTimeMinutes: Int,
    val sleepDurationMinutes: Int,
    val sleepQuality: SleepQuality?,
    val heartRateSamples: List<HeartRateSample>,
    val hrvSamples: List<HrvSample>,
    val mood: Mood?,
    val syncedAt: Instant
)

/**
 * A single heart rate measurement.
 * 
 * @property timestamp When the measurement was taken
 * @property bpm Heart rate in beats per minute
 */
data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Int
)

/**
 * A single heart rate variability measurement.
 * 
 * @property timestamp When the measurement was taken
 * @property sdnn Standard deviation of NN intervals in milliseconds
 */
data class HrvSample(
    val timestamp: Instant,
    val sdnn: Double
)

/**
 * Sleep quality assessment.
 */
enum class SleepQuality {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT
}

/**
 * User's mood state.
 */
enum class Mood {
    VERY_SAD,
    SAD,
    NEUTRAL,
    HAPPY,
    VERY_HAPPY
}

/**
 * Type of health metric for analytics and anomaly detection.
 */
enum class MetricType {
    STEPS,
    DISTANCE,
    CALORIES,
    SCREEN_TIME,
    SLEEP,
    HEART_RATE,
    HRV,
    MOOD
}
