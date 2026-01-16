package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing user progress data.
 */
@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val totalPoints: Int,
    val level: Int,
    val lastUpdated: Long, // Epoch millis
    val needsSync: Boolean = false
)

/**
 * Room entity for storing streak data.
 */
@Entity(
    tableName = "streaks",
    indices = [Index(value = ["userId", "type"], unique = true)]
)
data class StreakEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String, // StreakType enum as string
    val currentCount: Int,
    val longestCount: Int,
    val lastAchievedDate: Long?, // Epoch day
    val startDate: Long?, // Epoch day
    val lastUpdated: Long, // Epoch millis
    val needsSync: Boolean = false
)

/**
 * Room entity for storing unlocked badges.
 */
@Entity(
    tableName = "unlocked_badges",
    indices = [Index(value = ["userId", "badgeId"], unique = true)]
)
data class UnlockedBadgeEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val badgeId: String,
    val unlockedAt: Long, // Epoch millis
    val pointsAwarded: Int,
    val needsSync: Boolean = false
)

/**
 * Room entity for storing points history.
 */
@Entity(tableName = "points_history")
data class PointsHistoryEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val points: Int,
    val reason: String,
    val timestamp: Long, // Epoch millis
    val needsSync: Boolean = false
)
