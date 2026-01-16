package com.healthtracker.domain.model

import java.time.Instant

/**
 * Domain model representing a user in the Health Tracker system.
 * 
 * @property id Unique identifier for the user
 * @property profile User's profile information
 * @property settings User's app settings
 * @property createdAt Timestamp when the user was created
 * @property updatedAt Timestamp when the user was last updated
 */
data class User(
    val id: String,
    val profile: UserProfile,
    val settings: UserSettings,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * User profile containing personal information.
 * 
 * @property name User's display name
 * @property age User's age in years (must be >= 13)
 * @property weight User's weight in kilograms (20-500 kg)
 * @property height User's height in centimeters (50-300 cm)
 * @property goal User's primary health goal
 * @property dietPreference User's dietary preference (optional)
 */
data class UserProfile(
    val name: String,
    val age: Int,
    val weight: Float,
    val height: Float,
    val goal: HealthGoal,
    val dietPreference: DietPreference? = null
)

/**
 * User's primary health goal.
 */
enum class HealthGoal {
    WEIGHT_LOSS,
    FITNESS,
    GENERAL
}

/**
 * User's dietary preference for meal planning.
 */
enum class DietPreference {
    VEGETARIAN,
    NON_VEGETARIAN
}

/**
 * User settings for app behavior and notifications.
 * 
 * @property notificationsEnabled Whether push notifications are enabled
 * @property dataCollectionEnabled Whether background data collection is enabled
 * @property sensitiveDataOptIn Whether user has opted into sensitive data collection
 * @property hydrationReminders Whether hydration reminders are enabled
 * @property mindfulnessReminders Whether mindfulness reminders are enabled
 * @property reminderIntervalMinutes Interval between reminders in minutes
 */
data class UserSettings(
    val notificationsEnabled: Boolean = true,
    val dataCollectionEnabled: Boolean = true,
    val sensitiveDataOptIn: Boolean = false,
    val hydrationReminders: Boolean = true,
    val mindfulnessReminders: Boolean = true,
    val reminderIntervalMinutes: Int = 120
)
