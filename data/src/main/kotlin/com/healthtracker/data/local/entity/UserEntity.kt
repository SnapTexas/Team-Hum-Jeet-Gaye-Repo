package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing user data locally.
 * 
 * This entity stores the user profile and settings for offline access.
 * Syncs with Firebase when online.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    
    // Profile fields
    val name: String,
    val age: Int,
    val weightKg: Float,
    val heightCm: Float,
    val goal: String, // HealthGoal enum as string
    val dietPreference: String?, // DietPreference enum as string, nullable
    
    // Settings fields
    val notificationsEnabled: Boolean = true,
    val dataCollectionEnabled: Boolean = true,
    val sensitiveDataOptIn: Boolean = false,
    val hydrationReminders: Boolean = true,
    val mindfulnessReminders: Boolean = true,
    val reminderIntervalMinutes: Int = 120,
    
    // Timestamps
    val createdAt: Long, // Epoch millis
    val updatedAt: Long, // Epoch millis
    
    // Sync status
    val needsSync: Boolean = false
)
