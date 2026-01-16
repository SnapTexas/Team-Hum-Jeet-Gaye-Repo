package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.healthtracker.data.local.entity.PointsHistoryEntity
import com.healthtracker.data.local.entity.StreakEntity
import com.healthtracker.data.local.entity.UnlockedBadgeEntity
import com.healthtracker.data.local.entity.UserProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for gamification operations.
 */
@Dao
interface GamificationDao {
    
    // User Progress operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProgress(progress: UserProgressEntity)
    
    @Update
    suspend fun updateUserProgress(progress: UserProgressEntity)
    
    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    fun getUserProgressFlow(userId: String): Flow<UserProgressEntity?>
    
    @Query("SELECT * FROM user_progress WHERE userId = :userId LIMIT 1")
    suspend fun getUserProgress(userId: String): UserProgressEntity?
    
    @Query("UPDATE user_progress SET totalPoints = totalPoints + :points, lastUpdated = :timestamp, needsSync = 1 WHERE userId = :userId")
    suspend fun addPoints(userId: String, points: Int, timestamp: Long)
    
    @Query("UPDATE user_progress SET level = :level, lastUpdated = :timestamp, needsSync = 1 WHERE userId = :userId")
    suspend fun updateLevel(userId: String, level: Int, timestamp: Long)
    
    // Streak operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StreakEntity)
    
    @Update
    suspend fun updateStreak(streak: StreakEntity)
    
    @Query("SELECT * FROM streaks WHERE userId = :userId")
    fun getStreaksFlow(userId: String): Flow<List<StreakEntity>>
    
    @Query("SELECT * FROM streaks WHERE userId = :userId")
    suspend fun getStreaks(userId: String): List<StreakEntity>
    
    @Query("SELECT * FROM streaks WHERE userId = :userId AND type = :type LIMIT 1")
    fun getStreakFlow(userId: String, type: String): Flow<StreakEntity?>
    
    @Query("SELECT * FROM streaks WHERE userId = :userId AND type = :type LIMIT 1")
    suspend fun getStreak(userId: String, type: String): StreakEntity?
    
    @Query("SELECT * FROM streaks WHERE userId = :userId AND currentCount > 0")
    suspend fun getActiveStreaks(userId: String): List<StreakEntity>
    
    // Badge operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnlockedBadge(badge: UnlockedBadgeEntity)
    
    @Query("SELECT * FROM unlocked_badges WHERE userId = :userId")
    fun getUnlockedBadgesFlow(userId: String): Flow<List<UnlockedBadgeEntity>>
    
    @Query("SELECT * FROM unlocked_badges WHERE userId = :userId")
    suspend fun getUnlockedBadges(userId: String): List<UnlockedBadgeEntity>
    
    @Query("SELECT * FROM unlocked_badges WHERE userId = :userId AND badgeId = :badgeId LIMIT 1")
    suspend fun getUnlockedBadge(userId: String, badgeId: String): UnlockedBadgeEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM unlocked_badges WHERE userId = :userId AND badgeId = :badgeId)")
    suspend fun isBadgeUnlocked(userId: String, badgeId: String): Boolean
    
    // Points history operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointsHistory(history: PointsHistoryEntity)
    
    @Query("SELECT * FROM points_history WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getPointsHistoryFlow(userId: String, limit: Int = 50): Flow<List<PointsHistoryEntity>>
    
    @Query("SELECT SUM(points) FROM points_history WHERE userId = :userId")
    suspend fun getTotalPoints(userId: String): Int?
    
    // Sync operations
    
    @Query("SELECT * FROM user_progress WHERE needsSync = 1")
    suspend fun getProgressNeedingSync(): List<UserProgressEntity>
    
    @Query("SELECT * FROM streaks WHERE needsSync = 1")
    suspend fun getStreaksNeedingSync(): List<StreakEntity>
    
    @Query("SELECT * FROM unlocked_badges WHERE needsSync = 1")
    suspend fun getBadgesNeedingSync(): List<UnlockedBadgeEntity>
    
    @Query("UPDATE user_progress SET needsSync = 0 WHERE userId = :userId")
    suspend fun markProgressAsSynced(userId: String)
    
    @Query("UPDATE streaks SET needsSync = 0 WHERE userId = :userId")
    suspend fun markStreaksAsSynced(userId: String)
    
    @Query("UPDATE unlocked_badges SET needsSync = 0 WHERE userId = :userId")
    suspend fun markBadgesAsSynced(userId: String)
    
    // Delete operations
    
    @Query("DELETE FROM user_progress WHERE userId = :userId")
    suspend fun deleteUserProgress(userId: String)
    
    @Query("DELETE FROM streaks WHERE userId = :userId")
    suspend fun deleteStreaks(userId: String)
    
    @Query("DELETE FROM unlocked_badges WHERE userId = :userId")
    suspend fun deleteUnlockedBadges(userId: String)
    
    @Query("DELETE FROM points_history WHERE userId = :userId")
    suspend fun deletePointsHistory(userId: String)
    
    // Delete all operations for data export/deletion
    
    @Query("DELETE FROM user_progress WHERE userId = :userId")
    suspend fun deleteAllProgressForUser(userId: String)
    
    @Query("DELETE FROM streaks WHERE userId = :userId")
    suspend fun deleteAllStreaksForUser(userId: String)
    
    @Query("DELETE FROM unlocked_badges WHERE userId = :userId")
    suspend fun deleteAllBadgesForUser(userId: String)
    
    @Query("DELETE FROM points_history WHERE userId = :userId")
    suspend fun deleteAllPointsHistoryForUser(userId: String)
}
