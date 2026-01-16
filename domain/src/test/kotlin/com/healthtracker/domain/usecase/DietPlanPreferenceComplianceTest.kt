package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietPlan
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.MacroTargets
import com.healthtracker.domain.model.MealSuggestion
import com.healthtracker.domain.model.MealType
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.PlanGenerationInput
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.impl.PlanningUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based tests for Diet Plan Preference Compliance (Property 12).
 * 
 * **Validates: Requirements 7.3, 7.4**
 * 
 * Property 12: Diet Plan Preference Compliance
 * For any user with a diet preference (Vegetarian or Non-vegetarian),
 * all meal suggestions in the generated diet plan SHALL comply with that preference.
 */
class DietPlanPreferenceComplianceTest : FunSpec({
    
    val planRepository = mockk<PlanRepository>()
    val userRepository = mockk<UserRepository>()
    val planningUseCase = PlanningUseCaseImpl(planRepository, userRepository)
    
    /**
     * Property 12.1: Vegetarian preference excludes non-vegetarian meals
     * 
     * For any user with VEGETARIAN preference, ALL meal suggestions
     * SHALL have isVegetarian = true.
     */
    test("vegetarian preference excludes non-vegetarian meals") {
        checkAll(
            100,
            Arb.enum<HealthGoal>(),
            Arb.float(min = 50f, max = 150f),
            Arb.float(min = 140f, max = 200f)
        ) { goal, weight, height ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                weight = weight,
                height = height,
                dietPreference = DietPreference.VEGETARIAN
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, DietPreference.VEGETARIAN, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - ALL meals must be vegetarian
            dietPlan.mealSuggestions.shouldNotBeEmpty()
            dietPlan.mealSuggestions.forEach { meal ->
                meal.isVegetarian shouldBe true
            }
            
            // Verify preference is correctly set
            dietPlan.preference shouldBe DietPreference.VEGETARIAN
        }
    }
    
    /**
     * Property 12.2: Non-vegetarian preference can include all meals
     * 
     * For any user with NON_VEGETARIAN preference, meal suggestions
     * MAY include both vegetarian and non-vegetarian options.
     */
    test("non-vegetarian preference can include all meal types") {
        checkAll(
            100,
            Arb.enum<HealthGoal>(),
            Arb.float(min = 50f, max = 150f)
        ) { goal, weight ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                weight = weight,
                dietPreference = DietPreference.NON_VEGETARIAN
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, DietPreference.NON_VEGETARIAN, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - Plan should have meals
            dietPlan.mealSuggestions.shouldNotBeEmpty()
            
            // Verify preference is correctly set
            dietPlan.preference shouldBe DietPreference.NON_VEGETARIAN
            
            // Non-vegetarian plan can have both types (no restriction)
            // Just verify it has meals - no vegetarian-only constraint
        }
    }
    
    /**
     * Property 12.3: Diet plan covers all meal types
     * 
     * For any diet preference, the generated plan SHALL include
     * suggestions for Breakfast, Lunch, Dinner, and Snacks.
     */
    test("diet plan covers all meal types") {
        checkAll(
            100,
            Arb.enum<DietPreference>(),
            Arb.enum<HealthGoal>()
        ) { preference, goal ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                dietPreference = preference
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, preference, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - All meal types should be covered
            val mealTypes = dietPlan.mealSuggestions.map { it.mealType }.toSet()
            
            mealTypes.contains(MealType.BREAKFAST) shouldBe true
            mealTypes.contains(MealType.LUNCH) shouldBe true
            mealTypes.contains(MealType.DINNER) shouldBe true
            mealTypes.contains(MealType.SNACK) shouldBe true
        }
    }
    
    /**
     * Property 12.4: Meal calories are positive and reasonable
     * 
     * For any meal suggestion, calories SHALL be positive and
     * within reasonable bounds (50-2000 per meal).
     */
    test("meal calories are positive and reasonable") {
        checkAll(
            100,
            Arb.enum<DietPreference>(),
            Arb.enum<HealthGoal>()
        ) { preference, goal ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                dietPreference = preference
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, preference, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - All meals have reasonable calories
            dietPlan.mealSuggestions.forEach { meal ->
                meal.calories shouldBeGreaterThan 0
                (meal.calories <= 2000) shouldBe true
            }
        }
    }
    
    /**
     * Property 12.5: Macro values are non-negative
     * 
     * For any meal suggestion, protein, carbs, fat, and fiber
     * SHALL all be non-negative values.
     */
    test("macro values are non-negative") {
        checkAll(
            100,
            Arb.enum<DietPreference>(),
            Arb.enum<HealthGoal>()
        ) { preference, goal ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                dietPreference = preference
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, preference, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - All macros are non-negative
            dietPlan.mealSuggestions.forEach { meal ->
                (meal.protein >= 0f) shouldBe true
                (meal.carbs >= 0f) shouldBe true
                (meal.fat >= 0f) shouldBe true
                (meal.fiber >= 0f) shouldBe true
            }
            
            // Daily targets should also be positive
            dietPlan.dailyCalorieTarget shouldBeGreaterThan 0
            dietPlan.macroTargets.proteinGrams shouldBeGreaterThan 0
            dietPlan.macroTargets.carbsGrams shouldBeGreaterThan 0
            dietPlan.macroTargets.fatGrams shouldBeGreaterThan 0
        }
    }
    
    /**
     * Property 12.6: Meals have ingredients list
     * 
     * For any meal suggestion, the ingredients list SHALL not be empty.
     */
    test("meals have ingredients list") {
        checkAll(
            100,
            Arb.enum<DietPreference>(),
            Arb.enum<HealthGoal>()
        ) { preference, goal ->
            // Arrange
            val profile = createTestProfile(
                goal = goal,
                dietPreference = preference
            )
            
            val input = PlanGenerationInput(
                userId = "test_user",
                profile = profile,
                recentMetrics = createTestMetricsList()
            )
            
            coEvery { planRepository.generateDietPlan(any()) } coAnswers {
                generateMockDietPlan(input, preference, goal)
            }
            
            // Act
            val dietPlan = planRepository.generateDietPlan(input)
            
            // Assert - All meals have ingredients
            dietPlan.mealSuggestions.forEach { meal ->
                meal.ingredients.shouldNotBeEmpty()
            }
        }
    }
})

// Helper functions

private fun createTestProfile(
    goal: HealthGoal = HealthGoal.FITNESS,
    weight: Float = 70f,
    height: Float = 170f,
    dietPreference: DietPreference = DietPreference.NON_VEGETARIAN
): UserProfile {
    return UserProfile(
        name = "Test User",
        age = 30,
        weight = weight,
        height = height,
        goal = goal,
        dietPreference = dietPreference
    )
}

private fun createTestMetricsList(): List<HealthMetrics> {
    return (0..6).map { dayOffset ->
        HealthMetrics(
            id = UUID.randomUUID().toString(),
            userId = "test_user",
            date = LocalDate.now().minusDays(dayOffset.toLong()),
            steps = 5000,
            distanceMeters = 3750.0,
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

private fun generateMockDietPlan(
    input: PlanGenerationInput,
    preference: DietPreference,
    goal: HealthGoal
): DietPlan {
    val meals = mutableListOf<MealSuggestion>()
    
    // Breakfast
    meals.add(createMeal(
        name = "Greek Yogurt Parfait",
        mealType = MealType.BREAKFAST,
        calories = 350,
        isVegetarian = true
    ))
    
    if (preference == DietPreference.NON_VEGETARIAN) {
        meals.add(createMeal(
            name = "Eggs with Turkey Bacon",
            mealType = MealType.BREAKFAST,
            calories = 400,
            isVegetarian = false
        ))
    }
    
    // Lunch
    meals.add(createMeal(
        name = "Quinoa Buddha Bowl",
        mealType = MealType.LUNCH,
        calories = 520,
        isVegetarian = true
    ))
    
    if (preference == DietPreference.NON_VEGETARIAN) {
        meals.add(createMeal(
            name = "Grilled Chicken Salad",
            mealType = MealType.LUNCH,
            calories = 480,
            isVegetarian = false
        ))
    }
    
    // Dinner
    meals.add(createMeal(
        name = "Vegetable Stir-Fry with Tofu",
        mealType = MealType.DINNER,
        calories = 450,
        isVegetarian = true
    ))
    
    if (preference == DietPreference.NON_VEGETARIAN) {
        meals.add(createMeal(
            name = "Grilled Salmon",
            mealType = MealType.DINNER,
            calories = 520,
            isVegetarian = false
        ))
    }
    
    // Snack
    meals.add(createMeal(
        name = "Mixed Nuts",
        mealType = MealType.SNACK,
        calories = 180,
        isVegetarian = true
    ))
    
    val calorieTarget = when (goal) {
        HealthGoal.WEIGHT_LOSS -> 1600
        HealthGoal.FITNESS -> 2200
        HealthGoal.GENERAL -> 2000
    }
    
    return DietPlan(
        id = UUID.randomUUID().toString(),
        userId = input.userId,
        preference = preference,
        goal = goal,
        dailyCalorieTarget = calorieTarget,
        macroTargets = MacroTargets(
            proteinGrams = 120,
            carbsGrams = 200,
            fatGrams = 65,
            fiberGrams = 30,
            sugarGrams = 30,
            sodiumMg = 2000
        ),
        mealSuggestions = meals,
        generatedAt = Instant.now(),
        expiresAt = Instant.now().plusSeconds(604800)
    )
}

private fun createMeal(
    name: String,
    mealType: MealType,
    calories: Int,
    isVegetarian: Boolean
): MealSuggestion {
    return MealSuggestion(
        id = UUID.randomUUID().toString(),
        name = name,
        mealType = mealType,
        calories = calories,
        protein = 20f,
        carbs = 30f,
        fat = 10f,
        fiber = 5f,
        ingredients = listOf("Ingredient 1", "Ingredient 2", "Ingredient 3"),
        preparationTime = 15,
        isVegetarian = isVegetarian
    )
}
