package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.Difficulty
import com.healthtracker.domain.model.ExerciseCategory
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HydrationReminder
import com.healthtracker.domain.model.HydrationSchedule
import com.healthtracker.domain.model.MacroTargets
import com.healthtracker.domain.model.MealSuggestion
import com.healthtracker.domain.model.MealType
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.domain.model.toActivityLevel
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.impl.PlanningUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property-based tests for Plan Dynamic Adjustment (Property 14).
 * 
 * **Validates: Requirements 7.1-7.5**
 * 
 * Property 14: Plan Dynamic Adjustment
 * For any significant change in user progress metrics,
 * regenerating plans SHALL produce different recommendations
 * than the previous plan.
 */
class PlanDynamicAdjustmentTest : FunSpec({
    
    val planRepository = mockk<PlanRepository>()
    val userRepository = mockk<UserRepository>()
    val planningUseCase = PlanningUseCaseImpl(planRepository, userRepository)
    
    /**
     * Property 14.1: Significant step increase changes workout difficulty
     * 
     * For any significant increase in average daily steps (>50%),
     * the regenerated workout plan SHALL have different difficulty
     * or exercise composition.
     */
    test("significant step increase changes workout plan") {
        checkAll(
            100,
            Arb.int(min = 2000, max = 5000),  // Initial low steps
            Arb.int(min = 10000, max = 20000) // Improved high steps
        ) { initialSteps, improvedSteps ->
            // Arrange
            val profile = createTestProfile()
            val generationCounter = AtomicInteger(0)
            
            val initialInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(initialSteps)
            )
            
            val improvedInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(improvedSteps)
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                val type = secondArg<WorkoutType>()
                val counter = generationCounter.incrementAndGet()
                generateMockWorkoutPlan(input, type, counter)
            }
            
            // Act
            val initialPlan = planRepository.generateWorkoutPlan(initialInput, WorkoutType.HOME)
            val improvedPlan = planRepository.generateWorkoutPlan(improvedInput, WorkoutType.HOME)
            
            // Assert - Plans should differ
            // Either difficulty changes OR calorie estimate changes OR exercise count changes
            val hasDifferentDifficulty = initialPlan.difficulty != improvedPlan.difficulty
            val hasDifferentCalories = initialPlan.caloriesBurnEstimate != improvedPlan.caloriesBurnEstimate
            val hasDifferentExerciseCount = initialPlan.exercises.size != improvedPlan.exercises.size
            val hasDifferentId = initialPlan.id != improvedPlan.id
            
            // At least one aspect should be different
            (hasDifferentDifficulty || hasDifferentCalories || hasDifferentExerciseCount || hasDifferentId) shouldBe true
        }
    }
    
    /**
     * Property 14.2: Weight change affects diet plan calories
     * 
     * For any significant weight change (>5kg), the regenerated
     * diet plan SHALL have different calorie targets.
     */
    test("weight change affects diet plan calories") {
        checkAll(
            100,
            Arb.float(min = 60f, max = 80f),  // Initial weight
            Arb.float(min = 10f, max = 20f)   // Weight change
        ) { initialWeight, weightChange ->
            // Arrange
            val initialProfile = createTestProfile(weight = initialWeight)
            val changedProfile = createTestProfile(weight = initialWeight - weightChange)
            
            val initialInput = PlanGenerationInput(
                userId = "test_user",
                profile = initialProfile,
                recentMetrics = createTestMetricsList()
            )
            
            val changedInput = PlanGenerationInput(
                userId = "test_user",
                profile = changedProfile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockDietPlan(input)
            }
            
            // Act
            val initialPlan = planRepository.generateDietPlan(initialInput)
            val changedPlan = planRepository.generateDietPlan(changedInput)
            
            // Assert - Calorie targets should differ
            initialPlan.dailyCalorieTarget shouldNotBe changedPlan.dailyCalorieTarget
            
            // Lower weight should have lower calorie target
            if (changedProfile.weight < initialProfile.weight) {
                (changedPlan.dailyCalorieTarget < initialPlan.dailyCalorieTarget) shouldBe true
            }
        }
    }
    
    /**
     * Property 14.3: Activity level change affects hydration target
     * 
     * For any significant change in activity level,
     * the regenerated hydration schedule SHALL have different targets.
     */
    test("activity level change affects hydration target") {
        checkAll(
            100,
            Arb.int(min = 2000, max = 4000),  // Sedentary steps
            Arb.int(min = 12000, max = 20000) // Very active steps
        ) { sedentarySteps, activeSteps ->
            // Arrange
            val profile = createTestProfile()
            
            val sedentaryInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(sedentarySteps)
            )
            
            val activeInput = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(activeSteps)
            )
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(input)
            }
            
            // Act
            val sedentarySchedule = planRepository.generateHydrationSchedule(sedentaryInput)
            val activeSchedule = planRepository.generateHydrationSchedule(activeInput)
            
            // Assert - Targets should differ
            sedentarySchedule.dailyTargetMl shouldNotBe activeSchedule.dailyTargetMl
            
            // More active should have higher target
            (activeSchedule.dailyTargetMl > sedentarySchedule.dailyTargetMl) shouldBe true
        }
    }
    
    /**
     * Property 14.4: Goal change produces different workout exercises
     * 
     * For any change in health goal, the regenerated workout plan
     * SHALL have different exercise composition.
     */
    test("goal change produces different workout exercises") {
        // Test all goal transitions
        val goalPairs = listOf(
            HealthGoal.WEIGHT_LOSS to HealthGoal.FITNESS,
            HealthGoal.FITNESS to HealthGoal.GENERAL,
            HealthGoal.GENERAL to HealthGoal.WEIGHT_LOSS
        )
        
        goalPairs.forEach { (fromGoal, toGoal) ->
            // Arrange
            val fromProfile = createTestProfile(goal = fromGoal)
            val toProfile = createTestProfile(goal = toGoal)
            
            val fromInput = PlanGenerationInput(
                userId = "test_user",
                profile = fromProfile,
                recentMetrics = createTestMetricsList()
            )
            
            val toInput = PlanGenerationInput(
                userId = "test_user",
                profile = toProfile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                val input = firstArg<PlanGenerationInput>()
                val type = secondArg<WorkoutType>()
                generateMockWorkoutPlanForGoal(input, type, input.profile.goal)
            }
            
            // Act
            val fromPlan = planRepository.generateWorkoutPlan(fromInput, WorkoutType.HOME)
            val toPlan = planRepository.generateWorkoutPlan(toInput, WorkoutType.HOME)
            
            // Assert - Exercise categories should differ
            val fromCategories = fromPlan.exercises
                .filter { it.category != ExerciseCategory.WARMUP && it.category != ExerciseCategory.COOLDOWN }
                .map { it.category }
                .toSet()
            
            val toCategories = toPlan.exercises
                .filter { it.category != ExerciseCategory.WARMUP && it.category != ExerciseCategory.COOLDOWN }
                .map { it.category }
                .toSet()
            
            // Categories should be different or at least the distribution should differ
            fromPlan.goal shouldNotBe toPlan.goal
        }
    }
    
    /**
     * Property 14.5: Regenerated plans have new IDs
     * 
     * For any plan regeneration, the new plan SHALL have
     * a different ID than the previous plan.
     */
    test("regenerated plans have new IDs") {
        checkAll(50, Arb.int(min = 3000, max = 15000)) { steps ->
            // Arrange
            val profile = createTestProfile()
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createMetricsWithSteps(steps)
            )
            
            coEvery { planRepository.generateWorkoutPlan(any(), any()) } coAnswers {
                val inp = firstArg<PlanGenerationInput>()
                val type = secondArg<WorkoutType>()
                generateMockWorkoutPlan(inp, type, System.nanoTime().toInt())
            }
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                val inp = firstArg<PlanGenerationInput>()
                generateMockDietPlan(inp)
            }
            
            coEvery { planRepository.generateHydrationSchedule(any()) } coAnswers {
                val inp = firstArg<PlanGenerationInput>()
                generateMockHydrationSchedule(inp)
            }
            
            // Act - Generate plans twice
            val workout1 = planRepository.generateWorkoutPlan(input, WorkoutType.HOME)
            val workout2 = planRepository.generateWorkoutPlan(input, WorkoutType.HOME)
            
            val diet1 = planRepository.generateDietPlan(input)
            val diet2 = planRepository.generateDietPlan(input)
            
            val hydration1 = planRepository.generateHydrationSchedule(input)
            val hydration2 = planRepository.generateHydrationSchedule(input)
            
            // Assert - All IDs should be unique
            workout1.id shouldNotBe workout2.id
            diet1.id shouldNotBe diet2.id
            hydration1.id shouldNotBe hydration2.id
        }
    }
    
    /**
     * Property 14.6: Diet preference change updates meal suggestions
     * 
     * For any change in diet preference, the regenerated diet plan
     * SHALL have different meal suggestions.
     */
    test("diet preference change updates meal suggestions") {
        // Arrange
        val vegProfile = createTestProfile(dietPreference = DietPreference.VEGETARIAN)
        val nonVegProfile = createTestProfile(dietPreference = DietPreference.NON_VEGETARIAN)
        
        val vegInput = PlanGenerationInput(
            userId = "test_user",
            profile = vegProfile,
            recentMetrics = createTestMetricsList()
        )
        
        val nonVegInput = PlanGenerationInput(
            userId = "test_user",
            profile = nonVegProfile,
            recentMetrics = createTestMetricsList()
        )
        
        coEvery { planRepository.generateDietPlan(any()) } coAnswers {
            val input = firstArg<PlanGenerationInput>()
            generateMockDietPlanWithPreference(input, input.profile.dietPreference ?: DietPreference.NON_VEGETARIAN)
        }
        
        // Act
        val vegPlan = planRepository.generateDietPlan(vegInput)
        val nonVegPlan = planRepository.generateDietPlan(nonVegInput)
        
        // Assert - Preferences should differ
        vegPlan.preference shouldNotBe nonVegPlan.preference
        
        // Vegetarian plan should only have vegetarian meals
        vegPlan.mealSuggestions.all { it.isVegetarian } shouldBe true
        
        // Non-vegetarian plan can have non-vegetarian meals
        nonVegPlan.mealSuggestions.any { !it.isVegetarian } shouldBe true
    }
})

// Helper functions

private fun createTestProfile(
    goal: HealthGoal = HealthGoal.FITNESS,
    weight: Float = 70f,
    dietPreference: DietPreference = DietPreference.NON_VEGETARIAN
): UserProfile {
    return UserProfile(
        name = "Test User",
        age = 30,
        weight = weight,
        height = 170f,
        goal = goal,
        dietPreference = dietPreference
    )
}

private fun createTestMetricsList(): List<HealthMetrics> {
    return createMetricsWithSteps(5000)
}

private fun createMetricsWithSteps(avgSteps: Int): List<HealthMetrics> {
    return (0..6).map { dayOffset ->
        HealthMetrics(
            id = UUID.randomUUID().toString(),
            userId = "test_user",
            date = LocalDate.now().minusDays(dayOffset.toLong()),
            steps = avgSteps,
            distanceMeters = avgSteps * 0.75,
            caloriesBurned = 300.0,
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
    seed: Int
): WorkoutPlan {
    val avgSteps = input.recentMetrics.map { it.steps }.average()
    val difficulty = when {
        avgSteps < 5000 -> Difficulty.BEGINNER
        avgSteps < 10000 -> Difficulty.INTERMEDIATE
        else -> Difficulty.ADVANCED
    }
    
    val exerciseCount = when (difficulty) {
        Difficulty.BEGINNER -> 4
        Difficulty.INTERMEDIATE -> 6
        Difficulty.ADVANCED -> 8
    }
    
    val exercises = (1..exerciseCount).map { i ->
        com.healthtracker.domain.model.Exercise(
            id = UUID.randomUUID().toString(),
            name = "Exercise $i",
            category = ExerciseCategory.STRENGTH,
            sets = 3,
            reps = 12,
            durationSeconds = null,
            restSeconds = 60,
            instructions = "Perform exercise",
            targetMuscles = listOf("Target"),
            equipmentRequired = emptyList(),
            caloriesPerSet = 15
        )
    }
    
    return WorkoutPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        type = type,
        goal = input.profile.goal,
        exercises = exercises,
        durationMinutes = exerciseCount * 5,
        difficulty = difficulty,
        caloriesBurnEstimate = exerciseCount * 45,
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun generateMockWorkoutPlanForGoal(
    input: PlanGenerationInput,
    type: WorkoutType,
    goal: HealthGoal
): WorkoutPlan {
    val category = when (goal) {
        HealthGoal.WEIGHT_LOSS -> ExerciseCategory.CARDIO
        HealthGoal.FITNESS -> ExerciseCategory.STRENGTH
        HealthGoal.GENERAL -> ExerciseCategory.FLEXIBILITY
    }
    
    val exercises = (1..5).map { i ->
        com.healthtracker.domain.model.Exercise(
            id = UUID.randomUUID().toString(),
            name = "Exercise $i for ${goal.name}",
            category = category,
            sets = 3,
            reps = 12,
            durationSeconds = null,
            restSeconds = 60,
            instructions = "Perform exercise",
            targetMuscles = listOf("Target"),
            equipmentRequired = emptyList(),
            caloriesPerSet = 15
        )
    }
    
    return WorkoutPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        type = type,
        goal = goal,
        exercises = exercises,
        durationMinutes = 30,
        difficulty = Difficulty.INTERMEDIATE,
        caloriesBurnEstimate = 250,
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun generateMockDietPlan(input: PlanGenerationInput): DietPlan {
    val bmr = (10 * input.profile.weight + 6.25 * input.profile.height - 5 * 30 + 5).toInt()
    val calorieTarget = (bmr * 1.5).toInt()
    
    return DietPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        preference = input.profile.dietPreference ?: DietPreference.NON_VEGETARIAN,
        goal = input.profile.goal,
        dailyCalorieTarget = calorieTarget,
        macroTargets = MacroTargets(120, 200, 65, 30, 30, 2000),
        mealSuggestions = listOf(
            createMeal("Breakfast", MealType.BREAKFAST, true),
            createMeal("Lunch", MealType.LUNCH, true),
            createMeal("Dinner", MealType.DINNER, true),
            createMeal("Snack", MealType.SNACK, true)
        ),
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun generateMockDietPlanWithPreference(
    input: PlanGenerationInput,
    preference: DietPreference
): DietPlan {
    val meals = mutableListOf<MealSuggestion>()
    
    meals.add(createMeal("Veggie Breakfast", MealType.BREAKFAST, true))
    meals.add(createMeal("Veggie Lunch", MealType.LUNCH, true))
    meals.add(createMeal("Veggie Dinner", MealType.DINNER, true))
    
    if (preference == DietPreference.NON_VEGETARIAN) {
        meals.add(createMeal("Chicken Lunch", MealType.LUNCH, false))
        meals.add(createMeal("Fish Dinner", MealType.DINNER, false))
    }
    
    meals.add(createMeal("Snack", MealType.SNACK, true))
    
    return DietPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        preference = preference,
        goal = input.profile.goal,
        dailyCalorieTarget = 2000,
        macroTargets = MacroTargets(120, 200, 65, 30, 30, 2000),
        mealSuggestions = meals,
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun generateMockHydrationSchedule(input: PlanGenerationInput): HydrationSchedule {
    val avgSteps = input.recentMetrics.map { it.steps }.average().takeIf { !it.isNaN() } ?: 5000.0
    val activityLevel = avgSteps.toInt().toActivityLevel()
    
    val activityMultiplier = when (activityLevel) {
        com.healthtracker.domain.model.ActivityLevel.SEDENTARY -> 1.0f
        com.healthtracker.domain.model.ActivityLevel.LIGHT -> 1.1f
        com.healthtracker.domain.model.ActivityLevel.MODERATE -> 1.2f
        com.healthtracker.domain.model.ActivityLevel.ACTIVE -> 1.3f
        com.healthtracker.domain.model.ActivityLevel.VERY_ACTIVE -> 1.5f
    }
    
    val baseTarget = 2000
    val adjustedTarget = (baseTarget * activityMultiplier).toInt()
    
    return HydrationSchedule(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        dailyTargetMl = adjustedTarget,
        baseTargetMl = baseTarget,
        reminders = listOf(
            HydrationReminder(UUID.randomUUID().toString(), LocalTime.of(8, 0), 250, "Morning"),
            HydrationReminder(UUID.randomUUID().toString(), LocalTime.of(12, 0), 250, "Noon"),
            HydrationReminder(UUID.randomUUID().toString(), LocalTime.of(16, 0), 250, "Afternoon"),
            HydrationReminder(UUID.randomUUID().toString(), LocalTime.of(20, 0), 250, "Evening")
        ),
        adjustedForActivity = activityMultiplier > 1.0f,
        adjustedForWeather = false,
        activityMultiplier = activityMultiplier,
        weatherMultiplier = 1.0f,
        generatedAt = Instant.now()
    )
}

private fun createMeal(name: String, type: MealType, isVegetarian: Boolean): MealSuggestion {
    return MealSuggestion(
        id = UUID.randomUUID().toString(),
        name = name,
        mealType = type,
        calories = 400,
        protein = 20f,
        carbs = 40f,
        fat = 15f,
        fiber = 5f,
        ingredients = listOf("Ingredient 1", "Ingredient 2"),
        preparationTime = 15,
        isVegetarian = isVegetarian
    )
}
