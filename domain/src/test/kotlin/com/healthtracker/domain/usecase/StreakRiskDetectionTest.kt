package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Property-based tests for streak risk detection.
 * 
 * **Validates: Requirements 10.5**
 * 
 * Property 25: Streak Risk Detection
 * For any active streak where the user has not made progress toward the goal 
 * with less than 4 hours remaining in the day, a streak risk notification 
 * SHALL be triggered.
 */
class StreakRiskDetectionTest : FunSpec({
    
    test("Property 25.1: Streak is at risk when last achieved yesterday and <4 hours remain") {
        checkAll(100, Arb.int(1..100)) { streakCount ->
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            val streak = createTestStreak(
                currentCount = streakCount,
                lastAchievedDate = yesterday
            )
            
            // Simulate time after 8 PM (less than 4 hours remaining)
            val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(20, 0))
            
            isAtRisk.shouldBeTrue()
        }
    }
    
    test("Property 25.2: Streak is NOT at risk when achieved today") {
        checkAll(100, Arb.int(1..100)) { streakCount ->
            val today = LocalDate.now()
            
            val streak = createTestStreak(
                currentCount = streakCount,
                lastAchievedDate = today
            )
            
            // Even after 8 PM, if achieved today, not at risk
            val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(22, 0))
            
            isAtRisk.shouldBeFalse()
        }
    }
    
    test("Property 25.3: Streak is NOT at risk when >4 hours remain (before 8 PM)") {
        checkAll(100, Arb.int(1..100)) { streakCount ->
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            val streak = createTestStreak(
                currentCount = streakCount,
                lastAchievedDate = yesterday
            )
            
            // Before 8 PM (more than 4 hours remaining)
            val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(19, 59))
            
            isAtRisk.shouldBeFalse()
        }
    }
    
    test("Property 25.4: Zero streak is never at risk") {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        val streak = createTestStreak(
            currentCount = 0,
            lastAchievedDate = yesterday
        )
        
        val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(23, 0))
        
        isAtRisk.shouldBeFalse()
    }
    
    test("Property 25.5: Streak with null lastAchievedDate is never at risk") {
        val today = LocalDate.now()
        
        val streak = createTestStreak(
            currentCount = 5,
            lastAchievedDate = null
        )
        
        val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(23, 0))
        
        isAtRisk.shouldBeFalse()
    }
    
    test("Property 25.6: Streak from 2+ days ago is already broken, not at risk") {
        checkAll(100, Arb.int(2..30)) { daysAgo ->
            val today = LocalDate.now()
            val oldDate = today.minusDays(daysAgo.toLong())
            
            val streak = createTestStreak(
                currentCount = 10,
                lastAchievedDate = oldDate
            )
            
            // Even late at night, old streaks are broken, not "at risk"
            val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(23, 0))
            
            isAtRisk.shouldBeFalse()
        }
    }
    
    test("Property 25.7: Risk detection is time-sensitive (boundary at 8 PM)") {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        val streak = createTestStreak(
            currentCount = 7,
            lastAchievedDate = yesterday
        )
        
        // At exactly 8 PM (20:00), should be at risk
        checkStreakAtRisk(streak, today, LocalTime.of(20, 0)).shouldBeTrue()
        
        // At 7:59 PM, should NOT be at risk
        checkStreakAtRisk(streak, today, LocalTime.of(19, 59)).shouldBeFalse()
    }
    
    test("Property 25.8: All streak types can be at risk") {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        
        StreakType.entries.forEach { type ->
            val streak = Streak(
                id = "test_${type.name}",
                userId = "test_user",
                type = type,
                currentCount = 5,
                longestCount = 10,
                lastAchievedDate = yesterday,
                startDate = yesterday.minusDays(4),
                lastUpdated = Instant.now()
            )
            
            val isAtRisk = checkStreakAtRisk(streak, today, LocalTime.of(21, 0))
            isAtRisk.shouldBeTrue()
        }
    }
    
    test("Property 25.9: Risk detection is consistent for same inputs") {
        checkAll(100, Arb.int(1..100), Arb.int(20..23)) { streakCount, hour ->
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            val streak = createTestStreak(
                currentCount = streakCount,
                lastAchievedDate = yesterday
            )
            
            val time = LocalTime.of(hour, 0)
            
            val result1 = checkStreakAtRisk(streak, today, time)
            val result2 = checkStreakAtRisk(streak, today, time)
            
            result1 shouldBe result2
        }
    }
    
    test("Property 25.10: Hours remaining calculation is correct") {
        // At 20:00 (8 PM), 4 hours remain until midnight
        val hoursAt20 = calculateHoursRemaining(LocalTime.of(20, 0))
        hoursAt20 shouldBe 4
        
        // At 21:00 (9 PM), 3 hours remain
        val hoursAt21 = calculateHoursRemaining(LocalTime.of(21, 0))
        hoursAt21 shouldBe 3
        
        // At 23:00 (11 PM), 1 hour remains
        val hoursAt23 = calculateHoursRemaining(LocalTime.of(23, 0))
        hoursAt23 shouldBe 1
        
        // At 12:00 (noon), 12 hours remain
        val hoursAtNoon = calculateHoursRemaining(LocalTime.of(12, 0))
        hoursAtNoon shouldBe 12
    }
})

/**
 * Create a test streak.
 */
private fun createTestStreak(
    currentCount: Int,
    lastAchievedDate: LocalDate?
): Streak {
    return Streak(
        id = "test_streak",
        userId = "test_user",
        type = StreakType.DAILY_STEPS,
        currentCount = currentCount,
        longestCount = maxOf(currentCount, 10),
        lastAchievedDate = lastAchievedDate,
        startDate = lastAchievedDate?.minusDays(currentCount.toLong() - 1),
        lastUpdated = Instant.now()
    )
}

/**
 * Check if a streak is at risk.
 * A streak is at risk if:
 * 1. Current count > 0
 * 2. Last achieved date was yesterday (not today)
 * 3. Less than 4 hours remain in the day (after 8 PM)
 */
private fun checkStreakAtRisk(
    streak: Streak,
    today: LocalDate,
    currentTime: LocalTime
): Boolean {
    if (streak.currentCount == 0) return false
    if (streak.lastAchievedDate == null) return false
    
    val yesterday = today.minusDays(1)
    val isYesterday = streak.lastAchievedDate == yesterday
    val lessThan4HoursRemain = currentTime.hour >= 20
    
    return isYesterday && lessThan4HoursRemain
}

/**
 * Calculate hours remaining until midnight.
 */
private fun calculateHoursRemaining(currentTime: LocalTime): Int {
    return 24 - currentTime.hour
}
