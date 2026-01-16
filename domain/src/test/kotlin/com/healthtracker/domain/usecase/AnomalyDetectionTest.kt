package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.UserBaseline
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Property-based tests for anomaly detection threshold compliance.
 * 
 * **Validates: Requirements 4.2, 4.3, 4.4, 4.5**
 * 
 * Property 7: Anomaly Detection Threshold Compliance
 * For any health metric value that deviates more than 2 standard deviations 
 * from the user's baseline (for steps, sleep, screen time, or heart rate), 
 * the Anomaly_Detector SHALL flag an anomaly of the appropriate type.
 */
class AnomalyDetectionTest : FunSpec({
    
    test("Property 7.1: Values >2 std dev below baseline for steps flag LOW_ACTIVITY anomaly") {
        checkAll(100, Arb.double(1000.0..20000.0), Arb.double(500.0..3000.0)) { avgSteps, stdDev ->
            val baseline = createTestBaseline(
                averageSteps = avgSteps,
                stdDevSteps = stdDev
            )
            
            // Value more than 2 std dev below average
            val anomalousValue = avgSteps - (stdDev * 2.5)
            
            if (anomalousValue >= 0) {
                val isAnomalous = baseline.isAnomalous(MetricType.STEPS, anomalousValue)
                isAnomalous.shouldBeTrue()
            }
        }
    }
    
    test("Property 7.2: Values within 2 std dev of baseline do NOT flag anomaly") {
        checkAll(100, Arb.double(5000.0..15000.0), Arb.double(500.0..2000.0)) { avgSteps, stdDev ->
            val baseline = createTestBaseline(
                averageSteps = avgSteps,
                stdDevSteps = stdDev
            )
            
            // Value within 1.5 std dev (should NOT be anomalous)
            val normalValue = avgSteps - (stdDev * 1.5)
            
            if (normalValue >= 0) {
                val isAnomalous = baseline.isAnomalous(MetricType.STEPS, normalValue)
                isAnomalous.shouldBeFalse()
            }
        }
    }
    
    test("Property 7.3: Values >2 std dev above baseline for screen time flag EXCESSIVE_SCREEN_TIME") {
        checkAll(100, Arb.double(60.0..300.0), Arb.double(20.0..60.0)) { avgScreenTime, stdDev ->
            val baseline = createTestBaseline(
                averageScreenTimeMinutes = avgScreenTime,
                stdDevScreenTime = stdDev
            )
            
            // Value more than 2 std dev above average
            val anomalousValue = avgScreenTime + (stdDev * 2.5)
            
            val isAnomalous = baseline.isAnomalous(MetricType.SCREEN_TIME, anomalousValue)
            isAnomalous.shouldBeTrue()
        }
    }
    
    test("Property 7.4: Values >2 std dev from baseline for sleep flag IRREGULAR_SLEEP") {
        checkAll(100, Arb.double(360.0..540.0), Arb.double(30.0..90.0)) { avgSleep, stdDev ->
            val baseline = createTestBaseline(
                averageSleepMinutes = avgSleep,
                stdDevSleep = stdDev
            )
            
            // Value more than 2 std dev below average (too little sleep)
            val tooLittleSleep = avgSleep - (stdDev * 2.5)
            if (tooLittleSleep >= 0) {
                baseline.isAnomalous(MetricType.SLEEP, tooLittleSleep).shouldBeTrue()
            }
            
            // Value more than 2 std dev above average (too much sleep)
            val tooMuchSleep = avgSleep + (stdDev * 2.5)
            baseline.isAnomalous(MetricType.SLEEP, tooMuchSleep).shouldBeTrue()
        }
    }
    
    test("Property 7.5: Values >2 std dev above baseline for heart rate flag ELEVATED_HEART_RATE") {
        checkAll(100, Arb.double(60.0..90.0), Arb.double(5.0..15.0)) { avgHr, stdDev ->
            val baseline = createTestBaseline(
                averageHeartRate = avgHr,
                stdDevHeartRate = stdDev
            )
            
            // Value more than 2 std dev above average
            val elevatedHr = avgHr + (stdDev * 2.5)
            
            val isAnomalous = baseline.isAnomalous(MetricType.HEART_RATE, elevatedHr)
            isAnomalous.shouldBeTrue()
        }
    }
    
    test("Property 7.6: Expected range calculation is correct (mean ± 2*stdDev)") {
        checkAll(100, Arb.double(5000.0..15000.0), Arb.double(500.0..2000.0)) { avgSteps, stdDev ->
            val baseline = createTestBaseline(
                averageSteps = avgSteps,
                stdDevSteps = stdDev
            )
            
            val expectedRange = baseline.getExpectedRange(MetricType.STEPS)!!
            
            // Expected min should be avg - 2*stdDev (but not negative)
            val expectedMin = (avgSteps - 2 * stdDev).coerceAtLeast(0.0)
            val expectedMax = avgSteps + 2 * stdDev
            
            abs(expectedRange.start - expectedMin) shouldBe 0.0
            abs(expectedRange.endInclusive - expectedMax) shouldBe 0.0
        }
    }
    
    test("Property 7.7: Boundary values at exactly 2 std dev are NOT anomalous") {
        checkAll(50, Arb.double(5000.0..15000.0), Arb.double(500.0..2000.0)) { avgSteps, stdDev ->
            val baseline = createTestBaseline(
                averageSteps = avgSteps,
                stdDevSteps = stdDev
            )
            
            // Value at exactly 2 std dev below (boundary - should be in range)
            val boundaryLow = (avgSteps - 2 * stdDev).coerceAtLeast(0.0)
            val boundaryHigh = avgSteps + 2 * stdDev
            
            // Boundary values should NOT be anomalous (they're at the edge of the range)
            baseline.isAnomalous(MetricType.STEPS, boundaryLow).shouldBeFalse()
            baseline.isAnomalous(MetricType.STEPS, boundaryHigh).shouldBeFalse()
        }
    }
    
    test("Property 7.8: Values just outside 2 std dev ARE anomalous") {
        checkAll(50, Arb.double(5000.0..15000.0), Arb.double(500.0..2000.0)) { avgSteps, stdDev ->
            val baseline = createTestBaseline(
                averageSteps = avgSteps,
                stdDevSteps = stdDev
            )
            
            // Value just outside 2 std dev
            val justOutsideLow = (avgSteps - 2 * stdDev - 1).coerceAtLeast(0.0)
            val justOutsideHigh = avgSteps + 2 * stdDev + 1
            
            if (justOutsideLow < avgSteps - 2 * stdDev) {
                baseline.isAnomalous(MetricType.STEPS, justOutsideLow).shouldBeTrue()
            }
            baseline.isAnomalous(MetricType.STEPS, justOutsideHigh).shouldBeTrue()
        }
    }
})

/**
 * Helper function to create a test UserBaseline.
 */
private fun createTestBaseline(
    averageSteps: Double = 8000.0,
    averageSleepMinutes: Double = 420.0,
    averageScreenTimeMinutes: Double = 180.0,
    averageHeartRate: Double = 72.0,
    averageHrv: Double = 45.0,
    stdDevSteps: Double = 1500.0,
    stdDevSleep: Double = 60.0,
    stdDevScreenTime: Double = 45.0,
    stdDevHeartRate: Double = 8.0,
    stdDevHrv: Double = 10.0
): UserBaseline {
    return UserBaseline(
        userId = "test-user",
        averageSteps = averageSteps,
        averageSleepMinutes = averageSleepMinutes,
        averageScreenTimeMinutes = averageScreenTimeMinutes,
        averageHeartRate = averageHeartRate,
        averageHrv = averageHrv,
        standardDeviations = mapOf(
            MetricType.STEPS to stdDevSteps,
            MetricType.SLEEP to stdDevSleep,
            MetricType.SCREEN_TIME to stdDevScreenTime,
            MetricType.HEART_RATE to stdDevHeartRate,
            MetricType.HRV to stdDevHrv
        ),
        calculatedAt = Instant.now(),
        dataPointCount = 7
    )
}
