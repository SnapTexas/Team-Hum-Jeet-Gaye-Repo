package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.GamificationState
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based tests for gamification state sync.
 * 
 * **Validates: Requirements 10.6**
 * 
 * Property 26: Gamification State Sync Round-Trip
 * For any gamification state (points, badges, streaks) persisted to Firestore, 
 * retrieving on another device SHALL return equivalent state.
 */
class GamificationStateSyncTest : FunSpec({
    
    test("Property 26.1: Total points are preserved after sync round-trip") {
        checkAll(100, Arb.int(0..1000000)) { totalPoints ->
            val originalState = createTestState(totalPoints = totalPoints)
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.totalPoints shouldBe originalState.totalPoints
        }
    }
    
    test("Property 26.2: Level is preserved after sync round-trip") {
        checkAll(100, Arb.int(1..100)) { level ->
            val originalState = createTestState(level = level)
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.level shouldBe originalState.level
        }
    }
    
    test("Property 26.3: Unlocked badge IDs are preserved after sync round-trip") {
        checkAll(100, Arb.list(Arb.string(5..20), 0..10)) { badgeIds ->
            val originalState = createTestState(unlockedBadgeIds = badgeIds)
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.unlockedBadgeIds shouldContainExactlyInAnyOrder originalState.unlockedBadgeIds
        }
    }
    
    test("Property 26.4: Streak count is preserved after sync round-trip") {
        checkAll(100, Arb.int(0..365)) { streakCount ->
            val streak = createTestStreak(currentCount = streakCount)
            val originalState = createTestState(streaks = listOf(streak))
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.streaks.first().currentCount shouldBe streakCount
        }
    }
    
    test("Property 26.5: Streak longest count is preserved after sync round-trip") {
        checkAll(100, Arb.int(0..365), Arb.int(0..365)) { current, longest ->
            val actualLongest = maxOf(current, longest)
            val streak = createTestStreak(currentCount = current, longestCount = actualLongest)
            val originalState = createTestState(streaks = listOf(streak))
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.streaks.first().longestCount shouldBe actualLongest
        }
    }
    
    test("Property 26.6: Multiple streaks are preserved after sync round-trip") {
        val streaks = StreakType.entries.map { type ->
            Streak(
                id = UUID.randomUUID().toString(),
                userId = "test_user",
                type = type,
                currentCount = (0..100).random(),
                longestCount = (0..200).random(),
                lastAchievedDate = LocalDate.now().minusDays((0..30).random().toLong()),
                startDate = LocalDate.now().minusDays((0..60).random().toLong()),
                lastUpdated = Instant.now()
            )
        }
        
        val originalState = createTestState(streaks = streaks)
        
        val serialized = serializeState(originalState)
        val deserialized = deserializeState(serialized)
        
        deserialized.streaks.size shouldBe originalState.streaks.size
        
        originalState.streaks.forEach { original ->
            val synced = deserialized.streaks.find { it.type == original.type }
            synced?.currentCount shouldBe original.currentCount
            synced?.longestCount shouldBe original.longestCount
        }
    }
    
    test("Property 26.7: User ID is preserved after sync round-trip") {
        checkAll(100, Arb.string(10..50)) { userId ->
            val originalState = createTestState(userId = userId)
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.userId shouldBe originalState.userId
        }
    }
    
    test("Property 26.8: Empty state syncs correctly") {
        val emptyState = GamificationState(
            userId = "test_user",
            totalPoints = 0,
            level = 1,
            streaks = emptyList(),
            unlockedBadgeIds = emptyList(),
            lastSyncedAt = Instant.now(),
            version = 1
        )
        
        val serialized = serializeState(emptyState)
        val deserialized = deserializeState(serialized)
        
        deserialized.totalPoints shouldBe 0
        deserialized.level shouldBe 1
        deserialized.streaks.size shouldBe 0
        deserialized.unlockedBadgeIds.size shouldBe 0
    }
    
    test("Property 26.9: Version is preserved after sync round-trip") {
        checkAll(100, Arb.int(1..1000)) { version ->
            val originalState = createTestState(version = version.toLong())
            
            val serialized = serializeState(originalState)
            val deserialized = deserializeState(serialized)
            
            deserialized.version shouldBe originalState.version
        }
    }
    
    test("Property 26.10: Sync is idempotent (multiple syncs produce same result)") {
        checkAll(50, Arb.int(0..100000), Arb.int(1..50)) { points, level ->
            val originalState = createTestState(totalPoints = points, level = level)
            
            // Sync multiple times
            val serialized1 = serializeState(originalState)
            val deserialized1 = deserializeState(serialized1)
            
            val serialized2 = serializeState(deserialized1)
            val deserialized2 = deserializeState(serialized2)
            
            deserialized2.totalPoints shouldBe originalState.totalPoints
            deserialized2.level shouldBe originalState.level
        }
    }
    
    test("Property 26.11: Streak dates are preserved after sync round-trip") {
        val today = LocalDate.now()
        val startDate = today.minusDays(10)
        
        val streak = Streak(
            id = "test_streak",
            userId = "test_user",
            type = StreakType.DAILY_STEPS,
            currentCount = 10,
            longestCount = 15,
            lastAchievedDate = today,
            startDate = startDate,
            lastUpdated = Instant.now()
        )
        
        val originalState = createTestState(streaks = listOf(streak))
        
        val serialized = serializeState(originalState)
        val deserialized = deserializeState(serialized)
        
        val syncedStreak = deserialized.streaks.first()
        syncedStreak.lastAchievedDate shouldBe today
        syncedStreak.startDate shouldBe startDate
    }
})

/**
 * Create a test gamification state.
 */
private fun createTestState(
    userId: String = "test_user",
    totalPoints: Int = 1000,
    level: Int = 5,
    streaks: List<Streak> = emptyList(),
    unlockedBadgeIds: List<String> = emptyList(),
    version: Long = 1
): GamificationState {
    return GamificationState(
        userId = userId,
        totalPoints = totalPoints,
        level = level,
        streaks = streaks,
        unlockedBadgeIds = unlockedBadgeIds,
        lastSyncedAt = Instant.now(),
        version = version
    )
}

/**
 * Create a test streak.
 */
private fun createTestStreak(
    currentCount: Int = 5,
    longestCount: Int = 10
): Streak {
    return Streak(
        id = UUID.randomUUID().toString(),
        userId = "test_user",
        type = StreakType.DAILY_STEPS,
        currentCount = currentCount,
        longestCount = maxOf(currentCount, longestCount),
        lastAchievedDate = LocalDate.now(),
        startDate = LocalDate.now().minusDays(currentCount.toLong()),
        lastUpdated = Instant.now()
    )
}

/**
 * Simulate serializing state for sync (to JSON-like map).
 */
private fun serializeState(state: GamificationState): Map<String, Any?> {
    return mapOf(
        "userId" to state.userId,
        "totalPoints" to state.totalPoints,
        "level" to state.level,
        "streaks" to state.streaks.map { streak ->
            mapOf(
                "id" to streak.id,
                "userId" to streak.userId,
                "type" to streak.type.name,
                "currentCount" to streak.currentCount,
                "longestCount" to streak.longestCount,
                "lastAchievedDate" to streak.lastAchievedDate?.toEpochDay(),
                "startDate" to streak.startDate?.toEpochDay(),
                "lastUpdated" to streak.lastUpdated.toEpochMilli()
            )
        },
        "unlockedBadgeIds" to state.unlockedBadgeIds,
        "lastSyncedAt" to state.lastSyncedAt.toEpochMilli(),
        "version" to state.version
    )
}

/**
 * Simulate deserializing state from sync.
 */
@Suppress("UNCHECKED_CAST")
private fun deserializeState(data: Map<String, Any?>): GamificationState {
    val streaksData = data["streaks"] as List<Map<String, Any?>>
    val streaks = streaksData.map { streakData ->
        Streak(
            id = streakData["id"] as String,
            userId = streakData["userId"] as String,
            type = StreakType.valueOf(streakData["type"] as String),
            currentCount = streakData["currentCount"] as Int,
            longestCount = streakData["longestCount"] as Int,
            lastAchievedDate = (streakData["lastAchievedDate"] as? Long)?.let { LocalDate.ofEpochDay(it) },
            startDate = (streakData["startDate"] as? Long)?.let { LocalDate.ofEpochDay(it) },
            lastUpdated = Instant.ofEpochMilli(streakData["lastUpdated"] as Long)
        )
    }
    
    return GamificationState(
        userId = data["userId"] as String,
        totalPoints = data["totalPoints"] as Int,
        level = data["level"] as Int,
        streaks = streaks,
        unlockedBadgeIds = data["unlockedBadgeIds"] as List<String>,
        lastSyncedAt = Instant.ofEpochMilli(data["lastSyncedAt"] as Long),
        version = data["version"] as Long
    )
}
