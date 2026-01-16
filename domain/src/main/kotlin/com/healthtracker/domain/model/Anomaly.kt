package com.healthtracker.domain.model

import java.time.Instant

/**
 * Domain model representing a detected health anomaly.
 * 
 * @property id Unique identifier for the anomaly
 * @property userId User who owns this anomaly
 * @property type Type of anomaly detected
 * @property severity Severity level of the anomaly
 * @property detectedAt Timestamp when anomaly was detected
 * @property metricType Type of metric that triggered the anomaly
 * @property actualValue The actual value that was detected
 * @property expectedRange Expected range based on user's baseline
 * @property message Human-readable description of the anomaly
 * @property acknowledged Whether user has acknowledged this anomaly
 */
data class Anomaly(
    val id: String,
    val userId: String,
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val detectedAt: Instant,
    val metricType: MetricType,
    val actualValue: Double,
    val expectedRange: ClosedRange<Double>,
    val message: String,
    val acknowledged: Boolean = false
)

/**
 * Types of anomalies that can be detected.
 */
enum class AnomalyType {
    /** Step count significantly below baseline */
    LOW_ACTIVITY,
    /** Screen time significantly above baseline */
    EXCESSIVE_SCREEN_TIME,
    /** Sleep duration or quality significantly different from baseline */
    IRREGULAR_SLEEP,
    /** Heart rate significantly above baseline */
    ELEVATED_HEART_RATE,
    /** HRV indicates high stress levels */
    HIGH_STRESS,
    /** User hasn't logged water intake */
    MISSED_HYDRATION
}

/**
 * Severity levels for anomalies.
 */
enum class AnomalySeverity {
    /** Informational - minor deviation */
    INFO,
    /** Warning - moderate deviation requiring attention */
    WARNING,
    /** Alert - significant deviation requiring immediate attention */
    ALERT
}

/**
 * User's baseline health metrics calculated from historical data.
 * 
 * Baselines are calculated after 7 days of data collection
 * and used for anomaly detection using 2 standard deviation threshold.
 * 
 * @property userId User who owns this baseline
 * @property averageSteps Average daily step count
 * @property averageSleepMinutes Average sleep duration in minutes
 * @property averageScreenTimeMinutes Average screen time in minutes
 * @property averageHeartRate Average heart rate in BPM
 * @property averageHrv Average HRV (SDNN) in milliseconds
 * @property standardDeviations Map of metric type to standard deviation
 * @property calculatedAt Timestamp when baseline was calculated
 * @property dataPointCount Number of days used to calculate baseline
 */
data class UserBaseline(
    val userId: String,
    val averageSteps: Double,
    val averageSleepMinutes: Double,
    val averageScreenTimeMinutes: Double,
    val averageHeartRate: Double,
    val averageHrv: Double,
    val standardDeviations: Map<MetricType, Double>,
    val calculatedAt: Instant,
    val dataPointCount: Int
) {
    /**
     * Minimum number of days required to calculate a valid baseline.
     */
    companion object {
        const val MINIMUM_DAYS_FOR_BASELINE = 7
        const val ANOMALY_THRESHOLD_STD_DEV = 2.0
    }
    
    /**
     * Checks if this baseline has enough data points to be valid.
     */
    val isValid: Boolean
        get() = dataPointCount >= MINIMUM_DAYS_FOR_BASELINE
    
    /**
     * Gets the expected range for a metric type based on 2 standard deviations.
     * 
     * @param metricType The type of metric
     * @return Expected range or null if no baseline exists for this metric
     */
    fun getExpectedRange(metricType: MetricType): ClosedRange<Double>? {
        val stdDev = standardDeviations[metricType] ?: return null
        val average = when (metricType) {
            MetricType.STEPS -> averageSteps
            MetricType.SLEEP -> averageSleepMinutes
            MetricType.SCREEN_TIME -> averageScreenTimeMinutes
            MetricType.HEART_RATE -> averageHeartRate
            MetricType.HRV -> averageHrv
            else -> return null
        }
        
        val margin = stdDev * ANOMALY_THRESHOLD_STD_DEV
        val min = (average - margin).coerceAtLeast(0.0)
        val max = average + margin
        
        return min..max
    }
    
    /**
     * Checks if a value is anomalous for a given metric type.
     * 
     * @param metricType The type of metric
     * @param value The value to check
     * @return True if the value is outside 2 standard deviations from baseline
     */
    fun isAnomalous(metricType: MetricType, value: Double): Boolean {
        val range = getExpectedRange(metricType) ?: return false
        return value !in range
    }
}

/**
 * Result of anomaly detection analysis.
 */
data class AnomalyDetectionResult(
    val anomalies: List<Anomaly>,
    val usedMLModel: Boolean,
    val fallbackReason: String? = null
)
