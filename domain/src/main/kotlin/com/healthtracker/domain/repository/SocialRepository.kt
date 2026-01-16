package com.healthtracker.domain.repository

import com.healthtracker.domain.model.CircleChallenge
import com.healthtracker.domain.model.CircleInvitation
import com.healthtracker.domain.model.CircleLeaderboardEntry
import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleProgress
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.JoinCircleResult
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Repository interface for social features including Health Circles.
 * Handles circle management, membership, leaderboards, and privacy controls.
 */
interface SocialRepository {

    // ==================== Circle Management ====================

    /**
     * Get all circles the current user is a member of.
     */
    fun getCircles(): Flow<List<HealthCircle>>

    /**
     * Get a specific circle by ID.
     * @param circleId The circle's unique identifier
     */
    fun getCircle(circleId: String): Flow<HealthCircle?>

    /**
     * Create a new Health Circle.
     * @param name Display name for the circle
     * @param type Type of circle (friends, corporate, family)
     * @param description Optional description
     * @return The created circle or error
     */
    suspend fun createCircle(
        name: String,
        type: CircleType,
        description: String? = null
    ): Result<HealthCircle>

    /**
     * Join an existing circle using a join code.
     * @param code The circle's join code
     * @return Result indicating success or specific failure reason
     */
    suspend fun joinCircle(code: String): JoinCircleResult

    /**
     * Leave a circle.
     * @param circleId ID of the circle to leave
     * @return Success or error
     */
    suspend fun leaveCircle(circleId: String): Result<Unit>

    /**
     * Delete a circle (owner only).
     * @param circleId ID of the circle to delete
     * @return Success or error
     */
    suspend fun deleteCircle(circleId: String): Result<Unit>

    /**
     * Update circle details (owner/admin only).
     * @param circleId ID of the circle to update
     * @param name New name (optional)
     * @param description New description (optional)
     * @return Updated circle or error
     */
    suspend fun updateCircle(
        circleId: String,
        name: String? = null,
        description: String? = null
    ): Result<HealthCircle>

    /**
     * Regenerate the join code for a circle (owner/admin only).
     * @param circleId ID of the circle
     * @return New join code or error
     */
    suspend fun regenerateJoinCode(circleId: String): Result<String>

    // ==================== Member Management ====================

    /**
     * Get all members of a circle.
     * @param circleId ID of the circle
     */
    fun getCircleMembers(circleId: String): Flow<List<CircleMember>>

    /**
     * Remove a member from a circle (owner/admin only).
     * @param circleId ID of the circle
     * @param userId ID of the user to remove
     * @return Success or error
     */
    suspend fun removeMember(circleId: String, userId: String): Result<Unit>

    /**
     * Update a member's role (owner only).
     * @param circleId ID of the circle
     * @param userId ID of the user
     * @param newRole New role to assign
     * @return Success or error
     */
    suspend fun updateMemberRole(
        circleId: String,
        userId: String,
        newRole: com.healthtracker.domain.model.MemberRole
    ): Result<Unit>

    // ==================== Leaderboards & Progress ====================

    /**
     * Get the leaderboard for a circle.
     * @param circleId ID of the circle
     * @param metricType Type of metric to rank by (default: STEPS)
     * @param limit Maximum entries to return
     */
    fun getCircleLeaderboard(
        circleId: String,
        metricType: MetricType = MetricType.STEPS,
        limit: Int = 10
    ): Flow<List<CircleLeaderboardEntry>>

    /**
     * Get aggregated progress for a circle.
     * @param circleId ID of the circle
     * @param startDate Start of the period
     * @param endDate End of the period
     */
    suspend fun getCircleProgress(
        circleId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleProgress>

    /**
     * Get the current user's rank in a circle.
     * @param circleId ID of the circle
     * @param metricType Type of metric
     */
    suspend fun getUserRank(circleId: String, metricType: MetricType = MetricType.STEPS): Result<Int>

    // ==================== Privacy Controls ====================

    /**
     * Get the current user's privacy settings for circles.
     */
    fun getPrivacySettings(): Flow<CirclePrivacySettings>

    /**
     * Update which metrics are shared with a specific circle.
     * @param circleId ID of the circle
     * @param sharedMetrics Set of metrics to share
     * @return Success or error
     */
    suspend fun updateSharedMetrics(
        circleId: String,
        sharedMetrics: Set<MetricType>
    ): Result<Unit>

    /**
     * Update default shared metrics for new circles.
     * @param sharedMetrics Set of metrics to share by default
     * @return Success or error
     */
    suspend fun updateDefaultSharedMetrics(sharedMetrics: Set<MetricType>): Result<Unit>

    /**
     * Check if a specific metric is shared with a circle.
     * @param circleId ID of the circle
     * @param metricType Type of metric to check
     */
    suspend fun isMetricShared(circleId: String, metricType: MetricType): Boolean

    // ==================== Invitations ====================

    /**
     * Get pending invitations for the current user.
     */
    fun getPendingInvitations(): Flow<List<CircleInvitation>>

    /**
     * Send an invitation to join a circle.
     * @param circleId ID of the circle
     * @param inviteeId User ID to invite
     * @return The created invitation or error
     */
    suspend fun sendInvitation(circleId: String, inviteeId: String): Result<CircleInvitation>

    /**
     * Accept a circle invitation.
     * @param invitationId ID of the invitation
     * @return The joined circle or error
     */
    suspend fun acceptInvitation(invitationId: String): Result<HealthCircle>

    /**
     * Decline a circle invitation.
     * @param invitationId ID of the invitation
     * @return Success or error
     */
    suspend fun declineInvitation(invitationId: String): Result<Unit>

    // ==================== Challenges ====================

    /**
     * Get active challenges for a circle.
     * @param circleId ID of the circle
     */
    fun getCircleChallenges(circleId: String): Flow<List<CircleChallenge>>

    /**
     * Create a new challenge in a circle.
     * @param circleId ID of the circle
     * @param name Challenge name
     * @param description Challenge description
     * @param metricType Metric to track
     * @param targetValue Target value to achieve
     * @param startDate When the challenge starts
     * @param endDate When the challenge ends
     * @return The created challenge or error
     */
    suspend fun createChallenge(
        circleId: String,
        name: String,
        description: String,
        metricType: MetricType,
        targetValue: Double,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleChallenge>

    /**
     * Join a challenge.
     * @param challengeId ID of the challenge
     * @return Success or error
     */
    suspend fun joinChallenge(challengeId: String): Result<Unit>

    // ==================== Sync ====================

    /**
     * Sync circle data with remote server.
     */
    suspend fun syncCircles(): Result<Unit>

    /**
     * Force refresh circle data from remote.
     * @param circleId ID of the circle to refresh
     */
    suspend fun refreshCircle(circleId: String): Result<HealthCircle>
}
