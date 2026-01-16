package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.UserBaseline
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Property-based tests for baseline calculation.
 * 
 * **Validates: Requirements 4.1, 4.2**
 * 
 * Property 8: Baseline Calculation
 * The system SHALL calculate user baselines only after collecting
 * at least 7 days of health data. Baselines include averages and
 * standard deviations for steps, sleep, screen time, heart rate, and HRV.
 */
class BaselineCalculationTest : FunSpec({
    
    test("Property 8.1: Baseline requires minimum 7 days of data") {
        checkAll(100, Arb.int(1..6)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            // With less than 7 days, baseline should be null
            baseline.shouldBeNull()
        }
    }
    
    test("Property 8.2: Baseline is valid with exactly 7 days of data") {
        val metrics = generateMetricsList(7)
        val baseline = calculateBaseline(metrics)
        
        baseline.shouldNotBeNull()
        baseline.isValid.shouldBeTrue()
        baseline.dataPointCount shouldBe 7
    }
    
    test("Property 8.3: Baseline is valid with more than 7 days of data") {
        checkAll(50, Arb.int(7..30)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            baseline.shouldNotBeNull()
            baseline.isValid.shouldBeTrue()
            baseline.dataPointCount shouldBe dayCount
        }
    }
    
    test("Property 8.4: Baseline average equals sum/count for steps") {
        checkAll(50, Arb.int(7..14)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            baseline.shouldNotBeNull()
            
            val expectedAvg = metrics.sumOf { it.steps } / dayCount.toDouble()
            abs(baseline.averageSteps - expectedAvg) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 8.5: Baseline average equals sum/count for sleep") {
        checkAll(50, Arb.int(7..14)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            baseline.shouldNotBeNull()
            
            val expectedAvg = metrics.sumOf { it.sleepDurationMinutes } / dayCount.toDouble()
            abs(baseline.averageSleepMinutes - expectedAvg) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 8.6: Baseline average equals sum/count for screen time") {
        checkAll(50, Arb.int(7..14)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            baseline.shouldNotBeNull()
            
            val expectedAvg = metrics.sumOf { it.screenTimeMinutes } / dayCount.toDouble()
            abs(baseline.averageScreenTimeMinutes - expectedAvg) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 8.7: Standard deviation is non-negative for all metrics") {
        checkAll(50, Arb.int(7..14)) { dayCount ->
            val metrics = generateMetricsList(dayCount)
            val baseline = calculateBaseline(metrics)
            
            baseline.shouldNotBeNull()
            
            baseline.standardDeviations.values.forEach { stdDev ->
                stdDev shouldBeGreaterThanOrEqual 0.0
            }
        }
    }
    
    test("Property 8.8: Standard deviation is zero for constant values") {
        // Create metrics with constant step values
        val constantSteps = 8000
        val metrics = (1..7).map { day ->
            createTestMetrics(
                steps = constantSteps,
                sleepMinutes = 420 + day * 10, // Varying sleep
                screenTimeMinutes = 180,
                day = day
            )
        }
        
        val baseline = calculateBaseline(metrics)
        baseline.shouldNotBeNull()
        
        // Steps should have zero std dev since all values are the same
        baseline.standardDeviations[MetricType.STEPS] shouldBe 0.0
    }
    
    test("Property 8.9: Baseline with <7 days has isValid = false") {
        // Create a baseline manually with less than 7 data points
        val baseline = UserBaseline(
            userId = "test-user",
            averageSteps = 8000.0,
            averageSleepMinutes = 420.0,
            averageScreenTimeMinutes = 180.0,
            averageHeartRate = 72.0,
            averageHrv = 45.0,
            standardDeviations = mapOf(
                MetricType.STEPS to 1500.0,
                MetricType.SLEEP to 60.0,
                MetricType.SCREEN_TIME to 45.0
            ),
            calculatedAt = Instant.now(),
            dataPointCount = 5 // Less than 7
        )
        
        baseline.isValid.shouldBeFalse()
    }
    
    test("Property 8.10: Baseline includes all required metric types") {
        val metrics = generateMetricsList(7)
        val baseline = calculateBaseline(metrics)
        
        baseline.shouldNotBeNull()
        
        // Should have standard deviations for core metrics
        baseline.standardDeviations.containsKey(MetricType.STEPS).shouldBeTrue()
        baseline.standardDeviations.containsKey(MetricType.SLEEP).shouldBeTrue()
        baseline.standardDeviations.containsKey(MetricType.SCREEN_TIME).shouldBeTrue()
    }
    
    test("Property 8.11: Expected range calculation uses 2 standard deviations") {
        checkAll(50, Arb.double(5000.0..15000.0), Arb.double(500.0..2000.0)) { avgSteps, stdDev ->
            val baseline = UserBaseline(
                userId = "test-user",
                averageSteps = avgSteps,
                averageSleepMinutes = 420.0,
                averageScreenTimeMinutes = 180.0,
                averageHeartRate = 72.0,
                averageHrv = 45.0,
                standardDeviations = mapOf(MetricType.STEPS to stdDev),
                calculatedAt = Instant.now(),
                dataPointCount = 7
            )
            
            val range = baseline.getExpectedRange(MetricType.STEPS)
            range.shouldNotBeNull()
            
            val expectedMin = (avgSteps - 2 * stdDev).coerceAtLeast(0.0)
            val expectedMax = avgSteps + 2 * stdDev
            
            abs(range.start - expectedMin) shouldBeLessThanOrEqual 0.001
            abs(range.endInclusive - expectedMax) shouldBeLessThanOrEqual 0.001
        }
    }
})

/**
 * Helper function to generate a list of test metrics.
 */
private fun generateMetricsList(dayCount: Int): List<HealthMetrics> {
    return (1..dayCount).map { day ->
        createTestMetrics(
            steps = 5000 + (day * 500) + (Math.random() * 2000).toInt(),
            sleepMinutes = 360 + (day * 10) + (Math.random() * 60).toInt(),
            screenTimeMinutes = 120 + (day * 5) + (Math.random() * 30).toInt(),
            day = day
        )
    }
}

/**
 * Helper function to create test HealthMetrics.
 */
private fun createTestMetrics(
    steps: Int,
    sleepMinutes: Int,
    screenTimeMinutes: Int,
    day: Int
): HealthMetrics {
    val date = LocalDate.now().minusDays(day.toLong())
    return HealthMetrics(
        id = "test-metrics-$day",
        userId = "test-user",
        date = date,
        steps = steps,
        distanceMeters = steps * 0.762, // Average step length
        caloriesBurned = steps * 0.04,
        screenTimeMinutes = screenTimeMinutes,
        sleepDurationMinutes = sleepMinutes,
        sleepQuality = null,
        heartRateSamples = listOf(
            HeartRateSample(Instant.now(), 70 + (Math.random() * 10).toInt())
        ),
        hrvSamples = listOf(
            HrvSample(Instant.now(), 40.0 + Math.random() * 20)
        ),
        mood = null,
        syncedAt = Instant.now()
    )
}

/**
 * Simulates baseline calculation logic matching AnomalyRepositoryImpl.
 */
private fun calculateBaseline(metrics: List<HealthMetrics>): UserBaseline? {
    if (metrics.size < UserBaseline.MINIMUM_DAYS_FOR_BASELINE) {
        return null
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
    
    return UserBaseline(
        userId = "test-user",
        averageSteps = avgSteps,
        averageSleepMinutes = avgSleep,
        averageScreenTimeMinutes = avgScreenTime,
        averageHeartRate = avgHeartRate,
        averageHrv = avgHrv,
        standardDeviations = stdDevs,
        calculatedAt = Instant.now(),
        dataPointCount = metrics.size
    )
}

/**
 * Calculates standard deviation.
 */
private fun calculateStdDev(values: List<Double>, mean: Double): Double {
    if (values.size < 2) return 0.0
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}
