package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Achievement
import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Use case interface for gamification operations.
 * Handles streak tracking, badge unlocking, and achievement checking.
 */
interface GamificationUseCase {
    
    /**
     * Get the current user's progress.
     */
    fun getProgress(): Flow<UserProgress?>
    
    /**
     * Get all streaks for the current user.
     */
    fun getStreaks(): Flow<List<Streak>>
    
    /**
     * Get all badges with their unlock status.
     */
    fun getBadges(): Flow<List<Badge>>
    
    /**
     * Get leaderboard entries.
     * @param circleId Optional circle ID for circle-specific leaderboard
     */
    fun getLeaderboard(circleId: String? = null): Flow<List<LeaderboardEntry>>
    
    /**
     * Check and unlock any achievements based on current metrics.
     * @param metrics Current health metrics to evaluate
     * @return List of newly unlocked achievements
     */
    suspend fun checkAchievements(metrics: HealthMetrics): List<Achievement>
    
    /**
     * Update streak based on daily goal achievement.
     * @param type Type of streak to update
     * @param achieved Whether the goal was achieved
     * @param date Date of the achievement (defaults to today)
     */
    suspend fun updateStreak(
        type: StreakType, 
        achieved: Boolean, 
        date: LocalDate = LocalDate.now()
    ): Result<Streak>
    
    /**
     * Process daily metrics and update all relevant gamification data.
     * This includes:
     * - Updating streaks based on goal completion
     * - Checking for new badge unlocks
     * - Awarding points
     * @param metrics The daily health metrics
     */
    suspend fun processDailyMetrics(metrics: HealthMetrics): Result<List<Achievement>>
    
    /**
     * Get streaks that are at risk of being lost.
     * A streak is at risk if less than 4 hours remain in the day
     * and the goal hasn't been achieved yet.
     */
    suspend fun getAtRiskStreaks(): List<Streak>
    
    /**
     * Check if any streaks need risk notifications.
     * @return List of streak types that need notifications
     */
    suspend fun checkStreakRiskNotifications(): List<StreakType>
    
    /**
     * Award points for completing an action.
     * @param points Number of points to award
     * @param reason Description of the action
     */
    suspend fun awardPoints(points: Int, reason: String): Result<Int>
    
    /**
     * Sync gamification state across devices.
     */
    suspend fun syncState(): Result<Unit>
    
    /**
     * Calculate streak from a list of daily achievements.
     * Used for property testing and streak calculation.
     * @param dailyAchievements List of booleans indicating if goal was achieved each day
     * @return The current streak count
     */
    fun calculateStreak(dailyAchievements: List<Boolean>): Int
}
