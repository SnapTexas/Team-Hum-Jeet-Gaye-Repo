package com.healthtracker.domain.repository

import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.HydrationSchedule
import com.healthtracker.domain.model.HydrationSummary
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.WaterIntake
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for personalized planning features.
 * 
 * Handles workout plans, diet plans, and hydration schedules.
 */
interface PlanRepository {
    
    // ============================================
    // WORKOUT PLAN
    // ============================================
    
    /**
     * Gets the current workout plan for the user.
     * 
     * @param userId The user's ID
     * @param type The workout type (Home or Gym)
     * @return Flow emitting the workout plan or null if none exists
     */
    fun getWorkoutPlan(userId: String, type: WorkoutType): Flow<WorkoutPlan?>
    
    /**
     * Gets all available workout plans for the user.
     * 
     * @param userId The user's ID
     * @return Flow emitting list of workout plans
     */
    fun getAllWorkoutPlans(userId: String): Flow<List<WorkoutPlan>>
    
    /**
     * Generates a new workout plan.
     * 
     * @param input Plan generation input with user profile and metrics
     * @param type The workout type to generate
     * @return The generated workout plan
     */
    suspend fun generateWorkoutPlan(input: PlanGenerationInput, type: WorkoutType): WorkoutPlan
    
    /**
     * Saves a workout plan.
     * 
     * @param plan The workout plan to save
     */
    suspend fun saveWorkoutPlan(plan: WorkoutPlan)
    
    // ============================================
    // DIET PLAN
    // ============================================
    
    /**
     * Gets the current diet plan for the user.
     * 
     * @param userId The user's ID
     * @return Flow emitting the diet plan or null if none exists
     */
    fun getDietPlan(userId: String): Flow<DietPlan?>
    
    /**
     * Generates a new diet plan.
     * 
     * @param input Plan generation input with user profile and metrics
     * @return The generated diet plan
     */
    suspend fun generateDietPlan(input: PlanGenerationInput): DietPlan
    
    /**
     * Saves a diet plan.
     * 
     * @param plan The diet plan to save
     */
    suspend fun saveDietPlan(plan: DietPlan)
    
    // ============================================
    // HYDRATION SCHEDULE
    // ============================================
    
    /**
     * Gets the current hydration schedule for the user.
     * 
     * @param userId The user's ID
     * @return Flow emitting the hydration schedule or null if none exists
     */
    fun getHydrationSchedule(userId: String): Flow<HydrationSchedule?>
    
    /**
     * Generates a new hydration schedule.
     * 
     * @param input Plan generation input with user profile and metrics
     * @return The generated hydration schedule
     */
    suspend fun generateHydrationSchedule(input: PlanGenerationInput): HydrationSchedule
    
    /**
     * Saves a hydration schedule.
     * 
     * @param schedule The hydration schedule to save
     */
    suspend fun saveHydrationSchedule(schedule: HydrationSchedule)
    
    /**
     * Logs water intake.
     * 
     * @param userId The user's ID
     * @param amountMl Amount of water in milliliters
     * @return The logged water intake record
     */
    suspend fun logWaterIntake(userId: String, amountMl: Int): WaterIntake
    
    /**
     * Gets hydration summary for a date.
     * 
     * @param userId The user's ID
     * @param date The date to get summary for
     * @return Flow emitting the hydration summary
     */
    fun getHydrationSummary(userId: String, date: LocalDate): Flow<HydrationSummary>
    
    /**
     * Gets water intake logs for a date.
     * 
     * @param userId The user's ID
     * @param date The date to get logs for
     * @return Flow emitting list of water intake logs
     */
    fun getWaterIntakeLogs(userId: String, date: LocalDate): Flow<List<WaterIntake>>
    
    // ============================================
    // PLAN REGENERATION
    // ============================================
    
    /**
     * Regenerates all plans for the user.
     * 
     * @param input Plan generation input with user profile and metrics
     */
    suspend fun regenerateAllPlans(input: PlanGenerationInput)
    
    /**
     * Checks if plans need regeneration based on user progress.
     * 
     * @param userId The user's ID
     * @return True if plans should be regenerated
     */
    suspend fun shouldRegeneratePlans(userId: String): Boolean
}
