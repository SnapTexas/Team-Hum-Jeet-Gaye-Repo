package com.healthtracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * User's overall gamification progress including points, level, and goals.
 */
data class UserProgress(
    val userId: String,
    val dailyGoals: List<GoalProgress>,
    val weeklyGoals: List<GoalProgress>,
    val monthlyGoals: List<GoalProgress>,
    val totalPoints: Int,
    val level: Int,
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Points required to reach the next level.
     * Level formula: level = sqrt(totalPoints / 100)
     */
    val pointsToNextLevel: Int
        get() {
            val nextLevel = level + 1
            return (nextLevel * nextLevel * 100) - totalPoints
        }
    
    /**
     * Progress percentage toward next level (0-100).
     */
    val levelProgress: Float
        get() {
            val currentLevelPoints = level * level * 100
            val nextLevelPoints = (level + 1) * (level + 1) * 100
            val pointsInLevel = totalPoints - currentLevelPoints
            val pointsNeeded = nextLevelPoints - currentLevelPoints
            return if (pointsNeeded > 0) (pointsInLevel.toFloat() / pointsNeeded * 100) else 100f
        }
    
    companion object {
        /**
         * Calculate level from total points.
         */
        fun calculateLevel(points: Int): Int {
            return kotlin.math.sqrt(points / 100.0).toInt().coerceAtLeast(1)
        }
    }
}

/**
 * Streak tracking for consecutive goal achievements.
 */
data class Streak(
    val id: String,
    val userId: String,
    val type: StreakType,
    val currentCount: Int,
    val longestCount: Int,
    val lastAchievedDate: LocalDate?,
    val startDate: LocalDate?,
    val lastUpdated: Instant = Instant.now()
) {
    /**
     * Whether the streak is currently active (achieved today or yesterday).
     */
    val isActive: Boolean
        get() {
            val today = LocalDate.now()
            return lastAchievedDate != null && 
                   (lastAchievedDate == today || lastAchievedDate == today.minusDays(1))
        }
    
    /**
     * Whether the streak is at risk (not achieved today but was active yesterday).
     */
    val isAtRisk: Boolean
        get() {
            val today = LocalDate.now()
            return lastAchievedDate != null && 
                   lastAchievedDate == today.minusDays(1) &&
                   currentCount > 0
        }
}

/**
 * Types of streaks that can be tracked.
 */
enum class StreakType {
    DAILY_STEPS,
    DAILY_WATER,
    DAILY_WORKOUT,
    DAILY_MEDITATION,
    WEEKLY_GOALS
}

/**
 * Badge that can be earned by users.
 */
data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String,
    val category: BadgeCategory,
    val rarity: BadgeRarity,
    val requirement: BadgeRequirement,
    val unlockedAt: Instant? = null
) {
    /**
     * Whether this badge has been unlocked.
     */
    val isUnlocked: Boolean
        get() = unlockedAt != null
}

/**
 * Category of badges.
 */
enum class BadgeCategory {
    STEPS,
    STREAK,
    WORKOUT,
    MEDITATION,
    SOCIAL,
    SPECIAL
}

/**
 * Rarity level of badges affecting visual presentation.
 */
enum class BadgeRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

/**
 * Requirement to unlock a badge.
 */
data class BadgeRequirement(
    val type: BadgeType,
    val threshold: Int,
    val description: String
)

/**
 * Types of badge requirements.
 */
enum class BadgeType {
    STEPS_MILESTONE,      // Total steps reached
    STREAK_MILESTONE,     // Consecutive days
    WORKOUT_COUNT,        // Total workouts completed
    MEDITATION_COUNT,     // Total meditation sessions
    SOCIAL_CHALLENGE,     // Social challenges won
    STEPS_SINGLE_DAY,     // Steps in a single day
    CALORIES_BURNED,      // Total calories burned
    WATER_INTAKE,         // Water intake goals met
    SLEEP_QUALITY,        // Good sleep nights
    LEVEL_REACHED         // User level reached
}


/**
 * Achievement unlocked by a user.
 */
data class Achievement(
    val id: String,
    val badge: Badge,
    val unlockedAt: Instant,
    val pointsAwarded: Int
)

/**
 * Leaderboard entry for social comparisons.
 */
data class LeaderboardEntry(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val rank: Int,
    val score: Int,
    val metricValue: Double,
    val trend: TrendDirection,
    val isCurrentUser: Boolean = false
)

/**
 * Gamification state for syncing across devices.
 */
data class GamificationState(
    val userId: String,
    val totalPoints: Int,
    val level: Int,
    val streaks: List<Streak>,
    val unlockedBadgeIds: List<String>,
    val lastSyncedAt: Instant,
    val version: Long = 1
)

/**
 * Points awarded for various actions.
 */
object PointsConfig {
    const val DAILY_STEP_GOAL = 50
    const val WEEKLY_STEP_GOAL = 100
    const val MONTHLY_STEP_GOAL = 200
    const val STREAK_DAY = 10
    const val STREAK_WEEK = 100
    const val STREAK_MONTH = 500
    const val BADGE_COMMON = 25
    const val BADGE_UNCOMMON = 50
    const val BADGE_RARE = 100
    const val BADGE_EPIC = 250
    const val BADGE_LEGENDARY = 500
    const val WORKOUT_COMPLETED = 30
    const val MEDITATION_COMPLETED = 20
    const val WATER_GOAL_MET = 15
    const val SLEEP_GOAL_MET = 25
    
    /**
     * Get points for unlocking a badge based on rarity.
     */
    fun getPointsForBadge(rarity: BadgeRarity): Int = when (rarity) {
        BadgeRarity.COMMON -> BADGE_COMMON
        BadgeRarity.UNCOMMON -> BADGE_UNCOMMON
        BadgeRarity.RARE -> BADGE_RARE
        BadgeRarity.EPIC -> BADGE_EPIC
        BadgeRarity.LEGENDARY -> BADGE_LEGENDARY
    }
}

/**
 * Predefined badges available in the system.
 */
object PredefinedBadges {
    
    // Steps milestones
    val FIRST_STEPS = Badge(
        id = "badge_first_steps",
        name = "First Steps",
        description = "Walk 10,000 steps in a single day",
        iconName = "ic_badge_first_steps",
        category = BadgeCategory.STEPS,
        rarity = BadgeRarity.COMMON,
        requirement = BadgeRequirement(BadgeType.STEPS_SINGLE_DAY, 10000, "Walk 10,000 steps in a day")
    )
    
    val MARATHON_WALKER = Badge(
        id = "badge_marathon_walker",
        name = "Marathon Walker",
        description = "Walk 100,000 total steps",
        iconName = "ic_badge_marathon",
        category = BadgeCategory.STEPS,
        rarity = BadgeRarity.UNCOMMON,
        requirement = BadgeRequirement(BadgeType.STEPS_MILESTONE, 100000, "Accumulate 100,000 total steps")
    )
    
    val STEP_MASTER = Badge(
        id = "badge_step_master",
        name = "Step Master",
        description = "Walk 1,000,000 total steps",
        iconName = "ic_badge_step_master",
        category = BadgeCategory.STEPS,
        rarity = BadgeRarity.LEGENDARY,
        requirement = BadgeRequirement(BadgeType.STEPS_MILESTONE, 1000000, "Accumulate 1,000,000 total steps")
    )
    
    // Streak milestones
    val WEEK_WARRIOR = Badge(
        id = "badge_week_warrior",
        name = "Week Warrior",
        description = "Maintain a 7-day streak",
        iconName = "ic_badge_week_warrior",
        category = BadgeCategory.STREAK,
        rarity = BadgeRarity.COMMON,
        requirement = BadgeRequirement(BadgeType.STREAK_MILESTONE, 7, "Achieve a 7-day streak")
    )
    
    val MONTH_CHAMPION = Badge(
        id = "badge_month_champion",
        name = "Month Champion",
        description = "Maintain a 30-day streak",
        iconName = "ic_badge_month_champion",
        category = BadgeCategory.STREAK,
        rarity = BadgeRarity.RARE,
        requirement = BadgeRequirement(BadgeType.STREAK_MILESTONE, 30, "Achieve a 30-day streak")
    )
    
    val STREAK_LEGEND = Badge(
        id = "badge_streak_legend",
        name = "Streak Legend",
        description = "Maintain a 100-day streak",
        iconName = "ic_badge_streak_legend",
        category = BadgeCategory.STREAK,
        rarity = BadgeRarity.LEGENDARY,
        requirement = BadgeRequirement(BadgeType.STREAK_MILESTONE, 100, "Achieve a 100-day streak")
    )
    
    // Workout badges
    val FITNESS_STARTER = Badge(
        id = "badge_fitness_starter",
        name = "Fitness Starter",
        description = "Complete 10 workouts",
        iconName = "ic_badge_fitness_starter",
        category = BadgeCategory.WORKOUT,
        rarity = BadgeRarity.COMMON,
        requirement = BadgeRequirement(BadgeType.WORKOUT_COUNT, 10, "Complete 10 workouts")
    )
    
    val GYM_ENTHUSIAST = Badge(
        id = "badge_gym_enthusiast",
        name = "Gym Enthusiast",
        description = "Complete 50 workouts",
        iconName = "ic_badge_gym_enthusiast",
        category = BadgeCategory.WORKOUT,
        rarity = BadgeRarity.RARE,
        requirement = BadgeRequirement(BadgeType.WORKOUT_COUNT, 50, "Complete 50 workouts")
    )
    
    // Meditation badges
    val MINDFUL_BEGINNER = Badge(
        id = "badge_mindful_beginner",
        name = "Mindful Beginner",
        description = "Complete 5 meditation sessions",
        iconName = "ic_badge_mindful_beginner",
        category = BadgeCategory.MEDITATION,
        rarity = BadgeRarity.COMMON,
        requirement = BadgeRequirement(BadgeType.MEDITATION_COUNT, 5, "Complete 5 meditation sessions")
    )
    
    val ZEN_MASTER = Badge(
        id = "badge_zen_master",
        name = "Zen Master",
        description = "Complete 100 meditation sessions",
        iconName = "ic_badge_zen_master",
        category = BadgeCategory.MEDITATION,
        rarity = BadgeRarity.EPIC,
        requirement = BadgeRequirement(BadgeType.MEDITATION_COUNT, 100, "Complete 100 meditation sessions")
    )
    
    /**
     * Get all predefined badges.
     */
    fun getAllBadges(): List<Badge> = listOf(
        FIRST_STEPS,
        MARATHON_WALKER,
        STEP_MASTER,
        WEEK_WARRIOR,
        MONTH_CHAMPION,
        STREAK_LEGEND,
        FITNESS_STARTER,
        GYM_ENTHUSIAST,
        MINDFUL_BEGINNER,
        ZEN_MASTER
    )
    
    /**
     * Get badge by ID.
     */
    fun getBadgeById(id: String): Badge? = getAllBadges().find { it.id == id }
}
