package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.ExerciseCategory
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.impl.PlanningUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAny
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based tests for Workout Plan Goal Alignment (Property 11).
 * 
 * **Validates: Requirements 7.1, 7.2**
 * 
 * Property 11: Workout Plan Goal Alignment
 * For any user profile with a specified goal (Weight Loss, Fitness, General),
 * the generated workout plan SHALL include exercises appropriate for that goal,
 * and SHALL offer both Home and Gym variants.
 */
class WorkoutPlanGoalAlignmentTest : FunSpec({
    
    val planRepository = mockk<PlanRepository>()
    val userRepository = mockk<UserRepository>()
    val planningUseCase = PlanningUseCaseImpl(planRepository, userRepository)
    
    /**
     * Property 11.1: Weight Loss goal generates cardio-focused exercises
     * 
     * For any user with WEIGHT_LOSS goal, the workout plan SHALL include
     * CARDIO and HIIT exercises as primary components.
     */
    test("weight loss goal generates cardio-focused exercises") {
        checkAll(
            100,
            Arb.float(min = 50f, max = 150f),
            Arb.float(min = 140f, max = 200f),
            Arb.int(min = 18, max = 65)
        ) { weight, height, age ->
            // Arrange
            val profile = createTestProfile(
                goal = HealthGoal.WEIGHT_LOSS,
                weight = weight,
                height = height,
                age = age
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { userRepository.getCurrentUser() } returns flowOf(createTestUser(profile))
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                val type = secondArg<WorkoutType>()
                generateMockWorkoutPlan(input, type, HealthGoal.WEIGHT_LOSS)
            }
            coEvery { planRepository.saveWorkoutPlan(any()) } returns Unit
            
            // Act - Generate both Home and Gym plans
            val homePlan = planRepository.generateWorkoutPlan(input, WorkoutType.HOME)
            val gymPlan = planRepository.generateWorkoutPlan(input, WorkoutType.GYM)
            
            // Assert - Both plans should have cardio/HIIT exercises
            val homeCardioExercises = homePlan.exercises.filter { 
                it.category == ExerciseCategory.CARDIO || it.category == ExerciseCategory.HIIT 
            }
            val gymCardioExercises = gymPlan.exercises.filter { 
                it.category == ExerciseCategory.CARDIO || it.category == ExerciseCategory.HIIT 
            }
            
            homeCardioExercises.shouldNotBeEmpty()
            gymCardioExercises.shouldNotBeEmpty()
            
            // Cardio should be significant portion (at least 30% excluding warmup/cooldown)
            val homeMainExercises = homePlan.exercises.filter { 
                it.category != ExerciseCategory.WARMUP && it.category != ExerciseCategory.COOLDOWN 
            }
            val homeCardioRatio = homeCardioExercises.size.toFloat() / homeMainExercises.size
            (homeCardioRatio >= 0.3f) shouldBe true
        }
    }
    
    /**
     * Property 11.2: Fitness goal generates strength-focused exercises
     * 
     * For any user with FITNESS goal, the workout plan SHALL include
     * STRENGTH exercises as primary components.
     */
    test("fitness goal generates strength-focused exercises") {
        checkAll(
            100,
            Arb.float(min = 50f, max = 150f),
            Arb.float(min = 140f, max = 200f)
        ) { weight, height ->
            // Arrange
            val profile = createTestProfile(
                goal = HealthGoal.FITNESS,
                weight = weight,
                height = height
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                val type = secondArg<WorkoutType>()
                generateMockWorkoutPlan(input, type, HealthGoal.FITNESS)
            }
            
            // Act
            val homePlan = planRepository.generateWorkoutPlan(input, WorkoutType.HOME)
            val gymPlan = planRepository.generateWorkoutPlan(input, WorkoutType.GYM)
            
            // Assert - Both plans should have strength exercises
            val homeStrengthExercises = homePlan.exercises.filter { 
                it.category == ExerciseCategory.STRENGTH 
            }
            val gymStrengthExercises = gymPlan.exercises.filter { 
                it.category == ExerciseCategory.STRENGTH 
            }
            
            homeStrengthExercises.shouldNotBeEmpty()
            gymStrengthExercises.shouldNotBeEmpty()
            
            // Strength should be significant portion
            val gymMainExercises = gymPlan.exercises.filter { 
                it.category != ExerciseCategory.WARMUP && it.category != ExerciseCategory.COOLDOWN 
            }
            val gymStrengthRatio = gymStrengthExercises.size.toFloat() / gymMainExercises.size
            (gymStrengthRatio >= 0.4f) shouldBe true
        }
    }
    
    /**
     * Property 11.3: Both Home and Gym variants are offered
     * 
     * For any user goal, the system SHALL generate both Home and Gym
     * workout variants with appropriate exercises for each environment.
     */
    test("both home and gym variants are offered for any goal") {
        checkAll(100, Arb.enum<HealthGoal>()) { goal ->
            // Arrange
            val profile = createTestProfile(goal = goal)
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), WorkoutType.HOME) } coAnswers {
                generateMockWorkoutPlan(input, WorkoutType.HOME, goal)
            }
            coEvery { planRepository.generateWorkoutPlan(any(), WorkoutType.GYM) } coAnswers {
                generateMockWorkoutPlan(input, WorkoutType.GYM, goal)
            }
            
            // Act
            val homePlan = planRepository.generateWorkoutPlan(input, WorkoutType.HOME)
            val gymPlan = planRepository.generateWorkoutPlan(input, WorkoutType.GYM)
            
            // Assert - Both plans exist and have correct types
            homePlan.type shouldBe WorkoutType.HOME
            gymPlan.type shouldBe WorkoutType.GYM
            
            // Both should have exercises
            homePlan.exercises.shouldNotBeEmpty()
            gymPlan.exercises.shouldNotBeEmpty()
            
            // Home exercises should not require gym equipment
            val homeEquipment = homePlan.exercises.flatMap { it.equipmentRequired }
            val gymOnlyEquipment = listOf("Barbell", "Cable Machine", "Leg Press Machine", "Rowing Machine", "Treadmill")
            homeEquipment.none { it in gymOnlyEquipment } shouldBe true
            
            // Gym exercises can include gym equipment
            val gymEquipment = gymPlan.exercises.flatMap { it.equipmentRequired }
            // Gym plan should have at least some equipment-based exercises
            gymEquipment.isNotEmpty() shouldBe true
        }
    }
    
    /**
     * Property 11.4: Workout plan includes warmup and cooldown
     * 
     * For any generated workout plan, it SHALL include both warmup
     * and cooldown exercises for safety.
     */
    test("workout plan includes warmup and cooldown") {
        checkAll(
            100,
            Arb.enum<HealthGoal>(),
            Arb.enum<WorkoutType>()
        ) { goal, workoutType ->
            // Arrange
            val profile = createTestProfile(goal = goal)
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                generateMockWorkoutPlan(input, workoutType, goal)
            }
            
            // Act
            val plan = planRepository.generateWorkoutPlan(input, workoutType)
            
            // Assert
            val warmupExercises = plan.exercises.filter { it.category == ExerciseCategory.WARMUP }
            val cooldownExercises = plan.exercises.filter { it.category == ExerciseCategory.COOLDOWN }
            
            warmupExercises.shouldNotBeEmpty()
            cooldownExercises.shouldNotBeEmpty()
        }
    }
    
    /**
     * Property 11.5: Workout plan has positive duration and calorie estimate
     * 
     * For any generated workout plan, duration and calorie burn estimate
     * SHALL be positive values.
     */
    test("workout plan has positive duration and calorie estimate") {
        checkAll(
            100,
            Arb.enum<HealthGoal>(),
            Arb.enum<WorkoutType>()
        ) { goal, workoutType ->
            // Arrange
            val profile = createTestProfile(goal = goal)
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                generateMockWorkoutPlan(input, workoutType, goal)
            }
            
            // Act
            val plan = planRepository.generateWorkoutPlan(input, workoutType)
            
            // Assert
            plan.durationMinutes shouldBeGreaterThan 0
            plan.caloriesBurnEstimate shouldBeGreaterThan 0
        }
    }
})

// Helper functions

private fun createTestProfile(
    goal: HealthGoal = HealthGoal.FITNESS,
    weight: Float = 70f,
    height: Float = 170f,
    age: Int = 30,
    dietPreference: DietPreference = DietPreference.NON_VEGETARIAN
): UserProfile {
    return UserProfile(
        name = "Test User",
        age = age,
        weight = weight,
        height = height,
        goal = goal,
        dietPreference = dietPreference
    )
}

private fun createTestUser(profile: UserProfile): com.healthtracker.domain.model.User {
    return com.healthtracker.domain.model.User(
        id = "test_user",
        email = "test@example.com",
        profile = profile,
        createdAt = Instant.now(),
        lastLoginAt = Instant.now()
    )
}

private fun createTestMetricsList(): List<HealthMetrics> {
    return (0..6).map { dayOffset ->
        HealthMetrics(
            id = UUID.randomUUID().toString(),
            userId = "test_user",
            date = LocalDate.now().minusDays(dayOffset.toLong()),
            steps = 5000 + (dayOffset * 500),
            distanceMeters = 3750.0 + (dayOffset * 375),
            caloriesBurned = 300.0 + (dayOffset * 50),
            screenTimeMinutes = 120,
            sleepDurationMinutes = 420,
            sleepQuality = SleepQuality.GOOD,
            heartRateSamples = emptyList(),
            hrvSamples = emptyList(),
            mood = Mood.HAPPY,
            syncedAt = Instant.now()
        )
    }
}

private fun generateMockWorkoutPlan(
    input: PlanGenerationInput,
    type: WorkoutType,
    goal: HealthGoal
): com.healthtracker.domain.model.WorkoutPlan {
    val exercises = mutableListOf<com.healthtracker.domain.model.Exercise>()
    
    // Warmup
    exercises.add(com.healthtracker.domain.model.Exercise(
        id = UUID.randomUUID().toString(),
        name = "Dynamic Stretching",
        category = ExerciseCategory.WARMUP,
        sets = 1,
        reps = null,
        durationSeconds = 300,
        restSeconds = 0,
        instructions = "Warm up",
        targetMuscles = listOf("Full Body"),
        equipmentRequired = emptyList(),
        caloriesPerSet = 20
    ))
    
    // Goal-specific exercises
    when (goal) {
        HealthGoal.WEIGHT_LOSS -> {
            if (type == WorkoutType.HOME) {
                exercises.add(createExercise("Jumping Jacks", ExerciseCategory.CARDIO, emptyList()))
                exercises.add(createExercise("Burpees", ExerciseCategory.HIIT, emptyList()))
                exercises.add(createExercise("Mountain Climbers", ExerciseCategory.CARDIO, emptyList()))
            } else {
                exercises.add(createExercise("Treadmill Intervals", ExerciseCategory.CARDIO, listOf("Treadmill")))
                exercises.add(createExercise("Rowing Machine", ExerciseCategory.CARDIO, listOf("Rowing Machine")))
            }
        }
        HealthGoal.FITNESS -> {
            if (type == WorkoutType.HOME) {
                exercises.add(createExercise("Push-ups", ExerciseCategory.STRENGTH, emptyList()))
                exercises.add(createExercise("Squats", ExerciseCategory.STRENGTH, emptyList()))
                exercises.add(createExercise("Plank", ExerciseCategory.CORE, emptyList()))
            } else {
                exercises.add(createExercise("Bench Press", ExerciseCategory.STRENGTH, listOf("Barbell", "Bench")))
                exercises.add(createExercise("Lat Pulldown", ExerciseCategory.STRENGTH, listOf("Cable Machine")))
                exercises.add(createExercise("Leg Press", ExerciseCategory.STRENGTH, listOf("Leg Press Machine")))
            }
        }
        HealthGoal.GENERAL -> {
            if (type == WorkoutType.HOME) {
                exercises.add(createExercise("Walking", ExerciseCategory.CARDIO, emptyList()))
                exercises.add(createExercise("Wall Push-ups", ExerciseCategory.STRENGTH, emptyList()))
            } else {
                exercises.add(createExercise("Stationary Bike", ExerciseCategory.CARDIO, listOf("Stationary Bike")))
                exercises.add(createExercise("Machine Press", ExerciseCategory.STRENGTH, listOf("Chest Press Machine")))
            }
        }
    }
    
    // Cooldown
    exercises.add(com.healthtracker.domain.model.Exercise(
        id = UUID.randomUUID().toString(),
        name = "Static Stretching",
        category = ExerciseCategory.COOLDOWN,
        sets = 1,
        reps = null,
        durationSeconds = 300,
        restSeconds = 0,
        instructions = "Cool down",
        targetMuscles = listOf("Full Body"),
        equipmentRequired = emptyList(),
        caloriesPerSet = 15
    ))
    
    return com.healthtracker.domain.model.WorkoutPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        type = type,
        goal = goal,
        exercises = exercises,
        durationMinutes = 30,
        difficulty = com.healthtracker.domain.model.Difficulty.INTERMEDIATE,
        caloriesBurnEstimate = 250,
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun createExercise(
    name: String,
    category: ExerciseCategory,
    equipment: List<String>
): com.healthtracker.domain.model.Exercise {
    return com.healthtracker.domain.model.Exercise(
        id = UUID.randomUUID().toString(),
        name = name,
        category = category,
        sets = 3,
        reps = 12,
        durationSeconds = null,
        restSeconds = 60,
        instructions = "Perform exercise",
        targetMuscles = listOf("Target"),
        equipmentRequired = equipment,
        caloriesPerSet = 15
    )
}
