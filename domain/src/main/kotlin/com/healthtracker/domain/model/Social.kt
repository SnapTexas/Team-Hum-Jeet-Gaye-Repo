package com.healthtracker.domain.model

import java.time.Instant

/**
 * A Health Circle - a group of users who share health data and participate in challenges.
 * Supports friends, corporate, and family circle types.
 *
 * @property id Unique identifier for the circle
 * @property name Display name of the circle
 * @property type Type of circle (friends, corporate, family)
 * @property ownerId User ID of the circle owner
 * @property members List of circle members
 * @property joinCode Unique code for joining the circle
 * @property description Optional description of the circle
 * @property createdAt When the circle was created
 * @property isActive Whether the circle is currently active
 */
data class HealthCircle(
    val id: String,
    val name: String,
    val type: CircleType,
    val ownerId: String,
    val members: List<CircleMember>,
    val joinCode: String,
    val description: String? = null,
    val createdAt: Instant,
    val isActive: Boolean = true
) {
    /**
     * Total number of members in the circle.
     */
    val memberCount: Int
        get() = members.size

    /**
     * Check if a user is a member of this circle.
     */
    fun isMember(userId: String): Boolean = members.any { it.userId == userId }

    /**
     * Check if a user is the owner of this circle.
     */
    fun isOwner(userId: String): Boolean = ownerId == userId

    /**
     * Check if a user can manage this circle (owner or admin).
     */
    fun canManage(userId: String): Boolean =
        isOwner(userId) || members.any { it.userId == userId && it.role == MemberRole.ADMIN }
}

/**
 * Type of Health Circle.
 */
enum class CircleType {
    /** Circle for friends to compete and share progress */
    FRIENDS,
    /** Corporate/workplace wellness circle */
    CORPORATE,
    /** Family circle for monitoring elderly or family members */
    FAMILY
}

/**
 * A member of a Health Circle.
 *
 * @property userId Unique identifier of the user
 * @property displayName Display name shown in the circle
 * @property avatarUrl Optional URL to user's avatar image
 * @property role Member's role in the circle
 * @property sharedMetrics Set of metric types this member shares with the circle
 * @property joinedAt When the member joined the circle
 * @property lastActiveAt When the member was last active
 */
data class CircleMember(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: MemberRole,
    val sharedMetrics: Set<MetricType>,
    val joinedAt: Instant,
    val lastActiveAt: Instant? = null
) {
    /**
     * Check if this member shares a specific metric type.
     */
    fun sharesMetric(metricType: MetricType): Boolean = metricType in sharedMetrics

    /**
     * Check if this member is an admin or owner.
     */
    val isAdmin: Boolean
        get() = role == MemberRole.ADMIN || role == MemberRole.OWNER
}

/**
 * Role of a member within a Health Circle.
 */
enum class MemberRole {
    /** Circle creator with full permissions */
    OWNER,
    /** Administrator with management permissions */
    ADMIN,
    /** Regular member */
    MEMBER
}

/**
 * Entry in a circle leaderboard.
 * Note: LeaderboardEntry is already defined in Gamification.kt, 
 * this extends it with circle-specific functionality.
 *
 * @property rank Position in the leaderboard (1-based)
 * @property member The circle member
 * @property score Total score for the leaderboard period
 * @property metricValue Primary metric value (e.g., steps)
 * @property trend Direction of recent performance
 * @property periodContribution Contribution to circle's total
 */
data class CircleLeaderboardEntry(
    val rank: Int,
    val member: CircleMember,
    val score: Int,
    val metricValue: Double,
    val trend: TrendDirection,
    val periodContribution: Double = 0.0
) {
    /**
     * Whether this entry represents the current user.
     */
    var isCurrentUser: Boolean = false
}

/**
 * Aggregated progress for a Health Circle.
 *
 * @property circleId ID of the circle
 * @property totalSteps Combined steps from all opted-in members
 * @property totalCalories Combined calories from all opted-in members
 * @property averageSleepMinutes Average sleep across opted-in members
 * @property participatingMembers Number of members who contributed data
 * @property periodStart Start of the aggregation period
 * @property periodEnd End of the aggregation period
 */
data class CircleProgress(
    val circleId: String,
    val totalSteps: Long,
    val totalCalories: Double,
    val averageSleepMinutes: Double,
    val participatingMembers: Int,
    val periodStart: Instant,
    val periodEnd: Instant
) {
    /**
     * Average steps per participating member.
     */
    val averageStepsPerMember: Double
        get() = if (participatingMembers > 0) totalSteps.toDouble() / participatingMembers else 0.0

    /**
     * Average calories per participating member.
     */
    val averageCaloriesPerMember: Double
        get() = if (participatingMembers > 0) totalCalories / participatingMembers else 0.0
}

/**
 * Challenge within a Health Circle.
 *
 * @property id Unique identifier for the challenge
 * @property circleId ID of the circle this challenge belongs to
 * @property name Display name of the challenge
 * @property description Description of the challenge
 * @property metricType Type of metric being tracked
 * @property targetValue Target value to achieve
 * @property startDate When the challenge starts
 * @property endDate When the challenge ends
 * @property participants List of participating member IDs
 * @property isActive Whether the challenge is currently active
 */
data class CircleChallenge(
    val id: String,
    val circleId: String,
    val name: String,
    val description: String,
    val metricType: MetricType,
    val targetValue: Double,
    val startDate: Instant,
    val endDate: Instant,
    val participants: List<String>,
    val isActive: Boolean = true
) {
    /**
     * Check if the challenge is currently running.
     */
    fun isRunning(now: Instant = Instant.now()): Boolean =
        isActive && now.isAfter(startDate) && now.isBefore(endDate)

    /**
     * Check if the challenge has ended.
     */
    fun hasEnded(now: Instant = Instant.now()): Boolean =
        now.isAfter(endDate)
}

/**
 * Privacy settings for sharing metrics with circles.
 *
 * @property userId User ID these settings belong to
 * @property defaultSharedMetrics Default metrics shared with new circles
 * @property circleOverrides Per-circle metric sharing overrides
 */
data class CirclePrivacySettings(
    val userId: String,
    val defaultSharedMetrics: Set<MetricType> = DEFAULT_SHARED_METRICS,
    val circleOverrides: Map<String, Set<MetricType>> = emptyMap()
) {
    /**
     * Get shared metrics for a specific circle.
     */
    fun getSharedMetrics(circleId: String): Set<MetricType> =
        circleOverrides[circleId] ?: defaultSharedMetrics

    /**
     * Check if a metric is shared with a specific circle.
     */
    fun isMetricShared(circleId: String, metricType: MetricType): Boolean =
        metricType in getSharedMetrics(circleId)

    companion object {
        /**
         * Default metrics shared with circles (non-sensitive).
         */
        val DEFAULT_SHARED_METRICS = setOf(
            MetricType.STEPS,
            MetricType.DISTANCE,
            MetricType.CALORIES
        )

        /**
         * All metrics that can potentially be shared.
         */
        val SHAREABLE_METRICS = setOf(
            MetricType.STEPS,
            MetricType.DISTANCE,
            MetricType.CALORIES,
            MetricType.SLEEP,
            MetricType.SCREEN_TIME
        )

        /**
         * Sensitive metrics that require explicit opt-in.
         */
        val SENSITIVE_METRICS = setOf(
            MetricType.HEART_RATE,
            MetricType.HRV,
            MetricType.MOOD
        )
    }
}

/**
 * Invitation to join a Health Circle.
 *
 * @property id Unique identifier for the invitation
 * @property circleId ID of the circle being invited to
 * @property circleName Name of the circle
 * @property inviterId User ID of who sent the invitation
 * @property inviterName Display name of the inviter
 * @property inviteeId User ID of who is being invited
 * @property createdAt When the invitation was created
 * @property expiresAt When the invitation expires
 * @property status Current status of the invitation
 */
data class CircleInvitation(
    val id: String,
    val circleId: String,
    val circleName: String,
    val inviterId: String,
    val inviterName: String,
    val inviteeId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: InvitationStatus = InvitationStatus.PENDING
) {
    /**
     * Check if the invitation is still valid.
     */
    fun isValid(now: Instant = Instant.now()): Boolean =
        status == InvitationStatus.PENDING && now.isBefore(expiresAt)
}

/**
 * Status of a circle invitation.
 */
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}

/**
 * Result of joining a circle.
 */
sealed class JoinCircleResult {
    data class Success(val circle: HealthCircle) : JoinCircleResult()
    data class InvalidCode(val message: String = "Invalid join code") : JoinCircleResult()
    data class AlreadyMember(val message: String = "Already a member of this circle") : JoinCircleResult()
    data class CircleFull(val message: String = "Circle has reached maximum members") : JoinCircleResult()
    data class Error(val message: String) : JoinCircleResult()
}

/**
 * Configuration for circle limits and defaults.
 */
object CircleConfig {
    /** Maximum members per circle */
    const val MAX_MEMBERS_FRIENDS = 50
    const val MAX_MEMBERS_CORPORATE = 500
    const val MAX_MEMBERS_FAMILY = 20

    /** Maximum circles a user can join */
    const val MAX_CIRCLES_PER_USER = 10

    /** Join code length */
    const val JOIN_CODE_LENGTH = 8

    /** Invitation expiry duration in days */
    const val INVITATION_EXPIRY_DAYS = 7L

    /**
     * Get maximum members for a circle type.
     */
    fun getMaxMembers(type: CircleType): Int = when (type) {
        CircleType.FRIENDS -> MAX_MEMBERS_FRIENDS
        CircleType.CORPORATE -> MAX_MEMBERS_CORPORATE
        CircleType.FAMILY -> MAX_MEMBERS_FAMILY
    }
}
