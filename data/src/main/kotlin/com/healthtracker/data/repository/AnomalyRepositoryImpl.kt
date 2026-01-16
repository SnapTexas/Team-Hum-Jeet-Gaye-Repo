package com.healthtracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtracker.data.local.dao.AnomalyDao
import com.healthtracker.data.local.dao.UserBaselineDao
import com.healthtracker.data.local.entity.AnomalyEntity
import com.healthtracker.data.local.entity.UserBaselineEntity
import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalySeverity
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.UserBaseline
import com.healthtracker.domain.repository.AnomalyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Implementation of AnomalyRepository.
 * Handles anomaly detection, storage, and baseline management.
 */
class AnomalyRepositoryImpl @Inject constructor(
    private val anomalyDao: AnomalyDao,
    private val userBaselineDao: UserBaselineDao,
    private val firebaseAuth: FirebaseAuth,
    private val gson: Gson
) : AnomalyRepository {
    
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "anonymous"
    
    override fun getAnomalies(date: LocalDate): Flow<List<Anomaly>> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        
        return anomalyDao.getAnomaliesByDate(currentUserId, startOfDay, endOfDay)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override fun getUnacknowledgedAnomalies(): Flow<List<Anomaly>> {
        return anomalyDao.getUnacknowledgedAnomalies(currentUserId)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun getRecentAnomalies(limit: Int): List<Anomaly> = withContext(Dispatchers.IO) {
        anomalyDao.getRecentAnomalies(currentUserId, limit).map { it.toDomain() }
    }
    
    override suspend fun detectAnomalies(metrics: HealthMetrics): List<Anomaly> = withContext(Dispatchers.IO) {
        val baseline = userBaselineDao.getBaselineSync(currentUserId)?.toDomain()
        
        if (baseline == null || !baseline.isValid) {
            return@withContext emptyList()
        }
        
        val anomalies = mutableListOf<Anomaly>()
        val now = Instant.now()
        
        // Check steps (low activity)
        if (baseline.isAnomalous(MetricType.STEPS, metrics.steps.toDouble())) {
            val expectedRange = baseline.getExpectedRange(MetricType.STEPS)!!
            if (metrics.steps < expectedRange.start) {
                anomalies.add(createAnomaly(
                    type = AnomalyType.LOW_ACTIVITY,
                    metricType = MetricType.STEPS,
                    actualValue = metrics.steps.toDouble(),
                    expectedRange = expectedRange,
                    baseline = baseline,
                    detectedAt = now
                ))
            }
        }
        
        // Check screen time (excessive)
        if (baseline.isAnomalous(MetricType.SCREEN_TIME, metrics.screenTimeMinutes.toDouble())) {
            val expectedRange = baseline.getExpectedRange(MetricType.SCREEN_TIME)!!
            if (metrics.screenTimeMinutes > expectedRange.endInclusive) {
                anomalies.add(createAnomaly(
                    type = AnomalyType.EXCESSIVE_SCREEN_TIME,
                    metricType = MetricType.SCREEN_TIME,
                    actualValue = metrics.screenTimeMinutes.toDouble(),
                    expectedRange = expectedRange,
                    baseline = baseline,
                    detectedAt = now
                ))
            }
        }
        
        // Check sleep (irregular)
        if (baseline.isAnomalous(MetricType.SLEEP, metrics.sleepDurationMinutes.toDouble())) {
            val expectedRange = baseline.getExpectedRange(MetricType.SLEEP)!!
            anomalies.add(createAnomaly(
                type = AnomalyType.IRREGULAR_SLEEP,
                metricType = MetricType.SLEEP,
                actualValue = metrics.sleepDurationMinutes.toDouble(),
                expectedRange = expectedRange,
                baseline = baseline,
                detectedAt = now
            ))
        }
        
        // Check heart rate (elevated)
        if (metrics.heartRateSamples.isNotEmpty()) {
            val avgHeartRate = metrics.heartRateSamples.map { it.bpm }.average()
            if (baseline.isAnomalous(MetricType.HEART_RATE, avgHeartRate)) {
                val expectedRange = baseline.getExpectedRange(MetricType.HEART_RATE)!!
                if (avgHeartRate > expectedRange.endInclusive) {
                    anomalies.add(createAnomaly(
                        type = AnomalyType.ELEVATED_HEART_RATE,
                        metricType = MetricType.HEART_RATE,
                        actualValue = avgHeartRate,
                        expectedRange = expectedRange,
                        baseline = baseline,
                        detectedAt = now
                    ))
                }
            }
        }
        
        // Check HRV (high stress - low HRV)
        if (metrics.hrvSamples.isNotEmpty()) {
            val avgHrv = metrics.hrvSamples.map { it.sdnn }.average()
            if (baseline.isAnomalous(MetricType.HRV, avgHrv)) {
                val expectedRange = baseline.getExpectedRange(MetricType.HRV)!!
                if (avgHrv < expectedRange.start) {
                    anomalies.add(createAnomaly(
                        type = AnomalyType.HIGH_STRESS,
                        metricType = MetricType.HRV,
                        actualValue = avgHrv,
                        expectedRange = expectedRange,
                        baseline = baseline,
                        detectedAt = now
                    ))
                }
            }
        }
        
        anomalies
    }

    
    override suspend fun saveAnomalies(anomalies: List<Anomaly>) = withContext(Dispatchers.IO) {
        val entities = anomalies.map { it.toEntity() }
        anomalyDao.insertAllAnomalies(entities)
    }
    
    override suspend fun acknowledgeAnomaly(anomalyId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            anomalyDao.acknowledgeAnomaly(anomalyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getUserBaseline(): Flow<UserBaseline?> {
        return userBaselineDao.getBaseline(currentUserId)
            .map { entity -> entity?.toDomain() }
    }
    
    override suspend fun getUserBaselineSync(): UserBaseline? = withContext(Dispatchers.IO) {
        userBaselineDao.getBaselineSync(currentUserId)?.toDomain()
    }
    
    override suspend fun calculateAndSaveBaseline(metrics: List<HealthMetrics>): UserBaseline? = withContext(Dispatchers.IO) {
        if (metrics.size < UserBaseline.MINIMUM_DAYS_FOR_BASELINE) {
            return@withContext null
        }
        
        val count = metrics.size.toDouble()
        
        // Calculate averages
        val avgSteps = metrics.sumOf { it.steps } / count
        val avgSleep = metrics.sumOf { it.sleepDurationMinutes } / count
        val avgScreenTime = metrics.sumOf { it.screenTimeMinutes } / count
        
        val allHeartRates = metrics.flatMap { m -> m.heartRateSamples.map { it.bpm.toDouble() } }
        val avgHeartRate = if (allHeartRates.isNotEmpty()) allHeartRates.average() else 0.0
        
        val allHrvs = metrics.flatMap { m -> m.hrvSamples.map { it.sdnn } }
        val avgHrv = if (allHrvs.isNotEmpty()) allHrvs.average() else 0.0
        
        // Calculate standard deviations
        val stdDevs = mutableMapOf<MetricType, Double>()
        
        stdDevs[MetricType.STEPS] = calculateStdDev(metrics.map { it.steps.toDouble() }, avgSteps)
        stdDevs[MetricType.SLEEP] = calculateStdDev(metrics.map { it.sleepDurationMinutes.toDouble() }, avgSleep)
        stdDevs[MetricType.SCREEN_TIME] = calculateStdDev(metrics.map { it.screenTimeMinutes.toDouble() }, avgScreenTime)
        
        if (allHeartRates.isNotEmpty()) {
            stdDevs[MetricType.HEART_RATE] = calculateStdDev(allHeartRates, avgHeartRate)
        }
        
        if (allHrvs.isNotEmpty()) {
            stdDevs[MetricType.HRV] = calculateStdDev(allHrvs, avgHrv)
        }
        
        val baseline = UserBaseline(
            userId = currentUserId,
            averageSteps = avgSteps,
            averageSleepMinutes = avgSleep,
            averageScreenTimeMinutes = avgScreenTime,
            averageHeartRate = avgHeartRate,
            averageHrv = avgHrv,
            standardDeviations = stdDevs,
            calculatedAt = Instant.now(),
            dataPointCount = metrics.size
        )
        
        // Save to database
        userBaselineDao.insertBaseline(baseline.toEntity())
        
        baseline
    }
    
    override suspend fun deleteAllAnomalies() = withContext(Dispatchers.IO) {
        anomalyDao.deleteAllForUser(currentUserId)
    }
    
    private fun calculateStdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
    
    private fun createAnomaly(
        type: AnomalyType,
        metricType: MetricType,
        actualValue: Double,
        expectedRange: ClosedRange<Double>,
        baseline: UserBaseline,
        detectedAt: Instant
    ): Anomaly {
        val severity = calculateSeverity(actualValue, expectedRange, baseline.standardDeviations[metricType] ?: 0.0)
        val message = generateMessage(type, metricType, actualValue, expectedRange)
        
        return Anomaly(
            id = UUID.randomUUID().toString(),
            userId = currentUserId,
            type = type,
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
    
    // Extension functions for mapping
    private fun AnomalyEntity.toDomain(): Anomaly = Anomaly(
        id = id,
        userId = userId,
        type = AnomalyType.valueOf(type),
        severity = AnomalySeverity.valueOf(severity),
        detectedAt = Instant.ofEpochMilli(detectedAt),
        metricType = MetricType.valueOf(metricType),
        actualValue = actualValue,
        expectedRange = expectedMin..expectedMax,
        message = message,
        acknowledged = acknowledged
    )
    
    private fun Anomaly.toEntity(): AnomalyEntity = AnomalyEntity(
        id = id,
        userId = userId,
        type = type.name,
        severity = severity.name,
        detectedAt = detectedAt.toEpochMilli(),
        metricType = metricType.name,
        actualValue = actualValue,
        expectedMin = expectedRange.start,
        expectedMax = expectedRange.endInclusive,
        message = message,
        acknowledged = acknowledged
    )
    
    private fun UserBaselineEntity.toDomain(): UserBaseline {
        val stdDevsType = object : TypeToken<Map<String, Double>>() {}.type
        val stdDevsMap: Map<String, Double> = gson.fromJson(standardDeviationsJson, stdDevsType) ?: emptyMap()
        val stdDevs = stdDevsMap.mapKeys { MetricType.valueOf(it.key) }
        
        return UserBaseline(
            userId = userId,
            averageSteps = averageSteps,
            averageSleepMinutes = averageSleepMinutes,
            averageScreenTimeMinutes = averageScreenTimeMinutes,
            averageHeartRate = averageHeartRate,
            averageHrv = averageHrv,
            standardDeviations = stdDevs,
            calculatedAt = Instant.ofEpochMilli(calculatedAt),
            dataPointCount = dataPointCount
        )
    }
    
    private fun UserBaseline.toEntity(): UserBaselineEntity {
        val stdDevsMap = standardDeviations.mapKeys { it.key.name }
        
        return UserBaselineEntity(
            userId = userId,
            averageSteps = averageSteps,
            averageSleepMinutes = averageSleepMinutes,
            averageScreenTimeMinutes = averageScreenTimeMinutes,
            averageHeartRate = averageHeartRate,
            averageHrv = averageHrv,
            standardDeviationsJson = gson.toJson(stdDevsMap),
            calculatedAt = calculatedAt.toEpochMilli(),
            dataPointCount = dataPointCount
        )
    }
}
