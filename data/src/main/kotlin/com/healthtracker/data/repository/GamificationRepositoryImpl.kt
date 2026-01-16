package com.healthtracker.data.repository

import com.healthtracker.data.local.dao.GamificationDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.local.entity.PointsHistoryEntity
import com.healthtracker.data.local.entity.StreakEntity
import com.healthtracker.data.local.entity.UnlockedBadgeEntity
import com.healthtracker.data.local.entity.UserProgressEntity
import com.healthtracker.domain.model.Achievement
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.GamificationState
import com.healthtracker.domain.model.GoalProgress
import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.PointsConfig
import com.healthtracker.domain.model.PredefinedBadges
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.domain.model.TrendDirection
import com.healthtracker.domain.model.UserProgress
import com.healthtracker.domain.repository.GamificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of GamificationRepository.
 * Handles local storage and sync of gamification data.
 */
@Singleton
class GamificationRepositoryImpl @Inject constructor(
    private val gamificationDao: GamificationDao,
    private val userDao: UserDao
) : GamificationRepository {
    
    override fun getUserProgress(): Flow<UserProgress?> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                gamificationDao.getUserProgressFlow(user.id).map { progressEntity ->
                    progressEntity?.toDomain(user.id)
                }
            }
        }.map { flow ->
            flow?.let { 
                var result: UserProgress? = null
                it.collect { progress -> result = progress }
                result
            }
        }
    }
    
    override fun getStreaks(): Flow<List<Streak>> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                gamificationDao.getStreaksFlow(user.id).map { entities ->
                    entities.map { it.toDomain() }
                }
            } ?: flowOf(emptyList())
        }.map { flow ->
            var result: List<Streak> = emptyList()
            flow.collect { streaks -> result = streaks }
            result
        }
    }
    
    override fun getStreak(type: StreakType): Flow<Streak?> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                gamificationDao.getStreakFlow(user.id, type.name).map { it?.toDomain() }
            } ?: flowOf(null)
        }.map { flow ->
            var result: Streak? = null
            flow.collect { streak -> result = streak }
            result
        }
    }
    
    override fun getBadges(): Flow<List<Badge>> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                gamificationDao.getUnlockedBadgesFlow(user.id).map { unlockedEntities ->
                    val unlockedIds = unlockedEntities.map { it.badgeId }.toSet()
                    PredefinedBadges.getAllBadges().map { badge ->
                        if (badge.id in unlockedIds) {
                            val unlocked = unlockedEntities.find { it.badgeId == badge.id }
                            badge.copy(unlockedAt = unlocked?.let { Instant.ofEpochMilli(it.unlockedAt) })
                        } else {
                            badge
                        }
                    }
                }
            } ?: flowOf(PredefinedBadges.getAllBadges())
        }.map { flow ->
            var result: List<Badge> = PredefinedBadges.getAllBadges()
            flow.collect { badges -> result = badges }
            result
        }
    }
    
    override fun getUnlockedBadges(): Flow<List<Badge>> {
        return getBadges().map { badges ->
            badges.filter { it.isUnlocked }
        }
    }

    
    override fun getLeaderboard(circleId: String?, limit: Int): Flow<List<LeaderboardEntry>> {
        // For now, return empty list - will be implemented with social features
        // In production, this would query Firebase for circle members' scores
        return flowOf(emptyList())
    }
    
    override suspend fun updateStreak(
        type: StreakType,
        achieved: Boolean,
        date: LocalDate
    ): Result<Streak> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )
            
            val existingStreak = gamificationDao.getStreak(user.id, type.name)
            val epochDay = date.toEpochDay()
            val now = System.currentTimeMillis()
            
            val updatedStreak = if (existingStreak == null) {
                // Create new streak
                StreakEntity(
                    id = UUID.randomUUID().toString(),
                    userId = user.id,
                    type = type.name,
                    currentCount = if (achieved) 1 else 0,
                    longestCount = if (achieved) 1 else 0,
                    lastAchievedDate = if (achieved) epochDay else null,
                    startDate = if (achieved) epochDay else null,
                    lastUpdated = now,
                    needsSync = true
                )
            } else {
                val lastDate = existingStreak.lastAchievedDate?.let { LocalDate.ofEpochDay(it) }
                val isConsecutive = lastDate != null && 
                    (lastDate == date.minusDays(1) || lastDate == date)
                
                if (achieved) {
                    if (isConsecutive && lastDate != date) {
                        // Increment streak
                        val newCount = existingStreak.currentCount + 1
                        existingStreak.copy(
                            currentCount = newCount,
                            longestCount = maxOf(existingStreak.longestCount, newCount),
                            lastAchievedDate = epochDay,
                            lastUpdated = now,
                            needsSync = true
                        )
                    } else if (lastDate == date) {
                        // Already achieved today, no change
                        existingStreak
                    } else {
                        // Start new streak (gap in days)
                        existingStreak.copy(
                            currentCount = 1,
                            lastAchievedDate = epochDay,
                            startDate = epochDay,
                            lastUpdated = now,
                            needsSync = true
                        )
                    }
                } else {
                    // Reset streak if not achieved and it's a new day
                    if (lastDate != date) {
                        existingStreak.copy(
                            currentCount = 0,
                            startDate = null,
                            lastUpdated = now,
                            needsSync = true
                        )
                    } else {
                        existingStreak
                    }
                }
            }
            
            gamificationDao.insertStreak(updatedStreak)
            Timber.d("Streak updated: ${type.name}, count: ${updatedStreak.currentCount}")
            
            Result.Success(updatedStreak.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Failed to update streak")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to update streak: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun unlockBadge(badgeId: String): Result<Achievement> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )
            
            // Check if already unlocked
            if (gamificationDao.isBadgeUnlocked(user.id, badgeId)) {
                return@withContext Result.Error(
                    AppException.ValidationException("badge", "Badge already unlocked")
                )
            }
            
            val badge = PredefinedBadges.getBadgeById(badgeId) ?: return@withContext Result.Error(
                AppException.ValidationException("badgeId", "Badge not found")
            )
            
            val now = System.currentTimeMillis()
            val pointsAwarded = PointsConfig.getPointsForBadge(badge.rarity)
            
            // Create unlocked badge entry
            val unlockedBadge = UnlockedBadgeEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                badgeId = badgeId,
                unlockedAt = now,
                pointsAwarded = pointsAwarded,
                needsSync = true
            )
            
            gamificationDao.insertUnlockedBadge(unlockedBadge)
            
            // Award points
            addPoints(pointsAwarded, "Badge unlocked: ${badge.name}")
            
            Timber.d("Badge unlocked: ${badge.name}")
            
            val achievement = Achievement(
                id = unlockedBadge.id,
                badge = badge.copy(unlockedAt = Instant.ofEpochMilli(now)),
                unlockedAt = Instant.ofEpochMilli(now),
                pointsAwarded = pointsAwarded
            )
            
            Result.Success(achievement)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unlock badge")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to unlock badge: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun addPoints(points: Int, reason: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )
            
            val now = System.currentTimeMillis()
            
            // Ensure user progress exists
            var progress = gamificationDao.getUserProgress(user.id)
            if (progress == null) {
                progress = UserProgressEntity(
                    id = UUID.randomUUID().toString(),
                    userId = user.id,
                    totalPoints = 0,
                    level = 1,
                    lastUpdated = now,
                    needsSync = true
                )
                gamificationDao.insertUserProgress(progress)
            }
            
            // Add points
            gamificationDao.addPoints(user.id, points, now)
            
            // Record in history
            val historyEntry = PointsHistoryEntity(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                points = points,
                reason = reason,
                timestamp = now,
                needsSync = true
            )
            gamificationDao.insertPointsHistory(historyEntry)
            
            // Check for level up
            val newTotal = (gamificationDao.getTotalPoints(user.id) ?: 0)
            val newLevel = UserProgress.calculateLevel(newTotal)
            if (newLevel > progress.level) {
                gamificationDao.updateLevel(user.id, newLevel, now)
                Timber.d("Level up! New level: $newLevel")
            }
            
            Timber.d("Points added: $points for $reason. Total: $newTotal")
            
            Result.Success(newTotal)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add points")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to add points: ${e.message}",
                    cause = e
                )
            )
        }
    }

    
    override suspend fun saveGamificationState(state: GamificationState): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                
                // Save progress
                val progressEntity = UserProgressEntity(
                    id = UUID.randomUUID().toString(),
                    userId = state.userId,
                    totalPoints = state.totalPoints,
                    level = state.level,
                    lastUpdated = now,
                    needsSync = false
                )
                gamificationDao.insertUserProgress(progressEntity)
                
                // Save streaks
                state.streaks.forEach { streak ->
                    val streakEntity = StreakEntity(
                        id = streak.id,
                        userId = streak.userId,
                        type = streak.type.name,
                        currentCount = streak.currentCount,
                        longestCount = streak.longestCount,
                        lastAchievedDate = streak.lastAchievedDate?.toEpochDay(),
                        startDate = streak.startDate?.toEpochDay(),
                        lastUpdated = now,
                        needsSync = false
                    )
                    gamificationDao.insertStreak(streakEntity)
                }
                
                Timber.d("Gamification state saved")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save gamification state")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to save gamification state: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun getGamificationState(): Result<GamificationState> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )
                
                val progress = gamificationDao.getUserProgress(user.id)
                val streaks = gamificationDao.getStreaks(user.id)
                val unlockedBadges = gamificationDao.getUnlockedBadges(user.id)
                
                val state = GamificationState(
                    userId = user.id,
                    totalPoints = progress?.totalPoints ?: 0,
                    level = progress?.level ?: 1,
                    streaks = streaks.map { it.toDomain() },
                    unlockedBadgeIds = unlockedBadges.map { it.badgeId },
                    lastSyncedAt = Instant.now(),
                    version = 1
                )
                
                Result.Success(state)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get gamification state")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to get gamification state: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun syncGamificationState(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )
            
            // Get items needing sync
            val progressToSync = gamificationDao.getProgressNeedingSync()
            val streaksToSync = gamificationDao.getStreaksNeedingSync()
            val badgesToSync = gamificationDao.getBadgesNeedingSync()
            
            // TODO: Sync to Firebase
            // For now, just mark as synced
            
            gamificationDao.markProgressAsSynced(user.id)
            gamificationDao.markStreaksAsSynced(user.id)
            gamificationDao.markBadgesAsSynced(user.id)
            
            Timber.d("Gamification state synced")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync gamification state")
            Result.Error(
                AppException.NetworkException(
                    message = "Failed to sync gamification state: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun isStreakAtRisk(type: StreakType): Boolean = withContext(Dispatchers.IO) {
        val user = userDao.getUser() ?: return@withContext false
        val streak = gamificationDao.getStreak(user.id, type.name) ?: return@withContext false
        
        if (streak.currentCount == 0) return@withContext false
        
        val lastDate = streak.lastAchievedDate?.let { LocalDate.ofEpochDay(it) }
        val today = LocalDate.now()
        val currentHour = LocalTime.now().hour
        
        // Streak is at risk if:
        // 1. Last achievement was yesterday (not today)
        // 2. Less than 4 hours remaining in the day (after 8 PM)
        lastDate == today.minusDays(1) && currentHour >= 20
    }
    
    override suspend fun getAtRiskStreaks(): List<Streak> = withContext(Dispatchers.IO) {
        val user = userDao.getUser() ?: return@withContext emptyList()
        val activeStreaks = gamificationDao.getActiveStreaks(user.id)
        
        val today = LocalDate.now()
        val currentHour = LocalTime.now().hour
        
        activeStreaks.filter { streak ->
            val lastDate = streak.lastAchievedDate?.let { LocalDate.ofEpochDay(it) }
            // At risk if last achievement was yesterday and it's after 8 PM
            lastDate == today.minusDays(1) && currentHour >= 20
        }.map { it.toDomain() }
    }
    
    override suspend fun resetGamificationData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )
            
            gamificationDao.deleteUserProgress(user.id)
            gamificationDao.deleteStreaks(user.id)
            gamificationDao.deleteUnlockedBadges(user.id)
            gamificationDao.deletePointsHistory(user.id)
            
            Timber.d("Gamification data reset")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset gamification data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to reset gamification data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    // Extension functions for entity conversion
    
    private fun UserProgressEntity.toDomain(userId: String): UserProgress {
        return UserProgress(
            userId = userId,
            dailyGoals = emptyList(), // Populated by use case
            weeklyGoals = emptyList(),
            monthlyGoals = emptyList(),
            totalPoints = totalPoints,
            level = level,
            lastUpdated = Instant.ofEpochMilli(lastUpdated)
        )
    }
    
    private fun StreakEntity.toDomain(): Streak {
        return Streak(
            id = id,
            userId = userId,
            type = StreakType.valueOf(type),
            currentCount = currentCount,
            longestCount = longestCount,
            lastAchievedDate = lastAchievedDate?.let { LocalDate.ofEpochDay(it) },
            startDate = startDate?.let { LocalDate.ofEpochDay(it) },
            lastUpdated = Instant.ofEpochMilli(lastUpdated)
        )
    }
}
