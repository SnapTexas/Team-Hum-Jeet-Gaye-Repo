package com.healthtracker.domain.usecase.impl

import android.util.Log
import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalyDetectionResult
import com.healthtracker.domain.model.AnomalySeverity
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.UserBaseline
import com.healthtracker.domain.repository.AnomalyRepository
import com.healthtracker.domain.usecase.AnomalyDetectionUseCase
import com.healthtracker.ml.MLResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of AnomalyDetectionUseCase.
 * 
 * Uses defensive ML wrapper pattern - all ML operations are wrapped
 * in MLResult to ensure graceful fallback on failure.
 * 
 * Rule: ML failure = graceful fallback, never block UI or background job.
 */
class AnomalyDetectionUseCaseImpl @Inject constructor(
    private val anomalyRepository: AnomalyRepository
) : AnomalyDetectionUseCase {
    
    companion object {
        private const val TAG = "AnomalyDetection"
        private const val ML_TIMEOUT_MS = 200L
    }
    
    override suspend fun analyzeForAnomalies(metrics: HealthMetrics): AnomalyDetectionResult = 
        withContext(Dispatchers.Default) {
            val baseline = anomalyRepository.getUserBaselineSync()
            
            if (baseline == null || !baseline.isValid) {
                Log.d(TAG, "No valid baseline available, skipping anomaly detection")
                return@withContext AnomalyDetectionResult(
                    anomalies = emptyList(),
                    usedMLModel = false,
                    fallbackReason = "No valid baseline (need ${UserBaseline.MINIMUM_DAYS_FOR_BASELINE} days of data)"
                )
            }
            
            // Try ML-based detection with timeout
            val mlResult = tryMLDetection(metrics, baseline)
            
            when (mlResult) {
                is MLResult.Success -> {
                    val anomalies = mlResult.data
                    // Save detected anomalies
                    if (anomalies.isNotEmpty()) {
                        anomalyRepository.saveAnomalies(anomalies)
                    }
                    AnomalyDetectionResult(
                        anomalies = anomalies,
                        usedMLModel = true
                    )
                }
                is MLResult.Fallback -> {
                    Log.w(TAG, "ML detection failed: ${mlResult.reason}, using rule-based fallback")
                    // Fallback to rule-based detection
                    val anomalies = detectAnomaliesRuleBased(metrics, baseline)
                    if (anomalies.isNotEmpty()) {
                        anomalyRepository.saveAnomalies(anomalies)
                    }
                    AnomalyDetectionResult(
                        anomalies = anomalies,
                        usedMLModel = false,
                        fallbackReason = mlResult.reason
                    )
                }
            }
        }
    
    /**
     * Attempts ML-based anomaly detection with timeout.
     * 
     * CRITICAL: All ML operations must be wrapped in MLResult.
     * Max timeout: 200ms to prevent blocking.
     */
    private suspend fun tryMLDetection(
        metrics: HealthMetrics,
        baseline: UserBaseline
    ): MLResult<List<Anomaly>> {
        return MLResult.runWithTimeout(
            timeoutMs = ML_TIMEOUT_MS,
            fallbackReason = "ML inference timed out after ${ML_TIMEOUT_MS}ms"
        ) {
            // For now, use rule-based detection as ML model placeholder
            // In production, this would call TFLite model
            detectAnomaliesRuleBased(metrics, baseline)
        }
    }
    
    /**
     * Rule-based anomaly detection using 2 standard deviation threshold.
     * 
     * This is the fallback when ML fails and also serves as the
     * baseline implementation until ML model is integrated.
     */
    private fun detectAnomaliesRuleBased(
        metrics: HealthMetrics,
        baseline: UserBaseline
    ): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        val now = Instant.now()
        
        // Check steps (low activity)
        checkMetricAnomaly(
            metricType = MetricType.STEPS,
            actualValue = metrics.steps.toDouble(),
            baseline = baseline,
            anomalyType = AnomalyType.LOW_ACTIVITY,
            checkLow = true,
            checkHigh = false,
            userId = metrics.userId,
            detectedAt = now
        )?.let { anomalies.add(it) }
        
        // Check screen time (excessive)
        checkMetricAnomaly(
            metricType = MetricType.SCREEN_TIME,
            actualValue = metrics.screenTimeMinutes.toDouble(),
            baseline = baseline,
            anomalyType = AnomalyType.EXCESSIVE_SCREEN_TIME,
            checkLow = false,
            checkHigh = true,
            userId = metrics.userId,
            detectedAt = now
        )?.let { anomalies.add(it) }
        
        // Check sleep (irregular - both too low and too high)
        checkMetricAnomaly(
            metricType = MetricType.SLEEP,
            actualValue = metrics.sleepDurationMinutes.toDouble(),
            baseline = baseline,
            anomalyType = AnomalyType.IRREGULAR_SLEEP,
            checkLow = true,
            checkHigh = true,
            userId = metrics.userId,
            detectedAt = now
        )?.let { anomalies.add(it) }
        
        // Check heart rate (elevated)
        if (metrics.heartRateSamples.isNotEmpty()) {
            val avgHeartRate = metrics.heartRateSamples.map { it.bpm }.average()
            checkMetricAnomaly(
                metricType = MetricType.HEART_RATE,
                actualValue = avgHeartRate,
                baseline = baseline,
                anomalyType = AnomalyType.ELEVATED_HEART_RATE,
                checkLow = false,
                checkHigh = true,
                userId = metrics.userId,
                detectedAt = now
            )?.let { anomalies.add(it) }
        }
        
        // Check HRV (high stress = low HRV)
        if (metrics.hrvSamples.isNotEmpty()) {
            val avgHrv = metrics.hrvSamples.map { it.sdnn }.average()
            checkMetricAnomaly(
                metricType = MetricType.HRV,
                actualValue = avgHrv,
                baseline = baseline,
                anomalyType = AnomalyType.HIGH_STRESS,
                checkLow = true,
                checkHigh = false,
                userId = metrics.userId,
                detectedAt = now
            )?.let { anomalies.add(it) }
        }
        
        return anomalies
    }

    
    /**
     * Checks if a metric value is anomalous and creates an Anomaly if so.
     */
    private fun checkMetricAnomaly(
        metricType: MetricType,
        actualValue: Double,
        baseline: UserBaseline,
        anomalyType: AnomalyType,
        checkLow: Boolean,
        checkHigh: Boolean,
        userId: String,
        detectedAt: Instant
    ): Anomaly? {
        val expectedRange = baseline.getExpectedRange(metricType) ?: return null
        
        val isLowAnomaly = checkLow && actualValue < expectedRange.start
        val isHighAnomaly = checkHigh && actualValue > expectedRange.endInclusive
        
        if (!isLowAnomaly && !isHighAnomaly) return null
        
        val severity = calculateSeverity(actualValue, expectedRange, baseline.standardDeviations[metricType] ?: 0.0)
        val message = generateMessage(anomalyType, metricType, actualValue, expectedRange)
        
        return Anomaly(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = anomalyType,
            severity = severity,
            detectedAt = detectedAt,
            metricType = metricType,
            actualValue = actualValue,
            expectedRange = expectedRange,
            message = message
        )
    }
    
    private fun calculateSeverity(
        actualValue: Double,
        expectedRange: ClosedRange<Double>,
        stdDev: Double
    ): AnomalySeverity {
        if (stdDev == 0.0) return AnomalySeverity.INFO
        
        val mean = (expectedRange.start + expectedRange.endInclusive) / 2
        val deviation = kotlin.math.abs(actualValue - mean) / stdDev
        
        return when {
            deviation > 4 -> AnomalySeverity.ALERT
            deviation > 3 -> AnomalySeverity.WARNING
            else -> AnomalySeverity.INFO
        }
    }
    
    private fun generateMessage(
        type: AnomalyType,
        metricType: MetricType,
        actualValue: Double,
        expectedRange: ClosedRange<Double>
    ): String {
        return when (type) {
            AnomalyType.LOW_ACTIVITY -> 
                "Your step count (${actualValue.toInt()}) is significantly below your usual range (${expectedRange.start.toInt()}-${expectedRange.endInclusive.toInt()})."
            AnomalyType.EXCESSIVE_SCREEN_TIME -> 
                "Your screen time (${(actualValue / 60).toInt()}h ${(actualValue % 60).toInt()}m) is higher than usual."
            AnomalyType.IRREGULAR_SLEEP -> 
                "Your sleep duration (${(actualValue / 60).toInt()}h ${(actualValue % 60).toInt()}m) is outside your normal pattern."
            AnomalyType.ELEVATED_HEART_RATE -> 
                "Your average heart rate (${actualValue.toInt()} bpm) is elevated compared to your baseline."
            AnomalyType.HIGH_STRESS -> 
                "Your HRV indicates elevated stress levels. Consider taking a break."
            AnomalyType.MISSED_HYDRATION -> 
                "You haven't logged any water intake today."
        }
    }
    
    override fun getUserBaseline(): Flow<UserBaseline?> {
        return anomalyRepository.getUserBaseline()
    }
    
    override suspend fun updateBaseline(metrics: List<HealthMetrics>): Result<UserBaseline?> = 
        withContext(Dispatchers.Default) {
            try {
                val baseline = anomalyRepository.calculateAndSaveBaseline(metrics)
                Result.success(baseline)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update baseline", e)
                Result.failure(e)
            }
        }
    
    override fun getUnacknowledgedAnomalies(): Flow<List<Anomaly>> {
        return anomalyRepository.getUnacknowledgedAnomalies()
    }
    
    override suspend fun acknowledgeAnomaly(anomalyId: String): Result<Unit> {
        return anomalyRepository.acknowledgeAnomaly(anomalyId)
    }
    
    override suspend fun hasValidBaseline(): Boolean = withContext(Dispatchers.IO) {
        val baseline = anomalyRepository.getUserBaselineSync()
        baseline?.isValid == true
    }
}
