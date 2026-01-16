package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.HydrationSchedule
import com.healthtracker.domain.model.HydrationSummary
import com.healthtracker.domain.model.PlanGenerationResult
import com.healthtracker.domain.model.WaterIntake
import com.healthtracker.domain.model.WeatherCondition
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Use case interface for personalized planning features.
 * 
 * Handles workout plans, diet plans, and hydration schedules
 * with goal alignment and preference compliance.
 */
interface PlanningUseCase {
    
    // ============================================
    // WORKOUT PLANS
    // ============================================
    
    /**
     * Gets the current workout plan.
     * 
     * @param type The workout type (Home or Gym)
     * @return Flow emitting the workout plan
     */
    fun getWorkoutPlan(type: WorkoutType): Flow<WorkoutPlan?>
    
    /**
     * Gets both Home and Gym workout plans.
     * 
     * @return Flow emitting pair of (Home plan, Gym plan)
     */
    fun getBothWorkoutPlans(): Flow<Pair<WorkoutPlan?, WorkoutPlan?>>
    
    /**
     * Generates a new workout plan based on user's goal.
     * 
     * @param type The workout type to generate
     * @return The generated workout plan
     */
    suspend fun generateWorkoutPlan(type: WorkoutType): WorkoutPlan
    
    // ============================================
    // DIET PLANS
    // ============================================
    
    /**
     * Gets the current diet plan.
     * 
     * @return Flow emitting the diet plan
     */
    fun getDietPlan(): Flow<DietPlan?>
    
    /**
     * Generates a new diet plan based on user's preferences.
     * 
     * @return The generated diet plan
     */
    suspend fun generateDietPlan(): DietPlan
    
    // ============================================
    // HYDRATION
    // ============================================
    
    /**
     * Gets the current hydration schedule.
     * 
     * @return Flow emitting the hydration schedule
     */
    fun getHydrationSchedule(): Flow<HydrationSchedule?>
    
    /**
     * Generates a new hydration schedule.
     * 
     * @param weather Optional weather condition for adjustment
     * @return The generated hydration schedule
     */
    suspend fun generateHydrationSchedule(weather: WeatherCondition? = null): HydrationSchedule
    
    /**
     * Logs water intake.
     * 
     * @param amountMl Amount of water in milliliters
     * @return The logged water intake record
     */
    suspend fun logWaterIntake(amountMl: Int): WaterIntake
    
    /**
     * Gets today's hydration summary.
     * 
     * @return Flow emitting the hydration summary
     */
    fun getTodayHydrationSummary(): Flow<HydrationSummary>
    
    /**
     * Gets hydration summary for a specific date.
     * 
     * @param date The date to get summary for
     * @return Flow emitting the hydration summary
     */
    fun getHydrationSummary(date: LocalDate): Flow<HydrationSummary>
    
    // ============================================
    // PLAN MANAGEMENT
    // ============================================
    
    /**
     * Regenerates all plans based on current progress.
     * 
     * @param weather Optional weather condition for hydration adjustment
     * @return Result of plan generation
     */
    suspend fun regenerateAllPlans(weather: WeatherCondition? = null): PlanGenerationResult
    
    /**
     * Checks if plans need regeneration.
     * 
     * @return True if plans should be regenerated
     */
    suspend fun shouldRegeneratePlans(): Boolean
}
