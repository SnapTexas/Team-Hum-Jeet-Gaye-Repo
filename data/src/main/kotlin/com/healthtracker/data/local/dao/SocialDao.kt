package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.healthtracker.data.local.entity.CircleChallengeEntity
import com.healthtracker.data.local.entity.CircleInvitationEntity
import com.healthtracker.data.local.entity.CircleLeaderboardCacheEntity
import com.healthtracker.data.local.entity.CircleMemberEntity
import com.healthtracker.data.local.entity.CirclePrivacySettingsEntity
import com.healthtracker.data.local.entity.CircleProgressEntity
import com.healthtracker.data.local.entity.HealthCircleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for social/circle operations.
 */
@Dao
interface SocialDao {

    // ==================== Circle Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircle(circle: HealthCircleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCircles(circles: List<HealthCircleEntity>)

    @Update
    suspend fun updateCircle(circle: HealthCircleEntity)

    @Query("SELECT * FROM health_circles WHERE id = :circleId")
    fun getCircleFlow(circleId: String): Flow<HealthCircleEntity?>

    @Query("SELECT * FROM health_circles WHERE id = :circleId")
    suspend fun getCircle(circleId: String): HealthCircleEntity?

    @Query("SELECT * FROM health_circles WHERE joinCode = :joinCode AND isActive = 1")
    suspend fun getCircleByJoinCode(joinCode: String): HealthCircleEntity?

    @Query("SELECT * FROM health_circles WHERE isActive = 1 ORDER BY name ASC")
    fun getAllCirclesFlow(): Flow<List<HealthCircleEntity>>

    @Query("SELECT * FROM health_circles WHERE isActive = 1")
    suspend fun getAllCircles(): List<HealthCircleEntity>

    @Query("DELETE FROM health_circles WHERE id = :circleId")
    suspend fun deleteCircle(circleId: String)

    @Query("UPDATE health_circles SET isActive = 0, lastUpdated = :timestamp, needsSync = 1 WHERE id = :circleId")
    suspend fun deactivateCircle(circleId: String, timestamp: Long)

    @Query("UPDATE health_circles SET joinCode = :newCode, lastUpdated = :timestamp, needsSync = 1 WHERE id = :circleId")
    suspend fun updateJoinCode(circleId: String, newCode: String, timestamp: Long)

    // ==================== Member Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: CircleMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<CircleMemberEntity>)

    @Update
    suspend fun updateMember(member: CircleMemberEntity)

    @Query("SELECT * FROM circle_members WHERE circleId = :circleId ORDER BY role ASC, displayName ASC")
    fun getMembersFlow(circleId: String): Flow<List<CircleMemberEntity>>

    @Query("SELECT * FROM circle_members WHERE circleId = :circleId")
    suspend fun getMembers(circleId: String): List<CircleMemberEntity>

    @Query("SELECT * FROM circle_members WHERE circleId = :circleId AND userId = :userId")
    suspend fun getMember(circleId: String, userId: String): CircleMemberEntity?

    @Query("SELECT * FROM circle_members WHERE userId = :userId")
    suspend fun getMembershipsByUser(userId: String): List<CircleMemberEntity>

    @Query("SELECT hc.* FROM health_circles hc INNER JOIN circle_members cm ON hc.id = cm.circleId WHERE cm.userId = :userId AND hc.isActive = 1")
    fun getCirclesByUserFlow(userId: String): Flow<List<HealthCircleEntity>>

    @Query("SELECT hc.* FROM health_circles hc INNER JOIN circle_members cm ON hc.id = cm.circleId WHERE cm.userId = :userId AND hc.isActive = 1")
    suspend fun getCirclesByUser(userId: String): List<HealthCircleEntity>

    @Query("SELECT COUNT(*) FROM circle_members WHERE circleId = :circleId")
    suspend fun getMemberCount(circleId: String): Int

    @Query("DELETE FROM circle_members WHERE circleId = :circleId AND userId = :userId")
    suspend fun removeMember(circleId: String, userId: String)

    @Query("DELETE FROM circle_members WHERE circleId = :circleId")
    suspend fun removeAllMembers(circleId: String)

    @Query("UPDATE circle_members SET role = :role, needsSync = 1 WHERE circleId = :circleId AND userId = :userId")
    suspend fun updateMemberRole(circleId: String, userId: String, role: String)

    @Query("UPDATE circle_members SET sharedMetricsJson = :metricsJson, needsSync = 1 WHERE circleId = :circleId AND userId = :userId")
    suspend fun updateMemberSharedMetrics(circleId: String, userId: String, metricsJson: String)

    @Query("UPDATE circle_members SET lastActiveAt = :timestamp WHERE circleId = :circleId AND userId = :userId")
    suspend fun updateMemberLastActive(circleId: String, userId: String, timestamp: Long)

    // ==================== Privacy Settings Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrivacySettings(settings: CirclePrivacySettingsEntity)

    @Update
    suspend fun updatePrivacySettings(settings: CirclePrivacySettingsEntity)

    @Query("SELECT * FROM circle_privacy_settings WHERE userId = :userId")
    fun getPrivacySettingsFlow(userId: String): Flow<CirclePrivacySettingsEntity?>

    @Query("SELECT * FROM circle_privacy_settings WHERE userId = :userId")
    suspend fun getPrivacySettings(userId: String): CirclePrivacySettingsEntity?

    // ==================== Invitation Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: CircleInvitationEntity)

    @Update
    suspend fun updateInvitation(invitation: CircleInvitationEntity)

    @Query("SELECT * FROM circle_invitations WHERE inviteeId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingInvitationsFlow(userId: String): Flow<List<CircleInvitationEntity>>

    @Query("SELECT * FROM circle_invitations WHERE inviteeId = :userId AND status = 'PENDING'")
    suspend fun getPendingInvitations(userId: String): List<CircleInvitationEntity>

    @Query("SELECT * FROM circle_invitations WHERE id = :invitationId")
    suspend fun getInvitation(invitationId: String): CircleInvitationEntity?

    @Query("UPDATE circle_invitations SET status = :status, needsSync = 1 WHERE id = :invitationId")
    suspend fun updateInvitationStatus(invitationId: String, status: String)

    @Query("DELETE FROM circle_invitations WHERE id = :invitationId")
    suspend fun deleteInvitation(invitationId: String)

    @Query("DELETE FROM circle_invitations WHERE expiresAt < :currentTime AND status = 'PENDING'")
    suspend fun deleteExpiredInvitations(currentTime: Long)

    // ==================== Challenge Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: CircleChallengeEntity)

    @Update
    suspend fun updateChallenge(challenge: CircleChallengeEntity)

    @Query("SELECT * FROM circle_challenges WHERE circleId = :circleId AND isActive = 1 ORDER BY startDate DESC")
    fun getChallengesFlow(circleId: String): Flow<List<CircleChallengeEntity>>

    @Query("SELECT * FROM circle_challenges WHERE circleId = :circleId AND isActive = 1")
    suspend fun getChallenges(circleId: String): List<CircleChallengeEntity>

    @Query("SELECT * FROM circle_challenges WHERE id = :challengeId")
    suspend fun getChallenge(challengeId: String): CircleChallengeEntity?

    @Query("UPDATE circle_challenges SET participantsJson = :participantsJson, needsSync = 1 WHERE id = :challengeId")
    suspend fun updateChallengeParticipants(challengeId: String, participantsJson: String)

    // ==================== Leaderboard Cache Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaderboardEntries(entries: List<CircleLeaderboardCacheEntity>)

    @Query("SELECT * FROM circle_leaderboard_cache WHERE circleId = :circleId AND metricType = :metricType ORDER BY rank ASC LIMIT :limit")
    fun getLeaderboardFlow(circleId: String, metricType: String, limit: Int): Flow<List<CircleLeaderboardCacheEntity>>

    @Query("SELECT * FROM circle_leaderboard_cache WHERE circleId = :circleId AND metricType = :metricType ORDER BY rank ASC LIMIT :limit")
    suspend fun getLeaderboard(circleId: String, metricType: String, limit: Int): List<CircleLeaderboardCacheEntity>

    @Query("SELECT rank FROM circle_leaderboard_cache WHERE circleId = :circleId AND userId = :userId AND metricType = :metricType")
    suspend fun getUserRank(circleId: String, userId: String, metricType: String): Int?

    @Query("DELETE FROM circle_leaderboard_cache WHERE circleId = :circleId")
    suspend fun clearLeaderboardCache(circleId: String)

    @Query("DELETE FROM circle_leaderboard_cache WHERE cachedAt < :expiryTime")
    suspend fun clearExpiredLeaderboardCache(expiryTime: Long)

    // ==================== Progress Cache Operations ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: CircleProgressEntity)

    @Query("SELECT * FROM circle_progress WHERE circleId = :circleId AND periodStart = :periodStart AND periodEnd = :periodEnd")
    suspend fun getProgress(circleId: String, periodStart: Long, periodEnd: Long): CircleProgressEntity?

    @Query("DELETE FROM circle_progress WHERE circleId = :circleId")
    suspend fun clearProgressCache(circleId: String)

    // ==================== Sync Operations ====================

    @Query("SELECT * FROM health_circles WHERE needsSync = 1")
    suspend fun getCirclesNeedingSync(): List<HealthCircleEntity>

    @Query("SELECT * FROM circle_members WHERE needsSync = 1")
    suspend fun getMembersNeedingSync(): List<CircleMemberEntity>

    @Query("SELECT * FROM circle_privacy_settings WHERE needsSync = 1")
    suspend fun getPrivacySettingsNeedingSync(): List<CirclePrivacySettingsEntity>

    @Query("SELECT * FROM circle_invitations WHERE needsSync = 1")
    suspend fun getInvitationsNeedingSync(): List<CircleInvitationEntity>

    @Query("UPDATE health_circles SET needsSync = 0 WHERE id = :circleId")
    suspend fun markCircleAsSynced(circleId: String)

    @Query("UPDATE circle_members SET needsSync = 0 WHERE circleId = :circleId AND userId = :userId")
    suspend fun markMemberAsSynced(circleId: String, userId: String)

    @Query("UPDATE circle_privacy_settings SET needsSync = 0 WHERE userId = :userId")
    suspend fun markPrivacySettingsAsSynced(userId: String)

    // ==================== Cleanup Operations ====================

    @Transaction
    suspend fun deleteCircleWithMembers(circleId: String) {
        removeAllMembers(circleId)
        clearLeaderboardCache(circleId)
        clearProgressCache(circleId)
        deleteCircle(circleId)
    }

    @Query("DELETE FROM health_circles")
    suspend fun deleteAllCircles()

    @Query("DELETE FROM circle_members")
    suspend fun deleteAllMembers()

    @Query("DELETE FROM circle_privacy_settings")
    suspend fun deleteAllPrivacySettings()

    @Query("DELETE FROM circle_invitations")
    suspend fun deleteAllInvitations()

    @Query("DELETE FROM circle_challenges")
    suspend fun deleteAllChallenges()
    
    // User-specific deletion operations
    
    @Query("SELECT * FROM circle_members WHERE userId = :userId")
    suspend fun getCircleMembersForUser(userId: String): List<CircleMemberEntity>
    
    @Query("DELETE FROM circle_members WHERE userId = :userId")
    suspend fun deleteAllCircleMembersForUser(userId: String)
}
