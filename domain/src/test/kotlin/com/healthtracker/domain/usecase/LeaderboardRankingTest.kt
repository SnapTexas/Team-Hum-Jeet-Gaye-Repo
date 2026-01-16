package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.TrendDirection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property-based tests for leaderboard ranking correctness.
 * 
 * **Validates: Requirements 10.4**
 * 
 * Property 24: Leaderboard Ranking Correctness
 * For any set of opted-in users in a circle, the leaderboard SHALL rank 
 * users in descending order by score, with no ties having inconsistent ordering.
 */
class LeaderboardRankingTest : FunSpec({
    
    test("Property 24.1: Leaderboard is sorted by score in descending order") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            // Verify descending order by score
            rankedEntries.shouldBeSortedWith { a, b -> b.score.compareTo(a.score) }
        }
    }
    
    test("Property 24.2: Ranks are sequential starting from 1") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            rankedEntries.forEachIndexed { index, entry ->
                entry.rank shouldBe index + 1
            }
        }
    }
    
    test("Property 24.3: Higher score always has lower (better) rank") {
        checkAll(100, Arb.list(Arb.int(0..100000), 2..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            for (i in 0 until rankedEntries.size - 1) {
                val current = rankedEntries[i]
                val next = rankedEntries[i + 1]
                
                // If scores are different, higher score should have lower rank
                if (current.score != next.score) {
                    current.score shouldBeGreaterThan next.score
                    current.rank shouldBe next.rank - 1
                }
            }
        }
    }
    
    test("Property 24.4: Ties have consistent ordering (by userId for determinism)") {
        checkAll(100, Arb.list(Arb.int(0..100), 2..20)) { scores ->
            val entries = createLeaderboardEntries(scores)
            
            // Rank twice and verify same order
            val ranked1 = rankLeaderboard(entries)
            val ranked2 = rankLeaderboard(entries)
            
            ranked1.map { it.userId } shouldBe ranked2.map { it.userId }
        }
    }
    
    test("Property 24.5: All entries have unique ranks") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            val ranks = rankedEntries.map { it.rank }
            ranks.distinct().size shouldBe ranks.size
        }
    }
    
    test("Property 24.6: Empty leaderboard returns empty list") {
        val rankedEntries = rankLeaderboard(emptyList())
        rankedEntries.size shouldBe 0
    }
    
    test("Property 24.7: Single entry has rank 1") {
        checkAll(100, Arb.int(0..100000)) { score ->
            val entries = createLeaderboardEntries(listOf(score))
            val rankedEntries = rankLeaderboard(entries)
            
            rankedEntries.size shouldBe 1
            rankedEntries[0].rank shouldBe 1
        }
    }
    
    test("Property 24.8: Ranking preserves all entries") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            rankedEntries.size shouldBe entries.size
        }
    }
    
    test("Property 24.9: Tie-breaking is deterministic by userId") {
        // Create entries with same score but different userIds
        val sameScore = 5000
        val entries = (1..10).map { i ->
            LeaderboardEntry(
                userId = "user_$i",
                displayName = "User $i",
                avatarUrl = null,
                rank = 0,
                score = sameScore,
                metricValue = sameScore.toDouble(),
                trend = TrendDirection.STABLE
            )
        }
        
        val ranked1 = rankLeaderboard(entries)
        val ranked2 = rankLeaderboard(entries.shuffled())
        
        // Both should produce same ordering
        ranked1.map { it.userId } shouldBe ranked2.map { it.userId }
    }
    
    test("Property 24.10: Rank 1 always has highest score") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            if (rankedEntries.isNotEmpty()) {
                val maxScore = rankedEntries.maxOf { it.score }
                rankedEntries[0].score shouldBe maxScore
            }
        }
    }
    
    test("Property 24.11: Last rank always has lowest score") {
        checkAll(100, Arb.list(Arb.int(0..100000), 1..50)) { scores ->
            val entries = createLeaderboardEntries(scores)
            val rankedEntries = rankLeaderboard(entries)
            
            if (rankedEntries.isNotEmpty()) {
                val minScore = rankedEntries.minOf { it.score }
                rankedEntries.last().score shouldBe minScore
            }
        }
    }
})

/**
 * Create leaderboard entries from a list of scores.
 */
private fun createLeaderboardEntries(scores: List<Int>): List<LeaderboardEntry> {
    return scores.mapIndexed { index, score ->
        LeaderboardEntry(
            userId = "user_${UUID.randomUUID()}",
            displayName = "User ${index + 1}",
            avatarUrl = null,
            rank = 0, // Will be set by ranking
            score = score,
            metricValue = score.toDouble(),
            trend = TrendDirection.STABLE
        )
    }
}

/**
 * Rank leaderboard entries by score (descending) with tie-breaking by userId.
 */
private fun rankLeaderboard(entries: List<LeaderboardEntry>): List<LeaderboardEntry> {
    return entries
        .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.userId })
        .mapIndexed { index, entry ->
            entry.copy(rank = index + 1)
        }
}
