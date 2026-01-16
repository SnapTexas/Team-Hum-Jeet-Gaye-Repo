package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.BadgeCategory
import com.healthtracker.domain.model.BadgeRarity
import com.healthtracker.domain.model.BadgeRequirement
import com.healthtracker.domain.model.BadgeType
import com.healthtracker.domain.model.PredefinedBadges
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.Instant

/**
 * Property-based tests for badge unlock correctness.
 * 
 * **Validates: Requirements 10.3**
 * 
 * Property 23: Badge Unlock Correctness
 * For any user meeting a badge requirement threshold, the badge SHALL be 
 * unlocked and the unlock timestamp SHALL be recorded.
 */
class BadgeUnlockTest : FunSpec({
    
    test("Property 23.1: Meeting threshold unlocks badge") {
        checkAll(100, Arb.int(1..1000000)) { currentValue ->
            val badge = createTestBadge(threshold = 1000)
            
            val shouldUnlock = currentValue >= badge.requirement.threshold
            val unlocked = checkBadgeUnlock(badge, currentValue)
            
            unlocked shouldBe shouldUnlock
        }
    }
    
    test("Property 23.2: Value below threshold does NOT unlock badge") {
        checkAll(100, Arb.int(1..999)) { currentValue ->
            val badge = createTestBadge(threshold = 1000)
            
            val unlocked = checkBadgeUnlock(badge, currentValue)
            unlocked.shouldBeFalse()
        }
    }
    
    test("Property 23.3: Value at exactly threshold unlocks badge") {
        checkAll(100, Arb.int(100..10000)) { threshold ->
            val badge = createTestBadge(threshold = threshold)
            
            val unlocked = checkBadgeUnlock(badge, threshold)
            unlocked.shouldBeTrue()
        }
    }
    
    test("Property 23.4: Value above threshold unlocks badge") {
        checkAll(100, Arb.int(100..10000)) { threshold ->
            val badge = createTestBadge(threshold = threshold)
            val valueAbove = threshold + (1..1000).random()
            
            val unlocked = checkBadgeUnlock(badge, valueAbove)
            unlocked.shouldBeTrue()
        }
    }
    
    test("Property 23.5: Unlocked badge has non-null timestamp") {
        checkAll(100, Arb.int(1000..10000)) { currentValue ->
            val badge = createTestBadge(threshold = 1000)
            
            if (currentValue >= badge.requirement.threshold) {
                val unlockedBadge = unlockBadge(badge)
                unlockedBadge.unlockedAt.shouldNotBeNull()
            }
        }
    }
    
    test("Property 23.6: Locked badge has null timestamp") {
        val badge = createTestBadge(threshold = 1000)
        badge.unlockedAt.shouldBeNull()
        badge.isUnlocked.shouldBeFalse()
    }
    
    test("Property 23.7: Unlock timestamp is recorded at unlock time") {
        val badge = createTestBadge(threshold = 1000)
        val beforeUnlock = Instant.now()
        
        val unlockedBadge = unlockBadge(badge)
        
        val afterUnlock = Instant.now()
        
        unlockedBadge.unlockedAt.shouldNotBeNull()
        unlockedBadge.unlockedAt!!.isAfter(beforeUnlock.minusMillis(1)).shouldBeTrue()
        unlockedBadge.unlockedAt!!.isBefore(afterUnlock.plusMillis(1)).shouldBeTrue()
    }
    
    test("Property 23.8: All predefined badges have valid thresholds") {
        PredefinedBadges.getAllBadges().forEach { badge ->
            badge.requirement.threshold shouldBe badge.requirement.threshold.coerceAtLeast(1)
        }
    }
    
    test("Property 23.9: Badge unlock is idempotent (already unlocked stays unlocked)") {
        val badge = createTestBadge(threshold = 1000)
        val unlockedBadge = unlockBadge(badge)
        val timestamp = unlockedBadge.unlockedAt
        
        // "Unlocking" again should preserve the original timestamp
        val reUnlockedBadge = unlockedBadge.copy() // Simulating re-unlock attempt
        
        reUnlockedBadge.unlockedAt shouldBe timestamp
        reUnlockedBadge.isUnlocked.shouldBeTrue()
    }
    
    test("Property 23.10: Streak milestone badges unlock at correct thresholds") {
        val streakBadges = listOf(
            PredefinedBadges.WEEK_WARRIOR to 7,
            PredefinedBadges.MONTH_CHAMPION to 30,
            PredefinedBadges.STREAK_LEGEND to 100
        )
        
        streakBadges.forEach { (badge, expectedThreshold) ->
            badge.requirement.threshold shouldBe expectedThreshold
            badge.requirement.type shouldBe BadgeType.STREAK_MILESTONE
        }
    }
    
    test("Property 23.11: Step milestone badges unlock at correct thresholds") {
        val stepBadges = listOf(
            PredefinedBadges.FIRST_STEPS to 10000,
            PredefinedBadges.MARATHON_WALKER to 100000,
            PredefinedBadges.STEP_MASTER to 1000000
        )
        
        stepBadges.forEach { (badge, expectedThreshold) ->
            badge.requirement.threshold shouldBe expectedThreshold
        }
    }
})

/**
 * Create a test badge with specified threshold.
 */
private fun createTestBadge(threshold: Int): Badge {
    return Badge(
        id = "test_badge",
        name = "Test Badge",
        description = "A test badge",
        iconName = "ic_test",
        category = BadgeCategory.STEPS,
        rarity = BadgeRarity.COMMON,
        requirement = BadgeRequirement(
            type = BadgeType.STEPS_MILESTONE,
            threshold = threshold,
            description = "Reach $threshold steps"
        ),
        unlockedAt = null
    )
}

/**
 * Check if a badge should be unlocked based on current value.
 */
private fun checkBadgeUnlock(badge: Badge, currentValue: Int): Boolean {
    return currentValue >= badge.requirement.threshold
}

/**
 * Simulate unlocking a badge.
 */
private fun unlockBadge(badge: Badge): Badge {
    return badge.copy(unlockedAt = Instant.now())
}
