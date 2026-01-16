package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing Health Circle data.
 */
@Entity(tableName = "health_circles")
data class HealthCircleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: String, // CircleType enum as string
    val ownerId: String,
    val joinCode: String,
    val description: String?,
    val createdAt: Long, // Epoch millis
    val isActive: Boolean = true,
    val lastUpdated: Long, // Epoch millis
    val needsSync: Boolean = false
)

/**
 * Room entity for storing circle membership data.
 */
@Entity(
    tableName = "circle_members",
    indices = [Index(value = ["circleId", "userId"], unique = true)]
)
data class CircleMemberEntity(
    @PrimaryKey
    val id: String,
    val circleId: String,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String, // MemberRole enum as string
    val sharedMetricsJson: String, // JSON array of MetricType strings
    val joinedAt: Long, // Epoch millis
    val lastActiveAt: Long?, // Epoch millis
    val needsSync: Boolean = false
)

/**
 * Room entity for storing circle privacy settings.
 */
@Entity(tableName = "circle_privacy_settings")
data class CirclePrivacySettingsEntity(
    @PrimaryKey
    val userId: String,
    val defaultSharedMetricsJson: String, // JSON array of MetricType strings
    val circleOverridesJson: String, // JSON map of circleId -> MetricType array
    val lastUpdated: Long, // Epoch millis
    val needsSync: Boolean = false
)

/**
 * Room entity for storing circle invitations.
 */
@Entity(
    tableName = "circle_invitations",
    indices = [Index(value = ["inviteeId"])]
)
data class CircleInvitationEntity(
    @PrimaryKey
    val id: String,
    val circleId: String,
    val circleName: String,
    val inviterId: String,
    val inviterName: String,
    val inviteeId: String,
    val createdAt: Long, // Epoch millis
    val expiresAt: Long, // Epoch millis
    val status: String, // InvitationStatus enum as string
    val needsSync: Boolean = false
)

/**
 * Room entity for storing circle challenges.
 */
@Entity(
    tableName = "circle_challenges",
    indices = [Index(value = ["circleId"])]
)
data class CircleChallengeEntity(
    @PrimaryKey
    val id: String,
    val circleId: String,
    val name: String,
    val description: String,
    val metricType: String, // MetricType enum as string
    val targetValue: Double,
    val startDate: Long, // Epoch millis
    val endDate: Long, // Epoch millis
    val participantsJson: String, // JSON array of user IDs
    val isActive: Boolean = true,
    val needsSync: Boolean = false
)

/**
 * Room entity for caching leaderboard entries.
 */
@Entity(
    tableName = "circle_leaderboard_cache",
    indices = [Index(value = ["circleId", "metricType"])]
)
data class CircleLeaderboardCacheEntity(
    @PrimaryKey
    val id: String, // circleId_userId_metricType
    val circleId: String,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val metricType: String, // MetricType enum as string
    val rank: Int,
    val score: Int,
    val metricValue: Double,
    val trend: String, // TrendDirection enum as string
    val periodContribution: Double,
    val cachedAt: Long // Epoch millis
)

/**
 * Room entity for storing circle progress aggregations.
 */
@Entity(
    tableName = "circle_progress",
    indices = [Index(value = ["circleId", "periodStart", "periodEnd"])]
)
data class CircleProgressEntity(
    @PrimaryKey
    val id: String, // circleId_periodStart_periodEnd
    val circleId: String,
    val totalSteps: Long,
    val totalCalories: Double,
    val averageSleepMinutes: Double,
    val participatingMembers: Int,
    val periodStart: Long, // Epoch millis
    val periodEnd: Long, // Epoch millis
    val cachedAt: Long // Epoch millis
)
