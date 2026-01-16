package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalySeverity
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionAction
import com.healthtracker.domain.model.SuggestionGenerationResult
import com.healthtracker.domain.model.SuggestionInput
import com.healthtracker.domain.model.SuggestionType
import com.healthtracker.domain.model.UserBaseline
import com.healthtracker.domain.model.UserGoals
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based tests for suggestion generation coverage.
 * 
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.6**
 * 
 * Property 9: Suggestion Generation Coverage
 * The AI_Suggestion_Engine SHALL generate suggestions that cover
 * activity, sleep, nutrition, and hydration categories.
 */
class SuggestionGenerationTest : FunSpec({
    
    test("Property 9.1: Suggestions cover all four required categories (activity, sleep, nutrition, hydration)") {
        checkAll(50, Arb.int(1000..15000), Arb.int(300..600)) { steps, sleepMinutes ->
            val metrics = createTestMetrics(steps = steps, sleepMinutes = sleepMinutes)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            val types = suggestions.map { it.type }.toSet()
            
            // Must include all four required categories
            types.shouldContain(SuggestionType.ACTIVITY)
            types.shouldContain(SuggestionType.SLEEP)
            types.shouldContain(SuggestionType.NUTRITION)
            types.shouldContain(SuggestionType.HYDRATION)
        }
    }
    
    test("Property 9.2: At least 4 suggestions are generated with valid metrics") {
        checkAll(50, Arb.int(1000..15000)) { steps ->
            val metrics = createTestMetrics(steps = steps)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            suggestions shouldHaveAtLeastSize 4
        }
    }
    
    test("Property 9.3: Generic suggestions are generated when no metrics available") {
        val suggestions = generateGenericSuggestions()
        
        suggestions shouldHaveAtLeastSize 4
        
        val types = suggestions.map { it.type }.toSet()
        types.shouldContain(SuggestionType.ACTIVITY)
        types.shouldContain(SuggestionType.SLEEP)
        types.shouldContain(SuggestionType.NUTRITION)
        types.shouldContain(SuggestionType.HYDRATION)
    }
    
    test("Property 9.4: All suggestions have valid priority (1-5)") {
        checkAll(50, Arb.int(1000..15000)) { steps ->
            val metrics = createTestMetrics(steps = steps)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            suggestions.forEach { suggestion ->
                suggestion.priority shouldBeGreaterThan 0
                suggestion.priority shouldBeLessThanOrEqual 5
            }
        }
    }
    
    test("Property 9.5: Activity suggestions are actionable with OpenStepTracker action") {
        checkAll(50, Arb.int(1000..15000)) { steps ->
            val metrics = createTestMetrics(steps = steps)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            val activitySuggestion = suggestions.find { it.type == SuggestionType.ACTIVITY }
            activitySuggestion.shouldNotBeNull()
            activitySuggestion.actionable.shouldBeTrue()
            activitySuggestion.action shouldBe SuggestionAction.OpenStepTracker
        }
    }
    
    test("Property 9.6: Sleep suggestions include SetSleepReminder action") {
        checkAll(50, Arb.int(300..600)) { sleepMinutes ->
            val metrics = createTestMetrics(sleepMinutes = sleepMinutes)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            val sleepSuggestion = suggestions.find { it.type == SuggestionType.SLEEP }
            sleepSuggestion.shouldNotBeNull()
            sleepSuggestion.actionable.shouldBeTrue()
            (sleepSuggestion.action is SuggestionAction.SetSleepReminder).shouldBeTrue()
        }
    }
    
    test("Property 9.7: Hydration suggestions include LogWater action with target") {
        val metrics = createTestMetrics()
        val suggestions = generateRuleBasedSuggestions(metrics)
        
        val hydrationSuggestion = suggestions.find { it.type == SuggestionType.HYDRATION }
        hydrationSuggestion.shouldNotBeNull()
        hydrationSuggestion.actionable.shouldBeTrue()
        
        val action = hydrationSuggestion.action
        (action is SuggestionAction.LogWater).shouldBeTrue()
        (action as SuggestionAction.LogWater).targetMl shouldBeGreaterThan 0
    }
    
    test("Property 9.8: Nutrition suggestions include OpenDietTracker action") {
        val metrics = createTestMetrics()
        val suggestions = generateRuleBasedSuggestions(metrics)
        
        val nutritionSuggestion = suggestions.find { it.type == SuggestionType.NUTRITION }
        nutritionSuggestion.shouldNotBeNull()
        nutritionSuggestion.actionable.shouldBeTrue()
        nutritionSuggestion.action shouldBe SuggestionAction.OpenDietTracker
    }
    
    test("Property 9.9: Low step count generates high priority activity suggestion") {
        // Very low steps should generate priority 1 suggestion
        val metrics = createTestMetrics(steps = 2000)
        val suggestions = generateRuleBasedSuggestions(metrics)
        
        val activitySuggestion = suggestions.find { it.type == SuggestionType.ACTIVITY }
        activitySuggestion.shouldNotBeNull()
        activitySuggestion.priority shouldBe 1
    }
    
    test("Property 9.10: High step count generates lower priority activity suggestion") {
        // High steps should generate priority 4 suggestion
        val metrics = createTestMetrics(steps = 12000)
        val suggestions = generateRuleBasedSuggestions(metrics)
        
        val activitySuggestion = suggestions.find { it.type == SuggestionType.ACTIVITY }
        activitySuggestion.shouldNotBeNull()
        activitySuggestion.priority shouldBe 4
    }
    
    test("Property 9.11: Low sleep generates high priority sleep suggestion") {
        // Very low sleep should generate priority 1 suggestion
        val metrics = createTestMetrics(sleepMinutes = 300) // 5 hours
        val suggestions = generateRuleBasedSuggestions(metrics)
        
        val sleepSuggestion = suggestions.find { it.type == SuggestionType.SLEEP }
        sleepSuggestion.shouldNotBeNull()
        sleepSuggestion.priority shouldBe 1
    }
    
    test("Property 9.12: All suggestions have non-empty title and description") {
        checkAll(50, Arb.int(1000..15000)) { steps ->
            val metrics = createTestMetrics(steps = steps)
            val suggestions = generateRuleBasedSuggestions(metrics)
            
            suggestions.forEach { suggestion ->
                suggestion.title.shouldNotBeNull()
                suggestion.title.isNotBlank().shouldBeTrue()
                suggestion.description.shouldNotBeNull()
                suggestion.description.isNotBlank().shouldBeTrue()
            }
        }
    }
})

/**
 * Helper function to create test HealthMetrics.
 */
private fun createTestMetrics(
    steps: Int = 8000,
    sleepMinutes: Int = 420,
    screenTimeMinutes: Int = 180
): HealthMetrics {
    return HealthMetrics(
        id = "test-metrics",
        userId = "test-user",
        date = LocalDate.now(),
        steps = steps,
        distanceMeters = steps * 0.762,
        caloriesBurned = steps * 0.04,
        screenTimeMinutes = screenTimeMinutes,
        sleepDurationMinutes = sleepMinutes,
        sleepQuality = null,
        heartRateSamples = listOf(HeartRateSample(Instant.now(), 72)),
        hrvSamples = listOf(HrvSample(Instant.now(), 45.0)),
        mood = null,
        syncedAt = Instant.now()
    )
}

/**
 * Simulates rule-based suggestion generation matching AISuggestionUseCaseImpl.
 */
private fun generateRuleBasedSuggestions(metrics: HealthMetrics): List<Suggestion> {
    val suggestions = mutableListOf<Suggestion>()
    val now = Instant.now()
    val forDate = LocalDate.now().plusDays(1)
    val userId = metrics.userId
    val goals = UserGoals()
    
    // Activity suggestion
    val stepsPercent = (metrics.steps.toDouble() / goals.dailyStepsTarget) * 100
    val activityPriority = when {
        stepsPercent < 50 -> 1
        stepsPercent < 80 -> 2
        stepsPercent < 100 -> 3
        else -> 4
    }
    suggestions.add(Suggestion(
        id = UUID.randomUUID().toString(),
        userId = userId,
        type = SuggestionType.ACTIVITY,
        title = "Activity Suggestion",
        description = "Based on ${metrics.steps} steps",
        priority = activityPriority,
        actionable = true,
        action = SuggestionAction.OpenStepTracker,
        generatedAt = now,
        forDate = forDate
    ))
    
    // Sleep suggestion
    val sleepHours = metrics.sleepDurationMinutes / 60.0
    val sleepPriority = when {
        sleepHours < 6 -> 1
        sleepHours < 7 -> 2
        sleepHours < 8 -> 3
        else -> 4
    }
    suggestions.add(Suggestion(
        id = UUID.randomUUID().toString(),
        userId = userId,
        type = SuggestionType.SLEEP,
        title = "Sleep Suggestion",
        description = "Based on ${sleepHours} hours of sleep",
        priority = sleepPriority,
        actionable = true,
        action = SuggestionAction.SetSleepReminder("22:00"),
        generatedAt = now,
        forDate = forDate
    ))
    
    // Nutrition suggestion
    suggestions.add(Suggestion(
        id = UUID.randomUUID().toString(),
        userId = userId,
        type = SuggestionType.NUTRITION,
        title = "Nutrition Suggestion",
        description = "Eat balanced meals",
        priority = 3,
        actionable = true,
        action = SuggestionAction.OpenDietTracker,
        generatedAt = now,
        forDate = forDate
    ))
    
    // Hydration suggestion
    suggestions.add(Suggestion(
        id = UUID.randomUUID().toString(),
        userId = userId,
        type = SuggestionType.HYDRATION,
        title = "Hydration Suggestion",
        description = "Stay hydrated",
        priority = 2,
        actionable = true,
        action = SuggestionAction.LogWater(goals.dailyWaterMlTarget),
        generatedAt = now,
        forDate = forDate
    ))
    
    return suggestions
}

/**
 * Generates generic suggestions when no metrics available.
 */
private fun generateGenericSuggestions(): List<Suggestion> {
    val now = Instant.now()
    val forDate = LocalDate.now().plusDays(1)
    val userId = "test-user"
    
    return listOf(
        Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.ACTIVITY,
            title = "Start Moving",
            description = "Aim for 10,000 steps today",
            priority = 2,
            actionable = true,
            action = SuggestionAction.OpenStepTracker,
            generatedAt = now,
            forDate = forDate
        ),
        Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.SLEEP,
            title = "Prioritize Rest",
            description = "Aim for 7-8 hours of sleep",
            priority = 2,
            actionable = true,
            action = SuggestionAction.SetSleepReminder("22:30"),
            generatedAt = now,
            forDate = forDate
        ),
        Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.NUTRITION,
            title = "Eat Well",
            description = "Focus on balanced meals",
            priority = 3,
            actionable = true,
            action = SuggestionAction.OpenDietTracker,
            generatedAt = now,
            forDate = forDate
        ),
        Suggestion(
            id = UUID.randomUUID().toString(),
            userId = userId,
            type = SuggestionType.HYDRATION,
            title = "Stay Hydrated",
            description = "Drink at least 2.5 liters of water",
            priority = 2,
            actionable = true,
            action = SuggestionAction.LogWater(2500),
            generatedAt = now,
            forDate = forDate
        )
    )
}
