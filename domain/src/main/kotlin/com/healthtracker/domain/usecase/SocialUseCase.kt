package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.CircleChallenge
import com.healthtracker.domain.model.CircleLeaderboardEntry
import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleProgress
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.JoinCircleResult
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Use case interface for social features including Health Circles.
 * Provides business logic for circle management, membership, and privacy controls.
 */
interface SocialUseCase {

    // ==================== Circle Management ====================

    /**
     * Get all circles the current user is a member of.
     */
    fun getMyCircles(): Flow<List<HealthCircle>>

    /**
     * Get a specific circle by ID.
     */
    fun getCircle(circleId: String): Flow<HealthCircle?>

    /**
     * Create a new Health Circle.
     * @param name Display name for the circle
     * @param type Type of circle (friends, corporate, family)
     * @param description Optional description
     */
    suspend fun createCircle(
        name: String,
        type: CircleType,
        description: String? = null
    ): Result<HealthCircle>

    /**
     * Join an existing circle using a join code.
     * @param code The circle's join code
     */
    suspend fun joinCircle(code: String): JoinCircleResult

    /**
     * Leave a circle.
     * @param circleId ID of the circle to leave
     */
    suspend fun leaveCircle(circleId: String): Result<Unit>

    /**
     * Delete a circle (owner only).
     * @param circleId ID of the circle to delete
     */
    suspend fun deleteCircle(circleId: String): Result<Unit>

    // ==================== Member Management ====================

    /**
     * Get all members of a circle.
     */
    fun getCircleMembers(circleId: String): Flow<List<CircleMember>>

    /**
     * Remove a member from a circle (owner/admin only).
     */
    suspend fun removeMember(circleId: String, userId: String): Result<Unit>

    /**
     * Update a member's role (owner only).
     */
    suspend fun updateMemberRole(
        circleId: String,
        userId: String,
        newRole: MemberRole
    ): Result<Unit>

    // ==================== Leaderboards & Progress ====================

    /**
     * Get the leaderboard for a circle.
     * @param circleId ID of the circle
     * @param metricType Type of metric to rank by
     * @param limit Maximum entries to return
     */
    fun getCircleLeaderboard(
        circleId: String,
        metricType: MetricType = MetricType.STEPS,
        limit: Int = 10
    ): Flow<List<CircleLeaderboardEntry>>

    /**
     * Get aggregated progress for a circle.
     * Only includes metrics that members have opted to share.
     */
    suspend fun getCircleProgress(
        circleId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleProgress>

    /**
     * Get the current user's rank in a circle.
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
     */
    suspend fun updateSharedMetrics(
        circleId: String,
        sharedMetrics: Set<MetricType>
    ): Result<Unit>

    /**
     * Update default shared metrics for new circles.
     */
    suspend fun updateDefaultSharedMetrics(sharedMetrics: Set<MetricType>): Result<Unit>

    /**
     * Check if a specific metric is shared with a circle.
     */
    suspend fun isMetricShared(circleId: String, metricType: MetricType): Boolean

    // ==================== Challenges ====================

    /**
     * Get active challenges for a circle.
     */
    fun getCircleChallenges(circleId: String): Flow<List<CircleChallenge>>

    /**
     * Create a new challenge in a circle.
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
     */
    suspend fun joinChallenge(challengeId: String): Result<Unit>

    // ==================== Aggregation ====================

    /**
     * Calculate aggregated metrics for a circle, respecting privacy settings.
     * Only includes metrics that each member has opted to share.
     * 
     * Property 27: Circle aggregation includes only opted-in metrics.
     */
    suspend fun calculateCircleAggregation(
        circleId: String,
        metricType: MetricType,
        startDate: Instant,
        endDate: Instant
    ): Result<Double>

    /**
     * Get member metrics for display, filtering out unshared metrics.
     * 
     * Property 28: Unshared metrics don't appear in views.
     */
    suspend fun getMemberMetricsForDisplay(
        circleId: String,
        memberId: String,
        metricTypes: Set<MetricType>
    ): Result<Map<MetricType, Double>>
}
