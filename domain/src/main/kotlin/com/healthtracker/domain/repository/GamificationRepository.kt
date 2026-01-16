package com.healthtracker.domain.repository

import com.healthtracker.domain.model.Achievement
import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.GamificationState
import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for gamification data operations.
 * Handles streaks, badges, points, and leaderboards.
 */
interface GamificationRepository {
    
    /**
     * Get the current user's progress including points, level, and goals.
     */
    fun getUserProgress(): Flow<UserProgress?>
    
    /**
     * Get all streaks for the current user.
     */
    fun getStreaks(): Flow<List<Streak>>
    
    /**
     * Get a specific streak by type.
     */
    fun getStreak(type: StreakType): Flow<Streak?>
    
    /**
     * Get all badges (both locked and unlocked).
     */
    fun getBadges(): Flow<List<Badge>>
    
    /**
     * Get only unlocked badges.
     */
    fun getUnlockedBadges(): Flow<List<Badge>>
    
    /**
     * Get leaderboard entries for a specific circle or global.
     * @param circleId Optional circle ID, null for global leaderboard
     * @param limit Maximum number of entries to return
     */
    fun getLeaderboard(circleId: String? = null, limit: Int = 10): Flow<List<LeaderboardEntry>>
    
    /**
     * Update a streak (increment or reset).
     * @param type The type of streak to update
     * @param achieved Whether the goal was achieved today
     * @param date The date for the streak update
     */
    suspend fun updateStreak(type: StreakType, achieved: Boolean, date: LocalDate = LocalDate.now()): Result<Streak>
    
    /**
     * Unlock a badge for the user.
     * @param badgeId The ID of the badge to unlock
     */
    suspend fun unlockBadge(badgeId: String): Result<Achievement>
    
    /**
     * Add points to the user's total.
     * @param points Number of points to add
     * @param reason Description of why points were awarded
     */
    suspend fun addPoints(points: Int, reason: String): Result<Int>
    
    /**
     * Save the complete gamification state (for sync).
     */
    suspend fun saveGamificationState(state: GamificationState): Result<Unit>
    
    /**
     * Get the gamification state for syncing.
     */
    suspend fun getGamificationState(): Result<GamificationState>
    
    /**
     * Sync gamification state with remote server.
     */
    suspend fun syncGamificationState(): Result<Unit>
    
    /**
     * Check if a streak is at risk (less than 4 hours remaining).
     * @param type The type of streak to check
     */
    suspend fun isStreakAtRisk(type: StreakType): Boolean
    
    /**
     * Get streaks that are at risk of being lost.
     */
    suspend fun getAtRiskStreaks(): List<Streak>
    
    /**
     * Reset all gamification data (for testing or account reset).
     */
    suspend fun resetGamificationData(): Result<Unit>
}
