package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.BreathingExercise
import com.healthtracker.domain.model.BreathingExerciseState
import com.healthtracker.domain.model.DailyStressSummary
import com.healthtracker.domain.model.MeditationSession
import com.healthtracker.domain.model.MeditationState
import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressInput
import com.healthtracker.domain.model.StressTrend
import com.healthtracker.domain.model.WellnessActivity
import com.healthtracker.domain.model.WellnessActivityType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Use case interface for mental health and stress management.
 * 
 * Handles stress assessment, breathing exercises, meditation,
 * and mindfulness reminders.
 */
interface MentalHealthUseCase {
    
    // ============================================
    // STRESS ASSESSMENT
    // ============================================
    
    /**
     * Calculates stress level from multiple inputs.
     * 
     * Combines HRV, sleep, and mood data with weighted algorithm:
     * - HRV: 40% weight (lower HRV = higher stress)
     * - Sleep: 35% weight (poor sleep = higher stress)
     * - Mood: 25% weight (negative mood = higher stress)
     * 
     * @param input The stress input data
     * @return Stress assessment with level 0-100
     */
    suspend fun calculateStress(input: StressInput): StressAssessment
    
    /**
     * Gets the current stress assessment based on latest data.
     * 
     * @return Current stress assessment
     */
    suspend fun getCurrentStressAssessment(): StressAssessment
    
    /**
     * Gets stress assessments for today.
     * 
     * @return List of today's assessments
     */
    suspend fun getTodayStressAssessments(): List<StressAssessment>
    
    /**
     * Gets daily stress summary.
     * 
     * @param date The date to query
     * @return Daily stress summary
     */
    suspend fun getDailyStressSummary(date: LocalDate): DailyStressSummary?
    
    /**
     * Gets stress trend over a period.
     * 
     * @param days Number of days to include
     * @return Stress trend data
     */
    suspend fun getStressTrend(days: Int = 7): StressTrend
    
    // ============================================
    // BREATHING EXERCISES
    // ============================================
    
    /**
     * Gets all available breathing exercises.
     * 
     * @return List of breathing exercises
     */
    fun getBreathingExercises(): List<BreathingExercise>
    
    /**
     * Gets a specific breathing exercise.
     * 
     * @param exerciseId The exercise ID
     * @return The exercise or null
     */
    fun getBreathingExercise(exerciseId: String): BreathingExercise?
    
    /**
     * Gets recommended breathing exercise based on stress level.
     * 
     * @param stressLevel Current stress level
     * @return Recommended exercise
     */
    fun getRecommendedBreathingExercise(stressLevel: Int): BreathingExercise
    
    /**
     * Starts a breathing exercise session.
     * 
     * @param exercise The exercise to start
     * @return Flow of exercise state updates
     */
    fun startBreathingExercise(exercise: BreathingExercise): Flow<BreathingExerciseState>
    
    /**
     * Completes a breathing exercise and logs it.
     * 
     * @param exercise The completed exercise
     * @param durationSeconds Actual duration
     * @param stressLevelBefore Stress level before exercise
     * @param stressLevelAfter Stress level after exercise
     * @return The logged activity
     */
    suspend fun completeBreathingExercise(
        exercise: BreathingExercise,
        durationSeconds: Int,
        stressLevelBefore: Int? = null,
        stressLevelAfter: Int? = null
    ): WellnessActivity
    
    // ============================================
    // MEDITATION
    // ============================================
    
    /**
     * Gets all available meditation sessions.
     * 
     * @return List of meditation sessions
     */
    fun getMeditationSessions(): List<MeditationSession>
    
    /**
     * Gets meditation sessions by category.
     * 
     * @param category The category to filter
     * @return List of sessions in the category
     */
    fun getMeditationSessionsByCategory(
        category: com.healthtracker.domain.model.MeditationCategory
    ): List<MeditationSession>
    
    /**
     * Gets a specific meditation session.
     * 
     * @param sessionId The session ID
     * @return The session or null
     */
    fun getMeditationSession(sessionId: String): MeditationSession?
    
    /**
     * Starts a meditation session.
     * 
     * @param session The session to start
     * @return Flow of meditation state updates
     */
    fun startMeditationSession(session: MeditationSession): Flow<MeditationState>
    
    /**
     * Completes a meditation session and logs it.
     * 
     * @param session The completed session
     * @param durationSeconds Actual duration
     * @param stressLevelBefore Stress level before meditation
     * @param stressLevelAfter Stress level after meditation
     * @return The logged activity
     */
    suspend fun completeMeditationSession(
        session: MeditationSession,
        durationSeconds: Int,
        stressLevelBefore: Int? = null,
        stressLevelAfter: Int? = null
    ): WellnessActivity
    
    // ============================================
    // WELLNESS ACTIVITIES
    // ============================================
    
    /**
     * Gets today's wellness activities.
     * 
     * @return List of today's activities
     */
    suspend fun getTodayWellnessActivities(): List<WellnessActivity>
    
    /**
     * Gets wellness activities for a date range.
     * 
     * @param startDate Start of the range
     * @param endDate End of the range
     * @return List of activities
     */
    suspend fun getWellnessActivities(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WellnessActivity>
    
    /**
     * Gets total wellness minutes for today.
     * 
     * @return Total minutes
     */
    suspend fun getTodayWellnessMinutes(): Int
    
    /**
     * Logs a custom wellness activity.
     * 
     * @param type Activity type
     * @param name Activity name
     * @param durationSeconds Duration in seconds
     * @param notes Optional notes
     * @return The logged activity
     */
    suspend fun logWellnessActivity(
        type: WellnessActivityType,
        name: String,
        durationSeconds: Int,
        notes: String? = null
    ): WellnessActivity
    
    // ============================================
    // MINDFULNESS REMINDERS
    // ============================================
    
    /**
     * Gets all mindfulness reminders.
     * 
     * @return Flow of reminders
     */
    fun getReminders(): Flow<List<MindfulnessReminder>>
    
    /**
     * Creates a new mindfulness reminder.
     * 
     * @param reminder The reminder to create
     * @return The created reminder
     */
    suspend fun createReminder(reminder: MindfulnessReminder): MindfulnessReminder
    
    /**
     * Updates a reminder.
     * 
     * @param reminder The updated reminder
     * @return The updated reminder
     */
    suspend fun updateReminder(reminder: MindfulnessReminder): MindfulnessReminder
    
    /**
     * Deletes a reminder.
     * 
     * @param reminderId The reminder ID to delete
     */
    suspend fun deleteReminder(reminderId: String)
    
    /**
     * Toggles reminder enabled state.
     * 
     * @param reminderId The reminder ID
     * @param enabled Whether to enable
     */
    suspend fun toggleReminder(reminderId: String, enabled: Boolean)
    
    /**
     * Schedules reminders for the day.
     * 
     * Called by WorkManager to schedule daily reminders.
     */
    suspend fun scheduleDailyReminders()
}
