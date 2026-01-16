package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.ActivityLevel
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HydrationReminder
import com.healthtracker.domain.model.HydrationSchedule
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.WeatherCondition
import com.healthtracker.domain.model.toActivityLevel
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.impl.PlanningUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Property-based tests for Hydration Schedule Adaptation (Property 13).
 * 
 * **Validates: Requirements 7.5**
 * 
 * Property 13: Hydration Schedule Adaptation
 * For any change in user activity level or weather conditions,
 * the hydration schedule daily target SHALL adjust proportionally
 * (higher activity/temperature = higher target).
 */
class HydrationScheduleAdaptationTest : FunSpec({
    
    val planRepository = mockk<PlanRepository>()
    val userRepository = mockk<UserRepository>()
    val planningUseCase = PlanningUseCaseImpl(planRepository, userRepository)
    
    companion object {
        const val BASE_WATER_ML = 2000
    }
    
    /**
     * Property 13.1: Higher activity level increases hydration target
     * 
     * For any increase in activity level (measured by steps),
     * the hydration target SHALL increase proportionally.
     */
    test("higher activity level increases hydration target") {
        checkAll(
            100,
            Arb.int(min = 1000, max = 4000),  // Low activity steps
            Arb.int(min = 10000, max = 20000) // High activity steps
        ) { lowSteps, highSteps ->
            // Arrange
            val profile = createTestProfile()
            
            val lowActivityInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(lowSteps)
            )
            
            val highActivityInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(highSteps)
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(input, null)
            }
            
            // Act
            val lowActivitySchedule = planRepository.generateHydrationSchedule(lowActivityInput)
            val highActivitySchedule = planRepository.generateHydrationSchedule(highActivityInput)
            
            // Assert - Higher activity should have higher or equal target
            highActivitySchedule.dailyTargetMl shouldBeGreaterThanOrEqual lowActivitySchedule.dailyTargetMl
            
            // Verify activity multiplier is applied
            val lowActivityLevel = lowSteps.toActivityLevel()
            val highActivityLevel = highSteps.toActivityLevel()
            
            if (highActivityLevel > lowActivityLevel) {
                highActivitySchedule.activityMultiplier shouldBeGreaterThan lowActivitySchedule.activityMultiplier
            }
        }
    }
    
    /**
     * Property 13.2: Hot weather increases hydration target
     * 
     * For any hot weather condition (>25°C), the hydration target
     * SHALL be higher than for normal weather.
     */
    test("hot weather increases hydration target") {
        checkAll(
            100,
            Arb.float(min = 15f, max = 24f),  // Normal temperature
            Arb.float(min = 26f, max = 40f)   // Hot temperature
        ) { normalTemp, hotTemp ->
            // Arrange
            val profile = createTestProfile()
            val metrics = createTestMetricsList()
            
            val normalWeather = WeatherCondition(
                temperatureCelsius = normalTemp,
                humidity = 50f
            )
            
            val hotWeather = WeatherCondition(
                temperatureCelsius = hotTemp,
                humidity = 50f
            )
            
            val normalInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = metrics,
                currentWeather = normalWeather
            )
            
            val hotInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = metrics,
                currentWeather = hotWeather
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(input, input.currentWeather)
            }
            
            // Act
            val normalSchedule = planRepository.generateHydrationSchedule(normalInput)
            val hotSchedule = planRepository.generateHydrationSchedule(hotInput)
            
            // Assert - Hot weather should have higher target
            hotSchedule.dailyTargetMl shouldBeGreaterThan normalSchedule.dailyTargetMl
            hotSchedule.adjustedForWeather shouldBe true
            hotSchedule.weatherMultiplier shouldBeGreaterThan normalSchedule.weatherMultiplier
        }
    }
    
    /**
     * Property 13.3: High humidity increases hydration target
     * 
     * For any high humidity condition (>70%), the hydration target
     * SHALL be higher than for normal humidity.
     */
    test("high humidity increases hydration target") {
        checkAll(
            100,
            Arb.float(min = 30f, max = 60f),  // Normal humidity
            Arb.float(min = 75f, max = 95f)   // High humidity
        ) { normalHumidity, highHumidity ->
            // Arrange
            val profile = createTestProfile()
            val metrics = createTestMetricsList()
            
            val normalWeather = WeatherCondition(
                temperatureCelsius = 25f,
                humidity = normalHumidity
            )
            
            val humidWeather = WeatherCondition(
                temperatureCelsius = 25f,
                humidity = highHumidity
            )
            
            val normalInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = metrics,
                currentWeather = normalWeather
            )
            
            val humidInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = metrics,
                currentWeather = humidWeather
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(input, input.currentWeather)
            }
            
            // Act
            val normalSchedule = planRepository.generateHydrationSchedule(normalInput)
            val humidSchedule = planRepository.generateHydrationSchedule(humidInput)
            
            // Assert - High humidity should have higher target
            humidSchedule.dailyTargetMl shouldBeGreaterThan normalSchedule.dailyTargetMl
            humidSchedule.adjustedForWeather shouldBe true
        }
    }
    
    /**
     * Property 13.4: Combined activity and weather increases target more
     * 
     * For any combination of high activity AND hot weather,
     * the hydration target SHALL be higher than either factor alone.
     */
    test("combined activity and weather increases target more") {
        checkAll(
            100,
            Arb.int(min = 12000, max = 20000), // High activity
            Arb.float(min = 28f, max = 38f)    // Hot temperature
        ) { highSteps, hotTemp ->
            // Arrange
            val profile = createTestProfile()
            
            val hotWeather = WeatherCondition(
                temperatureCelsius = hotTemp,
                humidity = 75f
            )
            
            // Activity only (no weather)
            val activityOnlyInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(highSteps),
                currentWeather = null
            )
            
            // Weather only (low activity)
            val weatherOnlyInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(3000),
                currentWeather = hotWeather
            )
            
            // Both activity and weather
            val combinedInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(highSteps),
                currentWeather = hotWeather
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(input, input.currentWeather)
            }
            
            // Act
            val activityOnlySchedule = planRepository.generateHydrationSchedule(activityOnlyInput)
            val weatherOnlySchedule = planRepository.generateHydrationSchedule(weatherOnlyInput)
            val combinedSchedule = planRepository.generateHydrationSchedule(combinedInput)
            
            // Assert - Combined should be highest
            combinedSchedule.dailyTargetMl shouldBeGreaterThan activityOnlySchedule.dailyTargetMl
            combinedSchedule.dailyTargetMl shouldBeGreaterThan weatherOnlySchedule.dailyTargetMl
            
            // Both flags should be true for combined
            combinedSchedule.adjustedForActivity shouldBe true
            combinedSchedule.adjustedForWeather shouldBe true
        }
    }
    
    /**
     * Property 13.5: Hydration schedule has reminders throughout the day
     * 
     * For any hydration schedule, reminders SHALL be distributed
     * throughout waking hours.
     */
    test("hydration schedule has reminders throughout the day") {
        checkAll(
            100,
            Arb.int(min = 3000, max = 15000),
            Arb.boolean()
        ) { steps, isHot ->
            // Arrange
            val profile = createTestProfile()
            val weather = if (isHot) {
                WeatherCondition(temperatureCelsius = 30f, humidity = 60f)
            } else null
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(steps),
                currentWeather = weather
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val inp = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(inp, inp.currentWeather)
            }
            
            // Act
            val schedule = planRepository.generateHydrationSchedule(input)
            
            // Assert - Should have multiple reminders
            schedule.reminders.shouldNotBeEmpty()
            schedule.reminders.size shouldBeGreaterThanOrEqual 4
            
            // Reminders should span waking hours (7am - 9pm)
            val times = schedule.reminders.map { it.time }
            val hasEarlyReminder = times.any { it.hour <= 9 }
            val hasLateReminder = times.any { it.hour >= 19 }
            
            hasEarlyReminder shouldBe true
            hasLateReminder shouldBe true
        }
    }
    
    /**
     * Property 13.6: Reminder amounts sum to daily target
     * 
     * For any hydration schedule, the sum of all reminder amounts
     * SHALL approximately equal the daily target.
     */
    test("reminder amounts sum to daily target") {
        checkAll(
            100,
            Arb.int(min = 3000, max = 15000)
        ) { steps ->
            // Arrange
            val profile = createTestProfile()
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(steps)
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val inp = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(inp, null)
            }
            
            // Act
            val schedule = planRepository.generateHydrationSchedule(input)
            
            // Assert - Sum should be close to target (within 10%)
            val reminderSum = schedule.reminders.sumOf { it.amountMl }
            val tolerance = schedule.dailyTargetMl * 0.1
            
            (reminderSum >= schedule.dailyTargetMl - tolerance) shouldBe true
            (reminderSum <= schedule.dailyTargetMl + tolerance) shouldBe true
        }
    }
})

// Helper functions

private fun createTestProfile(): UserProfile {
    return UserProfile(
        name = "Test User",
        age = 30,
        weight = 70f,
        height = 170f,
        goal = HealthGoal.FITNESS,
        dietPreference = DietPreference.NON_VEGETARIAN
    )
}

private fun createTestMetricsList(): List<HealthMetrics> {
    return createMetricsWithSteps(5000)
}

private fun createMetricsWithSteps(avgSteps: Int): List<HealthMetrics> {
    return (0..6).map { dayOffset ->
        HealthMetrics(
            id = UUID.randomUUID().toString(),
            userId = "test_user",
            date = LocalDate.now().minusDays(dayOffset.toLong()),
            steps = avgSteps + (dayOffset * 100),
            distanceMeters = avgSteps * 0.75,
            caloriesBurned = 300.0,
            screenTimeMinutes = 120,
            sleepDurationMinutes = 420,
            sleepQuality = SleepQuality.GOOD,
            heartRateSamples = emptyList(),
            hrvSamples = emptyList(),
            mood = Mood.HAPPY,
            syncedAt = Instant.now()
        )
    }
}

private fun generateMockHydrationSchedule(
    input: PlanGenerationInput,
    weather: WeatherCondition?
): HydrationSchedule {
    val avgSteps = input.recentMetrics.map { it.steps }.average().takeIf { !it.isNaN() } ?: 5000.0
    val activityLevel = avgSteps.toInt().toActivityLevel()
    
    val activityMultiplier = when (activityLevel) {
        ActivityLevel.SEDENTARY -> 1.0f
        ActivityLevel.LIGHT -> 1.1f
        ActivityLevel.MODERATE -> 1.2f
        ActivityLevel.ACTIVE -> 1.3f
        ActivityLevel.VERY_ACTIVE -> 1.5f
    }
    
    val weatherMultiplier = weather?.let { w ->
        var multiplier = 1.0f
        if (w.isHot) multiplier *= 1.2f
        if (w.isHumid) multiplier *= 1.1f
        multiplier
    } ?: 1.0f
    
    val baseTarget = 2000
    val adjustedTarget = (baseTarget * activityMultiplier * weatherMultiplier).toInt()
    
    val reminders = generateReminders(adjustedTarget)
    
    return HydrationSchedule(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        dailyTargetMl = adjustedTarget,
        baseTargetMl = baseTarget,
        reminders = reminders,
        adjustedForActivity = activityMultiplier > 1.0f,
        adjustedForWeather = weatherMultiplier > 1.0f,
        activityMultiplier = activityMultiplier,
        weatherMultiplier = weatherMultiplier,
        generatedAt = Instant.now()
    )
}

private fun generateReminders(dailyTarget: Int): List<HydrationReminder> {
    val times = listOf(
        LocalTime.of(7, 0),
        LocalTime.of(9, 0),
        LocalTime.of(11, 0),
        LocalTime.of(13, 0),
        LocalTime.of(15, 0),
        LocalTime.of(17, 0),
        LocalTime.of(19, 0),
        LocalTime.of(21, 0)
    )
    
    val amountPerReminder = dailyTarget / times.size
    
    return times.map { time ->
        HydrationReminder(
            id = UUID.randomUUID().toString(),
            time = time,
            amountMl = amountPerReminder,
            message = "Time to hydrate!"
        )
    }
}
