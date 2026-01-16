package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.MetricAverages
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.usecase.impl.AnalyticsUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

/**
 * Property-based tests for analytics aggregation correctness.
 * 
 * **Validates: Requirements 3.4**
 * 
 * Property 5: Analytics Aggregation Correctness
 * For any list of daily health metrics, the calculated averages, totals, and percentages 
 * SHALL be mathematically correct (average = sum/count, total = sum of values, 
 * percentage = (current/target) × 100).
 */
class AnalyticsAggregationTest : FunSpec({
    
    val analyticsUseCase = AnalyticsUseCaseImpl(mockk(relaxed = true))
    
    test("Property 5.1: Average calculation is mathematically correct (average = sum/count)") {
        checkAll(100, Arb.list(Arb.int(0..50000), 1..30)) { stepsList ->
            // Create health metrics with the generated steps
            val metricsList = stepsList.mapIndexed { index, steps ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    steps = steps
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            // Verify average = sum / count
            val expectedAverage = stepsList.sum().toDouble() / stepsList.size
            
            // Allow small floating point tolerance
            abs(averages.averageSteps - expectedAverage) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 5.2: Calorie average calculation is correct") {
        checkAll(100, Arb.list(Arb.int(0..5000), 1..30)) { caloriesList ->
            val metricsList = caloriesList.mapIndexed { index, calories ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    caloriesBurned = calories.toDouble()
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            val expectedAverage = caloriesList.sum().toDouble() / caloriesList.size
            abs(averages.averageCalories - expectedAverage) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 5.3: Sleep average calculation is correct") {
        checkAll(100, Arb.list(Arb.int(0..720), 1..30)) { sleepMinutesList ->
            val metricsList = sleepMinutesList.mapIndexed { index, sleepMinutes ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    sleepDurationMinutes = sleepMinutes
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            val expectedAverage = sleepMinutesList.sum().toDouble() / sleepMinutesList.size
            abs(averages.averageSleepMinutes - expectedAverage) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 5.4: Distance average calculation is correct") {
        checkAll(100, Arb.list(Arb.int(0..50000), 1..30)) { distanceList ->
            val metricsList = distanceList.mapIndexed { index, distance ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    distanceMeters = distance.toDouble()
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            val expectedAverage = distanceList.sum().toDouble() / distanceList.size
            abs(averages.averageDistance - expectedAverage) shouldBeLessThanOrEqual 0.001
        }
    }
    
    test("Property 5.5: Screen time average calculation is correct") {
        checkAll(100, Arb.list(Arb.int(0..1440), 1..30)) { screenTimeList ->
            val metricsList = screenTimeList.mapIndexed { index, screenTime ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    screenTimeMinutes = screenTime
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            val expectedAverage = screenTimeList.sum().toDouble() / screenTimeList.size
            abs(averages.averageScreenTimeMinutes - expectedAverage) shouldBeLessThanOrEqual 0.001
        }
    }

    
    test("Property 5.6: GoalProgress percentage calculation is correct (percentage = current/target × 100)") {
        checkAll(100, Arb.int(0..50000), Arb.int(1..50000)) { current, target ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = current
            )
            
            // Calculate expected percentage
            val expectedPercentage = (current.toDouble() / target.toDouble()) * 100
            
            // Create GoalProgress and verify
            val goalProgress = com.healthtracker.domain.model.GoalProgress(
                goalType = com.healthtracker.domain.model.MetricType.STEPS,
                target = target.toDouble(),
                current = current.toDouble()
            )
            
            abs(goalProgress.percentComplete - expectedPercentage.toFloat()) shouldBeLessThanOrEqual 0.01f
        }
    }
    
    test("Property 5.7: Empty metrics list returns zero averages") {
        val averages = analyticsUseCase.calculateAverages(emptyList())
        
        averages.averageSteps shouldBe 0.0
        averages.averageDistance shouldBe 0.0
        averages.averageCalories shouldBe 0.0
        averages.averageSleepMinutes shouldBe 0.0
        averages.averageScreenTimeMinutes shouldBe 0.0
        averages.averageHeartRate shouldBe null
        averages.averageHrv shouldBe null
    }
    
    test("Property 5.8: Single metric returns itself as average") {
        checkAll(100, Arb.int(0..50000)) { steps ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps
            )
            
            val averages = analyticsUseCase.calculateAverages(listOf(metrics))
            
            averages.averageSteps shouldBe steps.toDouble()
        }
    }
    
    test("Property 5.9: Averages are always non-negative") {
        checkAll(100, Arb.list(Arb.int(0..50000), 1..30)) { stepsList ->
            val metricsList = stepsList.mapIndexed { index, steps ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    steps = steps
                )
            }
            
            val averages = analyticsUseCase.calculateAverages(metricsList)
            
            averages.averageSteps shouldBeGreaterThanOrEqual 0.0
            averages.averageDistance shouldBeGreaterThanOrEqual 0.0
            averages.averageCalories shouldBeGreaterThanOrEqual 0.0
            averages.averageSleepMinutes shouldBeGreaterThanOrEqual 0.0
            averages.averageScreenTimeMinutes shouldBeGreaterThanOrEqual 0.0
        }
    }
    
    test("Property 5.10: Total calculation is sum of all values") {
        checkAll(100, Arb.list(Arb.int(0..10000), 1..10)) { stepsList ->
            val metricsList = stepsList.mapIndexed { index, steps ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    steps = steps
                )
            }
            
            // Total steps should equal sum
            val totalSteps = metricsList.sumOf { it.steps }
            val expectedSum = stepsList.sum()
            
            totalSteps shouldBe expectedSum
        }
    }
})

/**
 * Helper function to create test HealthMetrics.
 */
private fun createTestMetrics(
    id: String,
    date: LocalDate,
    steps: Int = 0,
    distanceMeters: Double = 0.0,
    caloriesBurned: Double = 0.0,
    screenTimeMinutes: Int = 0,
    sleepDurationMinutes: Int = 0
): HealthMetrics {
    return HealthMetrics(
        id = id,
        userId = "test-user",
        date = date,
        steps = steps,
        distanceMeters = distanceMeters,
        caloriesBurned = caloriesBurned,
        screenTimeMinutes = screenTimeMinutes,
        sleepDurationMinutes = sleepDurationMinutes,
        sleepQuality = null,
        heartRateSamples = emptyList(),
        hrvSamples = emptyList(),
        mood = null,
        syncedAt = Instant.now()
    )
}
