package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.Achievement
import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.BadgeType
import com.healthtracker.domain.model.DefaultGoals
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.PointsConfig
import com.healthtracker.domain.model.PredefinedBadges
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.domain.model.UserProgress
import com.healthtracker.domain.repository.GamificationRepository
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.usecase.GamificationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Implementation of GamificationUseCase.
 * Handles streak tracking, badge unlocking, and achievement checking.
 */
class GamificationUseCaseImpl @Inject constructor(
    private val gamificationRepository: GamificationRepository,
    private val healthDataRepository: HealthDataRepository
) : GamificationUseCase {
    
    override fun getProgress(): Flow<UserProgress?> {
        return gamificationRepository.getUserProgress()
    }
    
    override fun getStreaks(): Flow<List<Streak>> {
        return gamificationRepository.getStreaks()
    }
    
    override fun getBadges(): Flow<List<Badge>> {
        return gamificationRepository.getBadges()
    }
    
    override fun getLeaderboard(circleId: String?): Flow<List<LeaderboardEntry>> {
        return gamificationRepository.getLeaderboard(circleId)
    }
    
    override suspend fun checkAchievements(metrics: HealthMetrics): List<Achievement> {
        val achievements = mutableListOf<Achievement>()
        val unlockedBadges = gamificationRepository.getUnlockedBadges().firstOrNull() ?: emptyList()
        val unlockedIds = unlockedBadges.map { it.id }.toSet()
        
        // Check step-based badges
        checkStepBadges(metrics, unlockedIds, achievements)
        
        // Check streak-based badges
        checkStreakBadges(unlockedIds, achievements)
        
        return achievements
    }
    
    private suspend fun checkStepBadges(
        metrics: HealthMetrics,
        unlockedIds: Set<String>,
        achievements: MutableList<Achievement>
    ) {
        // Check single-day step achievement
        if (metrics.steps >= 10000 && PredefinedBadges.FIRST_STEPS.id !in unlockedIds) {
            val result = gamificationRepository.unlockBadge(PredefinedBadges.FIRST_STEPS.id)
            if (result is Result.Success) {
                achievements.add(result.data)
            }
        }
        
        // Check total steps milestones (would need historical data)
        // This is simplified - in production, we'd track cumulative steps
    }
    
    private suspend fun checkStreakBadges(
        unlockedIds: Set<String>,
        achievements: MutableList<Achievement>
    ) {
        val streaks = gamificationRepository.getStreaks().firstOrNull() ?: return
        
        for (streak in streaks) {
            // Check 7-day streak badge
            if (streak.currentCount >= 7 && PredefinedBadges.WEEK_WARRIOR.id !in unlockedIds) {
                val result = gamificationRepository.unlockBadge(PredefinedBadges.WEEK_WARRIOR.id)
                if (result is Result.Success) {
                    achievements.add(result.data)
                }
            }
            
            // Check 30-day streak badge
            if (streak.currentCount >= 30 && PredefinedBadges.MONTH_CHAMPION.id !in unlockedIds) {
                val result = gamificationRepository.unlockBadge(PredefinedBadges.MONTH_CHAMPION.id)
                if (result is Result.Success) {
                    achievements.add(result.data)
                }
            }
            
            // Check 100-day streak badge
            if (streak.currentCount >= 100 && PredefinedBadges.STREAK_LEGEND.id !in unlockedIds) {
                val result = gamificationRepository.unlockBadge(PredefinedBadges.STREAK_LEGEND.id)
                if (result is Result.Success) {
                    achievements.add(result.data)
                }
            }
        }
    }
    
    override suspend fun updateStreak(
        type: StreakType,
        achieved: Boolean,
        date: LocalDate
    ): Result<Streak> {
        return gamificationRepository.updateStreak(type, achieved, date)
    }

    
    override suspend fun processDailyMetrics(metrics: HealthMetrics): Result<List<Achievement>> {
        val achievements = mutableListOf<Achievement>()
        
        try {
            // Check step goal
            val stepGoalAchieved = metrics.steps >= DefaultGoals.DAILY_STEPS
            val stepStreakResult = updateStreak(StreakType.DAILY_STEPS, stepGoalAchieved, metrics.date)
            
            // Award points for achieving step goal
            if (stepGoalAchieved) {
                gamificationRepository.addPoints(PointsConfig.DAILY_STEP_GOAL, "Daily step goal achieved")
            }
            
            // Award streak bonus points
            if (stepStreakResult is Result.Success) {
                val streak = stepStreakResult.data
                if (streak.currentCount > 0 && streak.currentCount % 7 == 0) {
                    gamificationRepository.addPoints(PointsConfig.STREAK_WEEK, "7-day streak bonus")
                }
                if (streak.currentCount > 0 && streak.currentCount % 30 == 0) {
                    gamificationRepository.addPoints(PointsConfig.STREAK_MONTH, "30-day streak bonus")
                }
            }
            
            // Check sleep goal
            val sleepGoalAchieved = metrics.sleepDurationMinutes >= DefaultGoals.DAILY_SLEEP_MINUTES * 0.9
            if (sleepGoalAchieved) {
                gamificationRepository.addPoints(PointsConfig.SLEEP_GOAL_MET, "Sleep goal achieved")
            }
            
            // Check for new achievements
            val newAchievements = checkAchievements(metrics)
            achievements.addAll(newAchievements)
            
            return Result.Success(achievements)
        } catch (e: Exception) {
            return Result.Error(
                com.healthtracker.domain.model.AppException.StorageException(
                    message = "Failed to process daily metrics: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun getAtRiskStreaks(): List<Streak> {
        return gamificationRepository.getAtRiskStreaks()
    }
    
    override suspend fun checkStreakRiskNotifications(): List<StreakType> {
        val atRiskStreaks = getAtRiskStreaks()
        val currentHour = LocalTime.now().hour
        
        // Only notify if less than 4 hours remaining in the day (after 8 PM)
        return if (currentHour >= 20) {
            atRiskStreaks.map { it.type }
        } else {
            emptyList()
        }
    }
    
    override suspend fun awardPoints(points: Int, reason: String): Result<Int> {
        return gamificationRepository.addPoints(points, reason)
    }
    
    override suspend fun syncState(): Result<Unit> {
        return gamificationRepository.syncGamificationState()
    }
    
    /**
     * Calculate streak from a list of daily achievements.
     * The streak is the count of consecutive true values from the end of the list.
     * 
     * Property: For any list of daily achievements, the streak equals the count
     * of consecutive true values from the end. A false value resets the count to 0.
     * 
     * @param dailyAchievements List of booleans where true = goal achieved, false = missed
     * @return Current streak count (consecutive achievements from the end)
     */
    override fun calculateStreak(dailyAchievements: List<Boolean>): Int {
        if (dailyAchievements.isEmpty()) return 0
        
        var streak = 0
        // Count consecutive true values from the end
        for (i in dailyAchievements.indices.reversed()) {
            if (dailyAchievements[i]) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
    
    companion object {
        /**
         * Hours remaining threshold for streak risk notification.
         */
        const val STREAK_RISK_HOURS_THRESHOLD = 4
    }
}
