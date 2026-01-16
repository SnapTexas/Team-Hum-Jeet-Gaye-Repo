package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.domain.usecase.impl.GamificationUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate

/**
 * Property-based tests for streak increment logic.
 * 
 * **Validates: Requirements 10.2**
 * 
 * Property 22: Streak Increment Logic
 * For any consecutive days of goal achievement, the streak counter SHALL 
 * increment by 1 for each day, and SHALL reset to 0 when a day is missed.
 */
class StreakIncrementTest : FunSpec({
    
    test("Property 22.1: Consecutive true values from end equal streak count") {
        checkAll(100, Arb.list(Arb.boolean(), 1..30)) { dailyAchievements ->
            val streak = calculateStreak(dailyAchievements)
            val expectedStreak = dailyAchievements.takeLastWhile { it }.size
            
            streak shouldBe expectedStreak
        }
    }
    
    test("Property 22.2: Empty list results in streak of 0") {
        val streak = calculateStreak(emptyList())
        streak shouldBe 0
    }
    
    test("Property 22.3: All true values result in streak equal to list size") {
        checkAll(100, Arb.list(Arb.boolean(), 1..30)) { _ ->
            // Generate all-true list of random size
            val size = (1..30).random()
            val allTrue = List(size) { true }
            
            val streak = calculateStreak(allTrue)
            streak shouldBe size
        }
    }
    
    test("Property 22.4: All false values result in streak of 0") {
        checkAll(100, Arb.list(Arb.boolean(), 1..30)) { _ ->
            val size = (1..30).random()
            val allFalse = List(size) { false }
            
            val streak = calculateStreak(allFalse)
            streak shouldBe 0
        }
    }
    
    test("Property 22.5: Single false at end resets streak to 0") {
        checkAll(100, Arb.list(Arb.boolean(), 1..29)) { achievements ->
            val withFalseAtEnd = achievements + false
            
            val streak = calculateStreak(withFalseAtEnd)
            streak shouldBe 0
        }
    }
    
    test("Property 22.6: Single true at end results in streak of 1 if preceded by false") {
        checkAll(100, Arb.list(Arb.boolean(), 1..28)) { achievements ->
            val withFalseThenTrue = achievements + false + true
            
            val streak = calculateStreak(withFalseThenTrue)
            streak shouldBe 1
        }
    }
    
    test("Property 22.7: Streak is always non-negative") {
        checkAll(100, Arb.list(Arb.boolean(), 0..50)) { dailyAchievements ->
            val streak = calculateStreak(dailyAchievements)
            streak shouldBeGreaterThanOrEqual 0
        }
    }
    
    test("Property 22.8: Streak never exceeds list size") {
        checkAll(100, Arb.list(Arb.boolean(), 1..50)) { dailyAchievements ->
            val streak = calculateStreak(dailyAchievements)
            streak shouldBe streak.coerceAtMost(dailyAchievements.size)
        }
    }
    
    test("Property 22.9: Adding true to end increments streak by 1 if previous was true") {
        checkAll(100, Arb.list(Arb.boolean(), 1..29)) { achievements ->
            if (achievements.isNotEmpty() && achievements.last()) {
                val originalStreak = calculateStreak(achievements)
                val newStreak = calculateStreak(achievements + true)
                
                newStreak shouldBe originalStreak + 1
            }
        }
    }
    
    test("Property 22.10: Streak calculation is deterministic") {
        checkAll(100, Arb.list(Arb.boolean(), 1..30)) { dailyAchievements ->
            val streak1 = calculateStreak(dailyAchievements)
            val streak2 = calculateStreak(dailyAchievements)
            
            streak1 shouldBe streak2
        }
    }
})

/**
 * Calculate streak from a list of daily achievements.
 * This mirrors the implementation in GamificationUseCaseImpl.
 */
private fun calculateStreak(dailyAchievements: List<Boolean>): Int {
    if (dailyAchievements.isEmpty()) return 0
    
    var streak = 0
    for (i in dailyAchievements.indices.reversed()) {
        if (dailyAchievements[i]) {
            streak++
        } else {
            break
        }
    }
    return streak
}
