package com.healthtracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthtracker.data.local.dao.AnomalyDao
import com.healthtracker.data.local.dao.GamificationDao
import com.healthtracker.data.local.dao.HealthMetricsDao
import com.healthtracker.data.local.dao.MedicalDao
import com.healthtracker.data.local.dao.PrivacyDao
import com.healthtracker.data.local.dao.SocialDao
import com.healthtracker.data.local.dao.SuggestionDao
import com.healthtracker.data.local.dao.UserBaselineDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.local.entity.AnomalyEntity
import com.healthtracker.data.local.entity.AppointmentReminderDetailsEntity
import com.healthtracker.data.local.entity.AuditLogEntity
import com.healthtracker.data.local.entity.CircleChallengeEntity
import com.healthtracker.data.local.entity.CircleInvitationEntity
import com.healthtracker.data.local.entity.CircleLeaderboardCacheEntity
import com.healthtracker.data.local.entity.CircleMemberEntity
import com.healthtracker.data.local.entity.CirclePrivacySettingsEntity
import com.healthtracker.data.local.entity.CircleProgressEntity
import com.healthtracker.data.local.entity.HealthCircleEntity
import com.healthtracker.data.local.entity.HealthMetricsEntity
import com.healthtracker.data.local.entity.HealthReminderEntity
import com.healthtracker.data.local.entity.MedicalRecordEntity
import com.healthtracker.data.local.entity.MedicineReminderDetailsEntity
import com.healthtracker.data.local.entity.PointsHistoryEntity
import com.healthtracker.data.local.entity.PrivacySettingsEntity
import com.healthtracker.data.local.entity.ScheduledReminderEntity
import com.healthtracker.data.local.entity.StreakEntity
import com.healthtracker.data.local.entity.SuggestionEntity
import com.healthtracker.data.local.entity.UnlockedBadgeEntity
import com.healthtracker.data.local.entity.UserBaselineEntity
import com.healthtracker.data.local.entity.UserEntity
import com.healthtracker.data.local.entity.UserProgressEntity
import com.healthtracker.data.local.entity.VaccinationReminderDetailsEntity
import com.healthtracker.data.local.converter.Converters

/**
 * Room database for Health Tracker local storage.
 * 
 * Contains tables for:
 * - Users (profile and settings)
 * - Health metrics (daily data)
 * - User baselines (for anomaly detection)
 * - Anomalies (detected unusual patterns)
 * - Suggestions (AI-generated daily suggestions)
 * - Gamification (progress, streaks, badges, points)
 * 
 * Uses encrypted storage for sensitive health data.
 */
@Database(
    entities = [
        UserEntity::class,
        HealthMetricsEntity::class,
        UserBaselineEntity::class,
        AnomalyEntity::class,
        SuggestionEntity::class,
        UserProgressEntity::class,
        StreakEntity::class,
        UnlockedBadgeEntity::class,
        PointsHistoryEntity::class,
        // Social entities
        HealthCircleEntity::class,
        CircleMemberEntity::class,
        CirclePrivacySettingsEntity::class,
        CircleInvitationEntity::class,
        CircleChallengeEntity::class,
        CircleLeaderboardCacheEntity::class,
        CircleProgressEntity::class,
        // Medical entities
        MedicalRecordEntity::class,
        HealthReminderEntity::class,
        ScheduledReminderEntity::class,
        MedicineReminderDetailsEntity::class,
        VaccinationReminderDetailsEntity::class,
        AppointmentReminderDetailsEntity::class,
        // Privacy entities
        PrivacySettingsEntity::class,
        AuditLogEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HealthTrackerDatabase : RoomDatabase() {
    
    /**
     * DAO for user operations.
     */
    abstract fun userDao(): UserDao
    
    /**
     * DAO for health metrics operations.
     */
    abstract fun healthMetricsDao(): HealthMetricsDao
    
    /**
     * DAO for user baseline operations.
     */
    abstract fun userBaselineDao(): UserBaselineDao
    
    /**
     * DAO for anomaly operations.
     */
    abstract fun anomalyDao(): AnomalyDao
    
    /**
     * DAO for suggestion operations.
     */
    abstract fun suggestionDao(): SuggestionDao
    
    /**
     * DAO for gamification operations.
     */
    abstract fun gamificationDao(): GamificationDao
    
    /**
     * DAO for social/circle operations.
     */
    abstract fun socialDao(): SocialDao
    
    /**
     * DAO for medical records and reminders.
     */
    abstract fun medicalDao(): MedicalDao
    
    /**
     * DAO for privacy settings and audit logs.
     */
    abstract fun privacyDao(): PrivacyDao
    
    companion object {
        const val DATABASE_NAME = "health_tracker_db"
    }
}
