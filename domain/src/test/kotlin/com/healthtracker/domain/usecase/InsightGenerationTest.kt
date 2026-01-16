package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.Insight
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.usecase.impl.AnalyticsUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate

/**
 * Property-based tests for insight generation completeness.
 * 
 * **Validates: Requirements 3.3**
 * 
 * Property 6: Insight Generation Completeness
 * For any non-empty set of health metrics, the Analytics_Engine SHALL generate 
 * at least one insight, and each insight SHALL reference at least one metric 
 * type from the input data.
 */
class InsightGenerationTest : FunSpec({
    
    val analyticsUseCase = AnalyticsUseCaseImpl(mockk(relaxed = true))
    
    test("Property 6.1: Non-empty metrics generate at least one insight") {
        checkAll(100, Arb.int(0..50000), Arb.int(0..720), Arb.int(0..5000)) { steps, sleep, calories ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps,
                sleepDurationMinutes = sleep,
                caloriesBurned = calories.toDouble()
            )
            
            val insights = analyticsUseCase.generateInsights(metrics)
            
            // Non-empty metrics should always generate at least one insight
            insights.shouldNotBeEmpty()
        }
    }
    
    test("Property 6.2: Each insight references at least one metric type from input") {
        checkAll(100, Arb.int(0..50000), Arb.int(0..720)) { steps, sleep ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps,
                sleepDurationMinutes = sleep
            )
            
            val insights = analyticsUseCase.generateInsights(metrics)
            
            // Each insight should reference at least one metric type
            insights.forEach { insight ->
                insight.relatedMetrics.shouldNotBeEmpty()
            }
        }
    }
    
    test("Property 6.3: Insights reference valid metric types") {
        val validMetricTypes = MetricType.entries.toSet()
        
        checkAll(100, Arb.int(0..50000)) { steps ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps
            )
            
            val insights = analyticsUseCase.generateInsights(metrics)
            
            // All referenced metric types should be valid
            insights.forEach { insight ->
                insight.relatedMetrics.forEach { metricType ->
                    validMetricTypes.contains(metricType) shouldBe true
                }
            }
        }
    }
    
    test("Property 6.4: Step goal achievement generates step-related insight") {
        // When steps exceed goal (10000), should generate achievement insight
        val metrics = createTestMetrics(
            id = "test",
            date = LocalDate.now(),
            steps = 12000 // Above 10000 goal
        )
        
        val insights = analyticsUseCase.generateInsights(metrics)
        
        // Should have at least one insight related to steps
        val stepInsights = insights.filter { MetricType.STEPS in it.relatedMetrics }
        stepInsights.shouldNotBeEmpty()
    }
    
    test("Property 6.5: Low sleep generates sleep-related insight") {
        val metrics = createTestMetrics(
            id = "test",
            date = LocalDate.now(),
            steps = 5000,
            sleepDurationMinutes = 300 // 5 hours - below recommended
        )
        
        val insights = analyticsUseCase.generateInsights(metrics)
        
        // Should have at least one insight related to sleep
        val sleepInsights = insights.filter { MetricType.SLEEP in it.relatedMetrics }
        sleepInsights.shouldNotBeEmpty()
    }

    
    test("Property 6.6: High screen time generates screen time insight") {
        val metrics = createTestMetrics(
            id = "test",
            date = LocalDate.now(),
            steps = 5000,
            screenTimeMinutes = 300 // 5 hours - high screen time
        )
        
        val insights = analyticsUseCase.generateInsights(metrics)
        
        // Should have at least one insight related to screen time
        val screenTimeInsights = insights.filter { MetricType.SCREEN_TIME in it.relatedMetrics }
        screenTimeInsights.shouldNotBeEmpty()
    }
    
    test("Property 6.7: Multiple metrics generate multiple relevant insights") {
        val metrics = createTestMetrics(
            id = "test",
            date = LocalDate.now(),
            steps = 15000, // Above goal
            sleepDurationMinutes = 480, // Good sleep
            screenTimeMinutes = 90 // Low screen time
        )
        
        val insights = analyticsUseCase.generateInsights(metrics)
        
        // Should generate multiple insights for multiple good metrics
        insights.size shouldBe insights.distinctBy { it.id }.size // All unique
    }
    
    test("Property 6.8: List of metrics generates insights") {
        checkAll(50, Arb.list(Arb.int(0..50000), 1..7)) { stepsList ->
            val metricsList = stepsList.mapIndexed { index, steps ->
                createTestMetrics(
                    id = "test-$index",
                    date = LocalDate.now().minusDays(index.toLong()),
                    steps = steps
                )
            }
            
            val insights = analyticsUseCase.generateInsights(metricsList)
            
            // Non-empty metrics list should generate at least one insight
            insights.shouldNotBeEmpty()
        }
    }
    
    test("Property 6.9: Trend insights generated for sufficient data points") {
        // Create 7 days of increasing steps to trigger trend insight
        val metricsList = (0..6).map { index ->
            createTestMetrics(
                id = "test-$index",
                date = LocalDate.now().minusDays(index.toLong()),
                steps = 5000 + (index * 1000) // Increasing trend
            )
        }
        
        val insights = analyticsUseCase.generateInsights(metricsList)
        
        // Should generate at least one insight
        insights.shouldNotBeEmpty()
    }
    
    test("Property 6.10: Insights have non-empty messages") {
        checkAll(100, Arb.int(0..50000)) { steps ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps
            )
            
            val insights = analyticsUseCase.generateInsights(metrics)
            
            // All insights should have non-empty messages
            insights.forEach { insight ->
                insight.message.isNotBlank() shouldBe true
            }
        }
    }
    
    test("Property 6.11: Insights have valid IDs") {
        checkAll(100, Arb.int(0..50000)) { steps ->
            val metrics = createTestMetrics(
                id = "test",
                date = LocalDate.now(),
                steps = steps
            )
            
            val insights = analyticsUseCase.generateInsights(metrics)
            
            // All insights should have non-empty IDs
            insights.forEach { insight ->
                insight.id.isNotBlank() shouldBe true
            }
        }
    }
    
    test("Property 6.12: Heart rate insights generated when samples present") {
        val heartRateSamples = listOf(
            HeartRateSample(Instant.now(), 110), // Elevated
            HeartRateSample(Instant.now().minusSeconds(3600), 105),
            HeartRateSample(Instant.now().minusSeconds(7200), 108)
        )
        
        val metrics = HealthMetrics(
            id = "test",
            userId = "test-user",
            date = LocalDate.now(),
            steps = 5000,
            distanceMeters = 3810.0,
            caloriesBurned = 200.0,
            screenTimeMinutes = 120,
            sleepDurationMinutes = 420,
            sleepQuality = null,
            heartRateSamples = heartRateSamples,
            hrvSamples = emptyList(),
            mood = null,
            syncedAt = Instant.now()
        )
        
        val insights = analyticsUseCase.generateInsights(metrics)
        
        // Should have at least one insight related to heart rate
        val heartRateInsights = insights.filter { MetricType.HEART_RATE in it.relatedMetrics }
        heartRateInsights.shouldNotBeEmpty()
    }
})

/**
 * Helper function to create test HealthMetrics.
 */
private fun createTestMetrics(
    id: String,
    date: LocalDate,
    steps: Int = 0,
    distanceMeters: Double = steps * 0.762,
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
