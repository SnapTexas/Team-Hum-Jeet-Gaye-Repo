package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.ActivityLevel
import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.Difficulty
import com.healthtracker.domain.model.Exercise
import com.healthtracker.domain.model.ExerciseCategory
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HydrationReminder
import com.healthtracker.domain.model.HydrationSchedule
import com.healthtracker.domain.model.HydrationSummary
import com.healthtracker.domain.model.MacroTargets
import com.healthtracker.domain.model.MealSuggestion
import com.healthtracker.domain.model.MealType
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.PlanGenerationResult
import com.healthtracker.domain.model.WaterIntake
import com.healthtracker.domain.model.WeatherCondition
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.domain.model.toActivityLevel
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.PlanningUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlanningUseCase.
 * 
 * Generates personalized workout plans, diet plans, and hydration schedules
 * based on user goals, preferences, and activity levels.
 */
@Singleton
class PlanningUseCaseImpl @Inject constructor(
    private val planRepository: PlanRepository,
    private val userRepository: UserRepository,
    private val healthDataRepository: HealthDataRepository
) : PlanningUseCase {
    
    // Default user ID - in production, get from auth
    private val userId = "default_user"
    
    companion object {
        // Hydration constants
        private const val BASE_WATER_ML = 2000
        private const val ACTIVITY_MULTIPLIER_LIGHT = 1.1f
        private const val ACTIVITY_MULTIPLIER_MODERATE = 1.2f
        private const val ACTIVITY_MULTIPLIER_ACTIVE = 1.3f
        private const val ACTIVITY_MULTIPLIER_VERY_ACTIVE = 1.5f
        private const val WEATHER_HOT_MULTIPLIER = 1.2f
        private const val WEATHER_HUMID_MULTIPLIER = 1.1f
        
        // Plan expiration
        private const val PLAN_VALIDITY_DAYS = 7L
    }
    
    // ============================================
    // WORKOUT PLANS
    // ============================================
    
    override fun getWorkoutPlan(type: WorkoutType): Flow<WorkoutPlan?> {
        return planRepository.getWorkoutPlan(userId, type)
    }
    
    override fun getBothWorkoutPlans(): Flow<Pair<WorkoutPlan?, WorkoutPlan?>> {
        return combine(
            planRepository.getWorkoutPlan(userId, WorkoutType.HOME),
            planRepository.getWorkoutPlan(userId, WorkoutType.GYM)
        ) { home, gym -> Pair(home, gym) }
    }
    
    override suspend fun generateWorkoutPlan(type: WorkoutType): WorkoutPlan {
        val user = userRepository.getUser().firstOrNull()
            ?: throw IllegalStateException("User not found")
        
        val recentMetrics = healthDataRepository.getHealthMetricsRange(
            LocalDate.now().minusDays(7),
            LocalDate.now()
        ).firstOrNull() ?: emptyList()
        
        val input = PlanGenerationInput(
            userId = userId,
            profile = user.profile,
            recentMetrics = recentMetrics
        )
        
        val plan = planRepository.generateWorkoutPlan(input, type)
        planRepository.saveWorkoutPlan(plan)
        return plan
    }
    
    // ============================================
    // DIET PLANS
    // ============================================
    
    override fun getDietPlan(): Flow<DietPlan?> {
        return planRepository.getDietPlan(userId)
    }
    
    override suspend fun generateDietPlan(): DietPlan {
        val user = userRepository.getUser().firstOrNull()
            ?: throw IllegalStateException("User not found")
        
        val recentMetrics = healthDataRepository.getHealthMetricsRange(
            LocalDate.now().minusDays(7),
            LocalDate.now()
        ).firstOrNull() ?: emptyList()
        
        val input = PlanGenerationInput(
            userId = userId,
            profile = user.profile,
            recentMetrics = recentMetrics
        )
        
        val plan = planRepository.generateDietPlan(input)
        planRepository.saveDietPlan(plan)
        return plan
    }
    
    // ============================================
    // HYDRATION
    // ============================================
    
    override fun getHydrationSchedule(): Flow<HydrationSchedule?> {
        return planRepository.getHydrationSchedule(userId)
    }
    
    override suspend fun generateHydrationSchedule(weather: WeatherCondition?): HydrationSchedule {
        val user = userRepository.getUser().firstOrNull()
            ?: throw IllegalStateException("User not found")
        
        val recentMetrics = healthDataRepository.getHealthMetricsRange(
            LocalDate.now().minusDays(7),
            LocalDate.now()
        ).firstOrNull() ?: emptyList()
        
        val input = PlanGenerationInput(
            userId = userId,
            profile = user.profile,
            recentMetrics = recentMetrics,
            currentWeather = weather
        )
        
        val schedule = planRepository.generateHydrationSchedule(input)
        planRepository.saveHydrationSchedule(schedule)
        return schedule
    }
    
    override suspend fun logWaterIntake(amountMl: Int): WaterIntake {
        return planRepository.logWaterIntake(userId, amountMl)
    }
    
    override fun getTodayHydrationSummary(): Flow<HydrationSummary> {
        return planRepository.getHydrationSummary(userId, LocalDate.now())
    }
    
    override fun getHydrationSummary(date: LocalDate): Flow<HydrationSummary> {
        return planRepository.getHydrationSummary(userId, date)
    }
    
    // ============================================
    // PLAN MANAGEMENT
    // ============================================
    
    override suspend fun regenerateAllPlans(weather: WeatherCondition?): PlanGenerationResult {
        return try {
            val user = userRepository.getUser().firstOrNull()
                ?: return PlanGenerationResult.Error("User not found")
            
            val recentMetrics = healthDataRepository.getHealthMetricsRange(
                LocalDate.now().minusDays(7),
                LocalDate.now()
            ).firstOrNull() ?: emptyList()
            
            val input = PlanGenerationInput(
                userId = userId,
                profile = user.profile,
                recentMetrics = recentMetrics,
                currentWeather = weather
            )
            
            planRepository.regenerateAllPlans(input)
            
            val homePlan = planRepository.getWorkoutPlan(userId, WorkoutType.HOME).firstOrNull()
            val dietPlan = planRepository.getDietPlan(userId).firstOrNull()
            val hydrationSchedule = planRepository.getHydrationSchedule(userId).firstOrNull()
            
            PlanGenerationResult.Success(
                workoutPlan = homePlan,
                dietPlan = dietPlan,
                hydrationSchedule = hydrationSchedule
            )
        } catch (e: Exception) {
            PlanGenerationResult.Error("Failed to regenerate plans: ${e.message}")
        }
    }
    
    override suspend fun shouldRegeneratePlans(): Boolean {
        return planRepository.shouldRegeneratePlans(userId)
    }
}
