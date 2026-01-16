package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.AvatarQueryInput
import com.healthtracker.domain.model.AvatarQueryResult
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.usecase.impl.AvatarUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based tests for Avatar Context Awareness (Property 10).
 * 
 * **Validates: Requirements 6.5**
 * 
 * Property 10: Avatar Context Awareness
 * For any user query about health data (steps, sleep, calories, etc.),
 * the AI_Avatar response SHALL contain specific values from the user's
 * actual metrics for the relevant time period.
 */
class AvatarContextAwarenessTest : FunSpec({
    
    val healthDataRepository = mockk<HealthDataRepository>()
    val avatarUseCase = AvatarUseCaseImpl(healthDataRepository)
    
    /**
     * Property 10.1: Steps query returns actual step count
     * 
     * For any valid step count in user metrics, when querying about steps,
     * the response SHALL contain the actual step value.
     */
    test("steps query returns actual step count from metrics") {
        checkAll(100, Arb.positiveInt(max = 50000)) { steps ->
            // Arrange
            val metrics = createTestMetrics(steps = steps)
            coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(metrics)
            coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(listOf(metrics))
            
            val input = AvatarQueryInput(
                query = "how many steps did I take today?",
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response text should contain the actual step count
            response.text shouldContain steps.toString()
            
            // Metrics reference should contain steps
            response.metrics.shouldNotBeNull()
            val stepsMetric = response.metrics!!.find { it.type == MetricType.STEPS }
            stepsMetric.shouldNotBeNull()
            stepsMetric.value.toInt() shouldBe steps
        }
    }
    
    /**
     * Property 10.2: Sleep query returns actual sleep duration
     * 
     * For any valid sleep duration in user metrics, when querying about sleep,
     * the response SHALL contain the actual sleep value.
     */
    test("sleep query returns actual sleep duration from metrics") {
        checkAll(100, Arb.int(min = 60, max = 720)) { sleepMinutes ->
            // Arrange
            val metrics = createTestMetrics(sleepDurationMinutes = sleepMinutes)
            coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(metrics)
            coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(listOf(metrics))
            
            val input = AvatarQueryInput(
                query = "how did I sleep last night?",
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response should contain sleep information
            val hours = sleepMinutes / 60
            val mins = sleepMinutes % 60
            
            // Either hours or the formatted time should appear
            (response.text.contains("${hours}h") || response.text.contains("$sleepMinutes")) shouldBe true
            
            // Metrics reference should contain sleep
            response.metrics.shouldNotBeNull()
            val sleepMetric = response.metrics!!.find { it.type == MetricType.SLEEP }
            sleepMetric.shouldNotBeNull()
            sleepMetric.value.toInt() shouldBe sleepMinutes
        }
    }
    
    /**
     * Property 10.3: Calories query returns actual calorie count
     * 
     * For any valid calorie count in user metrics, when querying about calories,
     * the response SHALL contain the actual calorie value.
     */
    test("calories query returns actual calorie count from metrics") {
        checkAll(100, Arb.int(min = 100, max = 5000)) { calories ->
            // Arrange
            val metrics = createTestMetrics(caloriesBurned = calories.toDouble())
            coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(metrics)
            coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(listOf(metrics))
            
            val input = AvatarQueryInput(
                query = "how many calories did I burn?",
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response text should contain the actual calorie count
            response.text shouldContain calories.toString()
            
            // Metrics reference should contain calories
            response.metrics.shouldNotBeNull()
            val caloriesMetric = response.metrics!!.find { it.type == MetricType.CALORIES }
            caloriesMetric.shouldNotBeNull()
            caloriesMetric.value.toInt() shouldBe calories
        }
    }
    
    /**
     * Property 10.4: Heart rate query returns actual heart rate
     * 
     * For any valid heart rate samples in user metrics, when querying about heart rate,
     * the response SHALL contain the actual average heart rate value.
     */
    test("heart rate query returns actual heart rate from metrics") {
        checkAll(100, Arb.int(min = 50, max = 150)) { avgHeartRate ->
            // Arrange
            val heartRateSamples = listOf(
                HeartRateSample(Instant.now().minusSeconds(3600), avgHeartRate - 5),
                HeartRateSample(Instant.now().minusSeconds(1800), avgHeartRate),
                HeartRateSample(Instant.now(), avgHeartRate + 5)
            )
            val metrics = createTestMetrics(heartRateSamples = heartRateSamples)
            coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(metrics)
            coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(listOf(metrics))
            
            val input = AvatarQueryInput(
                query = "what's my heart rate?",
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response text should contain heart rate information
            response.text shouldContain "bpm"
            
            // Metrics reference should contain heart rate
            response.metrics.shouldNotBeNull()
            val hrMetric = response.metrics!!.find { it.type == MetricType.HEART_RATE }
            hrMetric.shouldNotBeNull()
            // Average should be close to avgHeartRate
            hrMetric.value.toInt() shouldBe avgHeartRate
        }
    }
    
    /**
     * Property 10.5: Overall health query returns multiple actual metrics
     * 
     * For any user metrics, when querying about overall health,
     * the response SHALL contain actual values for multiple metric types.
     */
    test("overall health query returns multiple actual metrics") {
        checkAll(
            100,
            Arb.positiveInt(max = 30000),
            Arb.int(min = 100, max = 3000),
            Arb.int(min = 300, max = 600)
        ) { steps, calories, sleepMinutes ->
            // Arrange
            val metrics = createTestMetrics(
                steps = steps,
                caloriesBurned = calories.toDouble(),
                sleepDurationMinutes = sleepMinutes
            )
            coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(metrics)
            coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(listOf(metrics))
            
            val input = AvatarQueryInput(
                query = "show me my health summary",
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response should contain multiple metrics
            response.text shouldContain steps.toString()
            response.text shouldContain calories.toString()
            
            // Metrics reference should contain multiple types
            response.metrics.shouldNotBeNull()
            val metricTypes = response.metrics!!.map { it.type }
            metricTypes shouldContain MetricType.STEPS
            metricTypes shouldContain MetricType.CALORIES
            metricTypes shouldContain MetricType.SLEEP
        }
    }
    
    /**
     * Property 10.6: No data scenario returns appropriate message
     * 
     * When no metrics data is available, the response SHALL indicate
     * that data is not available rather than returning incorrect values.
     */
    test("no data scenario returns appropriate message without false values") {
        // Arrange
        coEvery { healthDataRepository.getHealthMetrics(any()) } returns flowOf(null)
        coEvery { healthDataRepository.getHealthMetricsRange(any(), any()) } returns flowOf(emptyList())
        
        val queries = listOf(
            "how many steps today?",
            "how did I sleep?",
            "what's my heart rate?"
        )
        
        queries.forEach { query ->
            val input = AvatarQueryInput(
                query = query,
                userId = "test_user",
                currentDate = LocalDate.now()
            )
            
            // Act
            val result = avatarUseCase.processQuery(input)
            
            // Assert
            result shouldBe AvatarQueryResult.Success::class
            val response = (result as AvatarQueryResult.Success).response
            
            // Response should indicate no data, not return fake values
            val noDataIndicators = listOf(
                "don't have",
                "no data",
                "not available",
                "enable",
                "tracking"
            )
            
            val hasNoDataIndicator = noDataIndicators.any { 
                response.text.lowercase().contains(it) 
            }
            
            // Either has no data indicator OR has null metrics
            (hasNoDataIndicator || response.metrics == null) shouldBe true
        }
    }
})

/**
 * Helper function to create test HealthMetrics.
 */
private fun createTestMetrics(
    steps: Int = 5000,
    caloriesBurned: Double = 300.0,
    sleepDurationMinutes: Int = 420,
    heartRateSamples: List<HeartRateSample> = emptyList()
): HealthMetrics {
    return HealthMetrics(
        id = UUID.randomUUID().toString(),
        userId = "test_user",
        date = LocalDate.now(),
        steps = steps,
        distanceMeters = steps * 0.75,
        caloriesBurned = caloriesBurned,
        screenTimeMinutes = 120,
        sleepDurationMinutes = sleepDurationMinutes,
        sleepQuality = SleepQuality.GOOD,
        heartRateSamples = heartRateSamples,
        hrvSamples = emptyList(),
        mood = Mood.HAPPY,
        syncedAt = Instant.now()
    )
}
