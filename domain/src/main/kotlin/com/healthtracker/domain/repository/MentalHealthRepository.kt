package com.healthtracker.domain.repository

import com.healthtracker.domain.model.DailyStressSummary
import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.MentalScheduledReminder
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressTrend
import com.healthtracker.domain.model.WellnessActivity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for mental health and stress management operations.
 * 
 * Handles stress assessments, wellness activities, and mindfulness reminders.
 */
interface MentalHealthRepository {
    
    // ============================================
    // STRESS ASSESSMENTS
    // ============================================
    
    /**
     * Saves a stress assessment.
     * 
     * @param assessment The stress assessment to save
     */
    suspend fun saveStressAssessment(assessment: StressAssessment)
    
    /**
     * Gets stress assessments for a date.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return List of assessments for the date
     */
    suspend fun getStressAssessments(userId: String, date: LocalDate): List<StressAssessment>
    
    /**
     * Gets the latest stress assessment.
     * 
     * @param userId The user ID
     * @return The latest assessment or null
     */
    suspend fun getLatestStressAssessment(userId: String): StressAssessment?
    
    /**
     * Gets daily stress summary.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return Daily stress summary
     */
    suspend fun getDailyStressSummary(userId: String, date: LocalDate): DailyStressSummary?
    
    /**
     * Gets stress trend over a date range.
     * 
     * @param userId The user ID
     * @param startDate Start of the range
     * @param endDate End of the range
     * @return Stress trend data
     */
    suspend fun getStressTrend(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): StressTrend
    
    // ============================================
    // WELLNESS ACTIVITIES
    // ============================================
    
    /**
     * Logs a completed wellness activity.
     * 
     * @param activity The activity to log
     * @return The logged activity with ID
     */
    suspend fun logWellnessActivity(activity: WellnessActivity): WellnessActivity
    
    /**
     * Gets wellness activities for a date.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return List of activities for the date
     */
    suspend fun getWellnessActivities(userId: String, date: LocalDate): List<WellnessActivity>
    
    /**
     * Gets wellness activities for a date range.
     * 
     * @param userId The user ID
     * @param startDate Start of the range
     * @param endDate End of the range
     * @return List of activities in the range
     */
    suspend fun getWellnessActivitiesRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WellnessActivity>
    
    /**
     * Gets total wellness minutes for a date.
     * 
     * @param userId The user ID
     * @param date The date to query
     * @return Total minutes spent on wellness activities
     */
    suspend fun getTotalWellnessMinutes(userId: String, date: LocalDate): Int
    
    // ============================================
    // MINDFULNESS REMINDERS
    // ============================================
    
    /**
     * Saves a mindfulness reminder configuration.
     * 
     * @param reminder The reminder to save
     * @return The saved reminder with ID
     */
    suspend fun saveReminder(reminder: MindfulnessReminder): MindfulnessReminder
    
    /**
     * Gets all reminders for a user.
     * 
     * @param userId The user ID
     * @return Flow of reminders
     */
    fun getReminders(userId: String): Flow<List<MindfulnessReminder>>
    
    /**
     * Gets a specific reminder.
     * 
     * @param reminderId The reminder ID
     * @return The reminder or null
     */
    suspend fun getReminder(reminderId: String): MindfulnessReminder?
    
    /**
     * Deletes a reminder.
     * 
     * @param reminderId The reminder ID to delete
     */
    suspend fun deleteReminder(reminderId: String)
    
    /**
     * Updates reminder enabled state.
     * 
     * @param reminderId The reminder ID
     * @param enabled Whether the reminder is enabled
     */
    suspend fun updateReminderEnabled(reminderId: String, enabled: Boolean)
    
    /**
     * Schedules a reminder instance.
     * 
     * @param scheduledReminder The scheduled reminder
     */
    suspend fun scheduleReminder(scheduledReminder: MentalScheduledReminder)
    
    /**
     * Marks a scheduled reminder as delivered.
     * 
     * @param scheduledReminderId The scheduled reminder ID
     */
    suspend fun markReminderDelivered(scheduledReminderId: String)
    
    /**
     * Gets pending scheduled reminders.
     * 
     * @param userId The user ID
     * @return List of pending reminders
     */
    suspend fun getPendingReminders(userId: String): List<MentalScheduledReminder>
}
