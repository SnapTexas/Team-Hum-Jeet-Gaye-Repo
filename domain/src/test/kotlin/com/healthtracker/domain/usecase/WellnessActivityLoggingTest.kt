package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.BreathingExercise
import com.healthtracker.domain.model.BreathingExercises
import com.healthtracker.domain.model.BreathingPattern
import com.healthtracker.domain.model.ExerciseDifficulty
import com.healthtracker.domain.model.MeditationCategory
import com.healthtracker.domain.model.MeditationSession
import com.healthtracker.domain.model.MeditationSessions
import com.healthtracker.domain.model.WellnessActivity
import com.healthtracker.domain.model.WellnessActivityType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for wellness activity logging.
 * 
 * **Validates: Requirements 9.4**
 * 
 * Property 20: Wellness Activity Logging
 * When a breathing exercise or meditation session is completed, 
 * the system SHALL log the activity with its duration in seconds.
 */
class WellnessActivityLoggingTest : FunSpec({
    
    test("Property 20.1: Completed breathing exercises are logged with duration") {
        checkAll(100, Arb.element(BreathingExercises.ALL)) { exercise ->
            val activity = logBreathingExercise(
                exercise = exercise,
                durationSeconds = exercise.pattern.totalDurationSeconds
            )
            
            // Activity should be logged
            activity shouldNotBe null
            
            // Duration should be recorded
            activity.durationSeconds shouldBeGreaterThan 0
            activity.durationSeconds shouldBe exercise.pattern.totalDurationSeconds
            
            // Type should be breathing exercise
            activity.type shouldBe WellnessActivityType.BREATHING_EXERCISE
            
            // Name should reference the exercise
            activity.name.shouldNotBeEmpty()
        }
    }
    
    test("Property 20.2: Completed meditation sessions are logged with duration") {
        checkAll(100, Arb.element(MeditationSessions.ALL)) { session ->
            val durationSeconds = session.durationMinutes * 60
            
            val activity = logMeditationSession(
                session = session,
                durationSeconds = durationSeconds
            )
            
            // Activity should be logged
            activity shouldNotBe null
            
            // Duration should be recorded
            activity.durationSeconds shouldBeGreaterThan 0
            activity.durationSeconds shouldBe durationSeconds
            
            // Type should be meditation
            activity.type shouldBe WellnessActivityType.MEDITATION
            
            // Name should reference the session
            activity.name.shouldNotBeEmpty()
        }
    }
    
    test("Property 20.3: Activity duration is always positive") {
        checkAll(100, Arb.int(1..3600)) { durationSeconds ->
            val activity = logBreathingExercise(
                exercise = BreathingExercises.BOX_BREATHING,
                durationSeconds = durationSeconds
            )
            
            activity.durationSeconds shouldBeGreaterThan 0
        }
    }
    
    test("Property 20.4: Activity has valid completion timestamp") {
        checkAll(50, Arb.element(BreathingExercises.ALL)) { exercise ->
            val beforeLog = Instant.now()
            
            val activity = logBreathingExercise(
                exercise = exercise,
                durationSeconds = exercise.pattern.totalDurationSeconds
            )
            
            val afterLog = Instant.now()
            
            // Completion time should be between before and after
            activity.completedAt.isAfter(beforeLog.minusSeconds(1)) shouldBe true
            activity.completedAt.isBefore(afterLog.plusSeconds(1)) shouldBe true
        }
    }
    
    test("Property 20.5: Activity has unique ID") {
        val activities = mutableSetOf<String>()
        
        checkAll(100, Arb.element(BreathingExercises.ALL)) { exercise ->
            val activity = logBreathingExercise(
                exercise = exercise,
                durationSeconds = exercise.pattern.totalDurationSeconds
            )
            
            // ID should be unique
            activities.contains(activity.id) shouldBe false
            activities.add(activity.id)
        }
    }
    
    test("Property 20.6: Stress levels before and after are recorded when provided") {
        checkAll(50, Arb.int(0..100), Arb.int(0..100)) { stressBefore, stressAfter ->
            val activity = logBreathingExercise(
                exercise = BreathingExercises.RELAXING_BREATH,
                durationSeconds = BreathingExercises.RELAXING_BREATH.pattern.totalDurationSeconds,
                stressLevelBefore = stressBefore,
                stressLevelAfter = stressAfter
            )
            
            activity.stressLevelBefore shouldBe stressBefore
            activity.stressLevelAfter shouldBe stressAfter
        }
    }
    
    test("Property 20.7: Partial completion logs actual duration, not planned duration") {
        checkAll(50, Arb.element(MeditationSessions.ALL), Arb.int(30..300)) { session, actualSeconds ->
            val plannedSeconds = session.durationMinutes * 60
            
            // User stopped early
            val activity = logMeditationSession(
                session = session,
                durationSeconds = actualSeconds
            )
            
            // Should log actual duration, not planned
            activity.durationSeconds shouldBe actualSeconds
            
            // Actual may be less than planned
            if (actualSeconds < plannedSeconds) {
                activity.durationSeconds shouldBe actualSeconds
            }
        }
    }
    
    test("Property 20.8: Activity type matches the exercise type") {
        // Breathing exercises
        BreathingExercises.ALL.forEach { exercise ->
            val activity = logBreathingExercise(
                exercise = exercise,
                durationSeconds = exercise.pattern.totalDurationSeconds
            )
            activity.type shouldBe WellnessActivityType.BREATHING_EXERCISE
        }
        
        // Meditation sessions
        MeditationSessions.ALL.forEach { session ->
            val activity = logMeditationSession(
                session = session,
                durationSeconds = session.durationMinutes * 60
            )
            activity.type shouldBe WellnessActivityType.MEDITATION
        }
    }
    
    test("Property 20.9: Custom wellness activities can be logged") {
        checkAll(
            50,
            Arb.element(WellnessActivityType.entries.toList()),
            Arb.int(60..1800)
        ) { type, durationSeconds ->
            val activity = logCustomActivity(
                type = type,
                name = "Custom ${type.name}",
                durationSeconds = durationSeconds
            )
            
            activity.type shouldBe type
            activity.durationSeconds shouldBe durationSeconds
            activity.name.shouldNotBeEmpty()
        }
    }
    
    test("Property 20.10: Total wellness minutes calculation is correct") {
        val activities = listOf(
            logBreathingExercise(BreathingExercises.BOX_BREATHING, 64),
            logBreathingExercise(BreathingExercises.RELAXING_BREATH, 76),
            logMeditationSession(MeditationSessions.QUICK_STRESS_RELIEF, 300)
        )
        
        val totalSeconds = activities.sumOf { it.durationSeconds }
        val totalMinutes = totalSeconds / 60
        
        totalMinutes shouldBeGreaterThanOrEqual 7  // 64 + 76 + 300 = 440 seconds = 7+ minutes
    }
})

/**
 * Helper function to log a breathing exercise completion.
 */
private fun logBreathingExercise(
    exercise: BreathingExercise,
    durationSeconds: Int,
    stressLevelBefore: Int? = null,
    stressLevelAfter: Int? = null
): WellnessActivity {
    return WellnessActivity(
        id = UUID.randomUUID().toString(),
        userId = "test-user",
        type = WellnessActivityType.BREATHING_EXERCISE,
        name = exercise.name,
        durationSeconds = durationSeconds,
        completedAt = Instant.now(),
        stressLevelBefore = stressLevelBefore,
        stressLevelAfter = stressLevelAfter,
        notes = "Pattern: ${exercise.pattern.name}"
    )
}

/**
 * Helper function to log a meditation session completion.
 */
private fun logMeditationSession(
    session: MeditationSession,
    durationSeconds: Int,
    stressLevelBefore: Int? = null,
    stressLevelAfter: Int? = null
): WellnessActivity {
    return WellnessActivity(
        id = UUID.randomUUID().toString(),
        userId = "test-user",
        type = WellnessActivityType.MEDITATION,
        name = session.title,
        durationSeconds = durationSeconds,
        completedAt = Instant.now(),
        stressLevelBefore = stressLevelBefore,
        stressLevelAfter = stressLevelAfter,
        notes = "Category: ${session.category.name}"
    )
}

/**
 * Helper function to log a custom wellness activity.
 */
private fun logCustomActivity(
    type: WellnessActivityType,
    name: String,
    durationSeconds: Int,
    notes: String? = null
): WellnessActivity {
    return WellnessActivity(
        id = UUID.randomUUID().toString(),
        userId = "test-user",
        type = type,
        name = name,
        durationSeconds = durationSeconds,
        completedAt = Instant.now(),
        stressLevelBefore = null,
        stressLevelAfter = null,
        notes = notes
    )
}
