package com.healthtracker.data.mapper

import com.healthtracker.data.local.entity.UserEntity
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.UserSettings
import java.time.Instant

/**
 * Mapper functions for converting between User domain model and UserEntity.
 */
object UserMapper {
    
    /**
     * Converts UserEntity to User domain model.
     */
    fun UserEntity.toDomain(): User {
        return User(
            id = id,
            profile = UserProfile(
                name = name,
                age = age,
                weight = weightKg,
                height = heightCm,
                goal = HealthGoal.valueOf(goal),
                dietPreference = dietPreference?.let { DietPreference.valueOf(it) }
            ),
            settings = UserSettings(
                notificationsEnabled = notificationsEnabled,
                dataCollectionEnabled = dataCollectionEnabled,
                sensitiveDataOptIn = sensitiveDataOptIn,
                hydrationReminders = hydrationReminders,
                mindfulnessReminders = mindfulnessReminders,
                reminderIntervalMinutes = reminderIntervalMinutes
            ),
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )
    }
    
    /**
     * Converts User domain model to UserEntity.
     */
    fun User.toEntity(needsSync: Boolean = false): UserEntity {
        return UserEntity(
            id = id,
            name = profile.name,
            age = profile.age,
            weightKg = profile.weight,
            heightCm = profile.height,
            goal = profile.goal.name,
            dietPreference = profile.dietPreference?.name,
            notificationsEnabled = settings.notificationsEnabled,
            dataCollectionEnabled = settings.dataCollectionEnabled,
            sensitiveDataOptIn = settings.sensitiveDataOptIn,
            hydrationReminders = settings.hydrationReminders,
            mindfulnessReminders = settings.mindfulnessReminders,
            reminderIntervalMinutes = settings.reminderIntervalMinutes,
            createdAt = createdAt.toEpochMilli(),
            updatedAt = updatedAt.toEpochMilli(),
            needsSync = needsSync
        )
    }
    
    /**
     * Creates a new UserEntity from UserProfile (for new user creation).
     */
    fun createUserEntity(
        id: String,
        profile: UserProfile,
        settings: UserSettings = UserSettings()
    ): UserEntity {
        val now = System.currentTimeMillis()
        return UserEntity(
            id = id,
            name = profile.name,
            age = profile.age,
            weightKg = profile.weight,
            heightCm = profile.height,
            goal = profile.goal.name,
            dietPreference = profile.dietPreference?.name,
            notificationsEnabled = settings.notificationsEnabled,
            dataCollectionEnabled = settings.dataCollectionEnabled,
            sensitiveDataOptIn = settings.sensitiveDataOptIn,
            hydrationReminders = settings.hydrationReminders,
            mindfulnessReminders = settings.mindfulnessReminders,
            reminderIntervalMinutes = settings.reminderIntervalMinutes,
            createdAt = now,
            updatedAt = now,
            needsSync = true // New users need to be synced
        )
    }
}
