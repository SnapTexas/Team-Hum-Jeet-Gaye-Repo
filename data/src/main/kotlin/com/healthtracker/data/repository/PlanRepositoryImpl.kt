package com.healthtracker.data.repository

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
import com.healthtracker.domain.model.WaterIntake
import com.healthtracker.domain.model.WorkoutPlan
import com.healthtracker.domain.model.WorkoutType
import com.healthtracker.domain.model.toActivityLevel
import com.healthtracker.domain.repository.PlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PlanRepository.
 * 
 * Generates and manages personalized workout plans, diet plans,
 * and hydration schedules based on user goals and preferences.
 */
@Singleton
class PlanRepositoryImpl @Inject constructor() : PlanRepository {
    
    // In-memory storage (would be Room database in production)
    private val workoutPlans = MutableStateFlow<Map<String, WorkoutPlan>>(emptyMap())
    private val dietPlans = MutableStateFlow<Map<String, DietPlan>>(emptyMap())
    private val hydrationSchedules = MutableStateFlow<Map<String, HydrationSchedule>>(emptyMap())
    private val waterIntakeLogs = MutableStateFlow<List<WaterIntake>>(emptyList())
    
    companion object {
        private const val BASE_WATER_ML = 2000
        private const val PLAN_VALIDITY_DAYS = 7L
    }
    
    // ============================================
    // WORKOUT PLAN
    // ============================================
    
    override fun getWorkoutPlan(userId: String, type: WorkoutType): Flow<WorkoutPlan?> {
        return workoutPlans.map { plans ->
            plans.values.find { it.userId == userId && it.type == type }
        }
    }
    
    override fun getAllWorkoutPlans(userId: String): Flow<List<WorkoutPlan>> {
        return workoutPlans.map { plans ->
            plans.values.filter { it.userId == userId }
        }
    }
    
    override suspend fun generateWorkoutPlan(input: PlanGenerationInput, type: WorkoutType): WorkoutPlan {
        val exercises = generateExercisesForGoal(input.profile.goal, type)
        val difficulty = calculateDifficulty(input.recentMetrics.map { it.steps }.average())
        
        return WorkoutPlan(
            id = UUID.randomUUID().toString(),
            userId = input.userId,
            type = type,
            goal = input.profile.goal,
            exercises = exercises,
            durationMinutes = exercises.sumOf { 
                (it.sets * ((it.durationSeconds ?: 30) + it.restSeconds)) / 60 
            },
            difficulty = difficulty,
            caloriesBurnEstimate = exercises.sumOf { it.sets * it.caloriesPerSet },
            generatedAt = Instant.now(),
            expiresAt = Instant.now().plus(PLAN_VALIDITY_DAYS, ChronoUnit.DAYS)
        )
    }
    
    override suspend fun saveWorkoutPlan(plan: WorkoutPlan) {
        val key = "${plan.userId}_${plan.type}"
        workoutPlans.value = workoutPlans.value + (key to plan)
    }
    
    private fun generateExercisesForGoal(goal: HealthGoal, type: WorkoutType): List<Exercise> {
        val exercises = mutableListOf<Exercise>()
        
        // Warmup
        exercises.add(createExercise(
            name = "Dynamic Stretching",
            category = ExerciseCategory.WARMUP,
            sets = 1,
            durationSeconds = 300,
            restSeconds = 0,
            instructions = "Perform arm circles, leg swings, and torso twists",
            targetMuscles = listOf("Full Body"),
            equipment = emptyList(),
            caloriesPerSet = 20
        ))
        
        when (goal) {
            HealthGoal.WEIGHT_LOSS -> {
                // High-intensity cardio focused
                if (type == WorkoutType.HOME) {
                    exercises.addAll(listOf(
                        createExercise("Jumping Jacks", ExerciseCategory.CARDIO, 3, reps = 30, restSeconds = 30,
                            instructions = "Jump while spreading legs and raising arms overhead",
                            targetMuscles = listOf("Full Body"), equipment = emptyList(), caloriesPerSet = 15),
                        createExercise("Burpees", ExerciseCategory.HIIT, 3, reps = 10, restSeconds = 45,
                            instructions = "Squat, jump back to plank, push-up, jump forward, jump up",
                            targetMuscles = listOf("Full Body"), equipment = emptyList(), caloriesPerSet = 25),
                        createExercise("Mountain Climbers", ExerciseCategory.CARDIO, 3, durationSeconds = 45, restSeconds = 30,
                            instructions = "In plank position, alternate bringing knees to chest rapidly",
                            targetMuscles = listOf("Core", "Legs"), equipment = emptyList(), caloriesPerSet = 20),
                        createExercise("High Knees", ExerciseCategory.CARDIO, 3, durationSeconds = 45, restSeconds = 30,
                            instructions = "Run in place bringing knees up to hip level",
                            targetMuscles = listOf("Legs", "Core"), equipment = emptyList(), caloriesPerSet = 18),
                        createExercise("Squat Jumps", ExerciseCategory.HIIT, 3, reps = 15, restSeconds = 45,
                            instructions = "Perform a squat then explosively jump up",
                            targetMuscles = listOf("Legs", "Glutes"), equipment = emptyList(), caloriesPerSet = 22)
                    ))
                } else {
                    exercises.addAll(listOf(
                        createExercise("Treadmill Intervals", ExerciseCategory.CARDIO, 1, durationSeconds = 1200, restSeconds = 60,
                            instructions = "Alternate 1 min sprint with 1 min walk for 20 minutes",
                            targetMuscles = listOf("Legs", "Cardio"), equipment = listOf("Treadmill"), caloriesPerSet = 200),
                        createExercise("Rowing Machine", ExerciseCategory.CARDIO, 3, durationSeconds = 300, restSeconds = 60,
                            instructions = "Row at moderate intensity focusing on form",
                            targetMuscles = listOf("Back", "Arms", "Legs"), equipment = listOf("Rowing Machine"), caloriesPerSet = 50),
                        createExercise("Battle Ropes", ExerciseCategory.HIIT, 3, durationSeconds = 30, restSeconds = 45,
                            instructions = "Create waves with the ropes using alternating arms",
                            targetMuscles = listOf("Arms", "Core"), equipment = listOf("Battle Ropes"), caloriesPerSet = 25)
                    ))
                }
            }
            HealthGoal.FITNESS -> {
                // Balanced strength and cardio
                if (type == WorkoutType.HOME) {
                    exercises.addAll(listOf(
                        createExercise("Push-ups", ExerciseCategory.STRENGTH, 3, reps = 15, restSeconds = 60,
                            instructions = "Keep body straight, lower chest to floor, push back up",
                            targetMuscles = listOf("Chest", "Triceps", "Shoulders"), equipment = emptyList(), caloriesPerSet = 12),
                        createExercise("Bodyweight Squats", ExerciseCategory.STRENGTH, 3, reps = 20, restSeconds = 60,
                            instructions = "Feet shoulder-width apart, lower until thighs parallel to floor",
                            targetMuscles = listOf("Quads", "Glutes", "Hamstrings"), equipment = emptyList(), caloriesPerSet = 15),
                        createExercise("Plank", ExerciseCategory.CORE, 3, durationSeconds = 45, restSeconds = 30,
                            instructions = "Hold body in straight line from head to heels",
                            targetMuscles = listOf("Core", "Shoulders"), equipment = emptyList(), caloriesPerSet = 8),
                        createExercise("Lunges", ExerciseCategory.STRENGTH, 3, reps = 12, restSeconds = 60,
                            instructions = "Step forward, lower back knee toward floor, alternate legs",
                            targetMuscles = listOf("Quads", "Glutes", "Hamstrings"), equipment = emptyList(), caloriesPerSet = 14),
                        createExercise("Dips (Chair)", ExerciseCategory.STRENGTH, 3, reps = 12, restSeconds = 60,
                            instructions = "Using a chair, lower body by bending elbows, push back up",
                            targetMuscles = listOf("Triceps", "Chest"), equipment = listOf("Chair"), caloriesPerSet = 10)
                    ))
                } else {
                    exercises.addAll(listOf(
                        createExercise("Bench Press", ExerciseCategory.STRENGTH, 4, reps = 10, restSeconds = 90,
                            instructions = "Lower bar to chest, press up to full extension",
                            targetMuscles = listOf("Chest", "Triceps", "Shoulders"), equipment = listOf("Barbell", "Bench"), caloriesPerSet = 15),
                        createExercise("Lat Pulldown", ExerciseCategory.STRENGTH, 3, reps = 12, restSeconds = 60,
                            instructions = "Pull bar down to chest, squeeze shoulder blades",
                            targetMuscles = listOf("Back", "Biceps"), equipment = listOf("Cable Machine"), caloriesPerSet = 12),
                        createExercise("Leg Press", ExerciseCategory.STRENGTH, 4, reps = 12, restSeconds = 90,
                            instructions = "Push platform away, don't lock knees at top",
                            targetMuscles = listOf("Quads", "Glutes", "Hamstrings"), equipment = listOf("Leg Press Machine"), caloriesPerSet = 18),
                        createExercise("Dumbbell Rows", ExerciseCategory.STRENGTH, 3, reps = 10, restSeconds = 60,
                            instructions = "Pull dumbbell to hip, squeeze back muscles",
                            targetMuscles = listOf("Back", "Biceps"), equipment = listOf("Dumbbells", "Bench"), caloriesPerSet = 12)
                    ))
                }
            }
            HealthGoal.GENERAL -> {
                // Light, balanced routine
                if (type == WorkoutType.HOME) {
                    exercises.addAll(listOf(
                        createExercise("Walking in Place", ExerciseCategory.CARDIO, 1, durationSeconds = 300, restSeconds = 30,
                            instructions = "Walk in place at a comfortable pace",
                            targetMuscles = listOf("Legs"), equipment = emptyList(), caloriesPerSet = 25),
                        createExercise("Wall Push-ups", ExerciseCategory.STRENGTH, 2, reps = 12, restSeconds = 45,
                            instructions = "Push-ups against a wall for lower intensity",
                            targetMuscles = listOf("Chest", "Arms"), equipment = emptyList(), caloriesPerSet = 8),
                        createExercise("Chair Squats", ExerciseCategory.STRENGTH, 2, reps = 10, restSeconds = 45,
                            instructions = "Squat down to touch chair, stand back up",
                            targetMuscles = listOf("Legs", "Glutes"), equipment = listOf("Chair"), caloriesPerSet = 10),
                        createExercise("Standing Side Bends", ExerciseCategory.FLEXIBILITY, 2, reps = 15, restSeconds = 30,
                            instructions = "Bend sideways, alternating sides",
                            targetMuscles = listOf("Core", "Obliques"), equipment = emptyList(), caloriesPerSet = 5)
                    ))
                } else {
                    exercises.addAll(listOf(
                        createExercise("Stationary Bike", ExerciseCategory.CARDIO, 1, durationSeconds = 900, restSeconds = 60,
                            instructions = "Cycle at moderate pace for 15 minutes",
                            targetMuscles = listOf("Legs", "Cardio"), equipment = listOf("Stationary Bike"), caloriesPerSet = 100),
                        createExercise("Machine Chest Press", ExerciseCategory.STRENGTH, 2, reps = 12, restSeconds = 60,
                            instructions = "Push handles forward, return slowly",
                            targetMuscles = listOf("Chest", "Triceps"), equipment = listOf("Chest Press Machine"), caloriesPerSet = 12),
                        createExercise("Leg Extension", ExerciseCategory.STRENGTH, 2, reps = 12, restSeconds = 60,
                            instructions = "Extend legs, squeeze quads at top",
                            targetMuscles = listOf("Quads"), equipment = listOf("Leg Extension Machine"), caloriesPerSet = 10)
                    ))
                }
            }
        }
        
        // Cooldown
        exercises.add(createExercise(
            name = "Static Stretching",
            category = ExerciseCategory.COOLDOWN,
            sets = 1,
            durationSeconds = 300,
            restSeconds = 0,
            instructions = "Hold each stretch for 30 seconds: hamstrings, quads, chest, shoulders",
            targetMuscles = listOf("Full Body"),
            equipment = emptyList(),
            caloriesPerSet = 15
        ))
        
        return exercises
    }
    
    private fun createExercise(
        name: String,
        category: ExerciseCategory,
        sets: Int,
        reps: Int? = null,
        durationSeconds: Int? = null,
        restSeconds: Int,
        instructions: String,
        targetMuscles: List<String>,
        equipment: List<String>,
        caloriesPerSet: Int
    ): Exercise {
        return Exercise(
            id = UUID.randomUUID().toString(),
            name = name,
            category = category,
            sets = sets,
            reps = reps,
            durationSeconds = durationSeconds,
            restSeconds = restSeconds,
            instructions = instructions,
            targetMuscles = targetMuscles,
            equipmentRequired = equipment,
            caloriesPerSet = caloriesPerSet
        )
    }
    
    private fun calculateDifficulty(avgSteps: Double): Difficulty {
        return when {
            avgSteps < 5000 -> Difficulty.BEGINNER
            avgSteps < 10000 -> Difficulty.INTERMEDIATE
            else -> Difficulty.ADVANCED
        }
    }

    
    // ============================================
    // DIET PLAN
    // ============================================
    
    override fun getDietPlan(userId: String): Flow<DietPlan?> {
        return dietPlans.map { plans -> plans[userId] }
    }
    
    override suspend fun generateDietPlan(input: PlanGenerationInput): DietPlan {
        val preference = input.profile.dietPreference ?: DietPreference.NON_VEGETARIAN
        val calorieTarget = calculateCalorieTarget(input.profile.weight, input.profile.height, input.profile.goal)
        val macros = calculateMacroTargets(calorieTarget, input.profile.goal)
        val meals = generateMealSuggestions(preference, calorieTarget, input.profile.goal)
        
        return DietPlan(
            id = UUID.randomUUID().toString(),
            userId = input.userId,
            preference = preference,
            goal = input.profile.goal,
            dailyCalorieTarget = calorieTarget,
            macroTargets = macros,
            mealSuggestions = meals,
            generatedAt = Instant.now(),
            expiresAt = Instant.now().plus(PLAN_VALIDITY_DAYS, ChronoUnit.DAYS)
        )
    }
    
    override suspend fun saveDietPlan(plan: DietPlan) {
        dietPlans.value = dietPlans.value + (plan.userId to plan)
    }
    
    private fun calculateCalorieTarget(weight: Float, height: Float, goal: HealthGoal): Int {
        // Basic BMR calculation (Mifflin-St Jeor simplified)
        val bmr = (10 * weight + 6.25 * height - 5 * 30 + 5).toInt() // Assuming age 30
        val tdee = (bmr * 1.5).toInt() // Moderate activity
        
        return when (goal) {
            HealthGoal.WEIGHT_LOSS -> (tdee * 0.8).toInt() // 20% deficit
            HealthGoal.FITNESS -> (tdee * 1.1).toInt() // 10% surplus for muscle
            HealthGoal.GENERAL -> tdee
        }
    }
    
    private fun calculateMacroTargets(calories: Int, goal: HealthGoal): MacroTargets {
        return when (goal) {
            HealthGoal.WEIGHT_LOSS -> MacroTargets(
                proteinGrams = (calories * 0.35 / 4).toInt(), // 35% protein
                carbsGrams = (calories * 0.35 / 4).toInt(),   // 35% carbs
                fatGrams = (calories * 0.30 / 9).toInt(),     // 30% fat
                fiberGrams = 30,
                sugarGrams = 25,
                sodiumMg = 2000
            )
            HealthGoal.FITNESS -> MacroTargets(
                proteinGrams = (calories * 0.30 / 4).toInt(), // 30% protein
                carbsGrams = (calories * 0.45 / 4).toInt(),   // 45% carbs
                fatGrams = (calories * 0.25 / 9).toInt(),     // 25% fat
                fiberGrams = 35,
                sugarGrams = 40,
                sodiumMg = 2300
            )
            HealthGoal.GENERAL -> MacroTargets(
                proteinGrams = (calories * 0.25 / 4).toInt(), // 25% protein
                carbsGrams = (calories * 0.50 / 4).toInt(),   // 50% carbs
                fatGrams = (calories * 0.25 / 9).toInt(),     // 25% fat
                fiberGrams = 28,
                sugarGrams = 35,
                sodiumMg = 2300
            )
        }
    }
    
    private fun generateMealSuggestions(
        preference: DietPreference,
        calorieTarget: Int,
        goal: HealthGoal
    ): List<MealSuggestion> {
        val meals = mutableListOf<MealSuggestion>()
        
        // Breakfast options
        val breakfastCalories = (calorieTarget * 0.25).toInt()
        meals.addAll(getBreakfastOptions(preference, breakfastCalories))
        
        // Lunch options
        val lunchCalories = (calorieTarget * 0.35).toInt()
        meals.addAll(getLunchOptions(preference, lunchCalories))
        
        // Dinner options
        val dinnerCalories = (calorieTarget * 0.30).toInt()
        meals.addAll(getDinnerOptions(preference, dinnerCalories))
        
        // Snack options
        val snackCalories = (calorieTarget * 0.10).toInt()
        meals.addAll(getSnackOptions(preference, snackCalories))
        
        return meals
    }
    
    private fun getBreakfastOptions(preference: DietPreference, targetCalories: Int): List<MealSuggestion> {
        val options = mutableListOf<MealSuggestion>()
        
        // Vegetarian options
        options.add(createMeal(
            name = "Greek Yogurt Parfait",
            mealType = MealType.BREAKFAST,
            calories = 350,
            protein = 20f, carbs = 45f, fat = 10f, fiber = 5f,
            ingredients = listOf("Greek yogurt", "Granola", "Mixed berries", "Honey"),
            prepTime = 5,
            isVegetarian = true
        ))
        
        options.add(createMeal(
            name = "Avocado Toast with Eggs",
            mealType = MealType.BREAKFAST,
            calories = 400,
            protein = 18f, carbs = 35f, fat = 22f, fiber = 8f,
            ingredients = listOf("Whole grain bread", "Avocado", "Eggs", "Cherry tomatoes"),
            prepTime = 10,
            isVegetarian = true
        ))
        
        options.add(createMeal(
            name = "Oatmeal with Nuts and Fruit",
            mealType = MealType.BREAKFAST,
            calories = 380,
            protein = 12f, carbs = 55f, fat = 14f, fiber = 8f,
            ingredients = listOf("Rolled oats", "Almond milk", "Walnuts", "Banana", "Cinnamon"),
            prepTime = 8,
            isVegetarian = true
        ))
        
        // Non-vegetarian options
        if (preference == DietPreference.NON_VEGETARIAN) {
            options.add(createMeal(
                name = "Protein Pancakes with Turkey Bacon",
                mealType = MealType.BREAKFAST,
                calories = 450,
                protein = 35f, carbs = 40f, fat = 15f, fiber = 4f,
                ingredients = listOf("Protein pancake mix", "Turkey bacon", "Maple syrup", "Berries"),
                prepTime = 15,
                isVegetarian = false
            ))
        }
        
        return options.filter { it.isVegetarian || preference == DietPreference.NON_VEGETARIAN }
    }
    
    private fun getLunchOptions(preference: DietPreference, targetCalories: Int): List<MealSuggestion> {
        val options = mutableListOf<MealSuggestion>()
        
        // Vegetarian options
        options.add(createMeal(
            name = "Quinoa Buddha Bowl",
            mealType = MealType.LUNCH,
            calories = 520,
            protein = 18f, carbs = 65f, fat = 20f, fiber = 12f,
            ingredients = listOf("Quinoa", "Chickpeas", "Roasted vegetables", "Tahini dressing", "Avocado"),
            prepTime = 20,
            isVegetarian = true
        ))
        
        options.add(createMeal(
            name = "Mediterranean Salad",
            mealType = MealType.LUNCH,
            calories = 450,
            protein = 15f, carbs = 40f, fat = 25f, fiber = 10f,
            ingredients = listOf("Mixed greens", "Feta cheese", "Olives", "Cucumber", "Tomatoes", "Olive oil"),
            prepTime = 10,
            isVegetarian = true
        ))
        
        // Non-vegetarian options
        if (preference == DietPreference.NON_VEGETARIAN) {
            options.add(createMeal(
                name = "Grilled Chicken Salad",
                mealType = MealType.LUNCH,
                calories = 480,
                protein = 40f, carbs = 25f, fat = 22f, fiber = 8f,
                ingredients = listOf("Grilled chicken breast", "Mixed greens", "Cherry tomatoes", "Cucumber", "Balsamic vinaigrette"),
                prepTime = 15,
                isVegetarian = false
            ))
            
            options.add(createMeal(
                name = "Turkey Wrap",
                mealType = MealType.LUNCH,
                calories = 420,
                protein = 32f, carbs = 38f, fat = 15f, fiber = 6f,
                ingredients = listOf("Whole wheat wrap", "Turkey breast", "Lettuce", "Tomato", "Mustard"),
                prepTime = 8,
                isVegetarian = false
            ))
        }
        
        return options.filter { it.isVegetarian || preference == DietPreference.NON_VEGETARIAN }
    }
    
    private fun getDinnerOptions(preference: DietPreference, targetCalories: Int): List<MealSuggestion> {
        val options = mutableListOf<MealSuggestion>()
        
        // Vegetarian options
        options.add(createMeal(
            name = "Vegetable Stir-Fry with Tofu",
            mealType = MealType.DINNER,
            calories = 450,
            protein = 22f, carbs = 45f, fat = 18f, fiber = 10f,
            ingredients = listOf("Firm tofu", "Broccoli", "Bell peppers", "Snap peas", "Brown rice", "Soy sauce"),
            prepTime = 25,
            isVegetarian = true
        ))
        
        options.add(createMeal(
            name = "Lentil Curry",
            mealType = MealType.DINNER,
            calories = 480,
            protein = 20f, carbs = 60f, fat = 15f, fiber = 15f,
            ingredients = listOf("Red lentils", "Coconut milk", "Curry spices", "Spinach", "Basmati rice"),
            prepTime = 30,
            isVegetarian = true
        ))
        
        // Non-vegetarian options
        if (preference == DietPreference.NON_VEGETARIAN) {
            options.add(createMeal(
                name = "Grilled Salmon with Vegetables",
                mealType = MealType.DINNER,
                calories = 520,
                protein = 42f, carbs = 25f, fat = 28f, fiber = 6f,
                ingredients = listOf("Salmon fillet", "Asparagus", "Sweet potato", "Lemon", "Olive oil"),
                prepTime = 25,
                isVegetarian = false
            ))
            
            options.add(createMeal(
                name = "Lean Beef Stir-Fry",
                mealType = MealType.DINNER,
                calories = 500,
                protein = 38f, carbs = 40f, fat = 20f, fiber = 8f,
                ingredients = listOf("Lean beef strips", "Mixed vegetables", "Brown rice", "Ginger", "Garlic"),
                prepTime = 20,
                isVegetarian = false
            ))
        }
        
        return options.filter { it.isVegetarian || preference == DietPreference.NON_VEGETARIAN }
    }
    
    private fun getSnackOptions(preference: DietPreference, targetCalories: Int): List<MealSuggestion> {
        return listOf(
            createMeal(
                name = "Mixed Nuts",
                mealType = MealType.SNACK,
                calories = 180,
                protein = 6f, carbs = 8f, fat = 16f, fiber = 3f,
                ingredients = listOf("Almonds", "Walnuts", "Cashews"),
                prepTime = 0,
                isVegetarian = true
            ),
            createMeal(
                name = "Apple with Almond Butter",
                mealType = MealType.SNACK,
                calories = 200,
                protein = 5f, carbs = 25f, fat = 10f, fiber = 5f,
                ingredients = listOf("Apple", "Almond butter"),
                prepTime = 2,
                isVegetarian = true
            ),
            createMeal(
                name = "Protein Smoothie",
                mealType = MealType.SNACK,
                calories = 220,
                protein = 20f, carbs = 25f, fat = 5f, fiber = 4f,
                ingredients = listOf("Protein powder", "Banana", "Almond milk", "Spinach"),
                prepTime = 5,
                isVegetarian = true
            )
        )
    }
    
    private fun createMeal(
        name: String,
        mealType: MealType,
        calories: Int,
        protein: Float,
        carbs: Float,
        fat: Float,
        fiber: Float,
        ingredients: List<String>,
        prepTime: Int,
        isVegetarian: Boolean
    ): MealSuggestion {
        return MealSuggestion(
            id = UUID.randomUUID().toString(),
            name = name,
            mealType = mealType,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            ingredients = ingredients,
            preparationTime = prepTime,
            isVegetarian = isVegetarian
        )
    }

    
    // ============================================
    // HYDRATION SCHEDULE
    // ============================================
    
    override fun getHydrationSchedule(userId: String): Flow<HydrationSchedule?> {
        return hydrationSchedules.map { schedules -> schedules[userId] }
    }
    
    override suspend fun generateHydrationSchedule(input: PlanGenerationInput): HydrationSchedule {
        // Calculate activity level from recent metrics
        val avgSteps = input.recentMetrics.map { it.steps }.average().takeIf { !it.isNaN() } ?: 5000.0
        val activityLevel = avgSteps.toInt().toActivityLevel()
        
        // Calculate multipliers
        val activityMultiplier = when (activityLevel) {
            ActivityLevel.SEDENTARY -> 1.0f
            ActivityLevel.LIGHT -> 1.1f
            ActivityLevel.MODERATE -> 1.2f
            ActivityLevel.ACTIVE -> 1.3f
            ActivityLevel.VERY_ACTIVE -> 1.5f
        }
        
        val weatherMultiplier = input.currentWeather?.let { weather ->
            var multiplier = 1.0f
            if (weather.isHot) multiplier *= 1.2f
            if (weather.isHumid) multiplier *= 1.1f
            multiplier
        } ?: 1.0f
        
        // Calculate target
        val baseTarget = BASE_WATER_ML
        val adjustedTarget = (baseTarget * activityMultiplier * weatherMultiplier).toInt()
        
        // Generate reminders throughout the day
        val reminders = generateHydrationReminders(adjustedTarget)
        
        return HydrationSchedule(
            id = UUID.randomUUID().toString(),
            userId = input.userId,
            dailyTargetMl = adjustedTarget,
            baseTargetMl = baseTarget,
            reminders = reminders,
            adjustedForActivity = activityMultiplier > 1.0f,
            adjustedForWeather = weatherMultiplier > 1.0f,
            activityMultiplier = activityMultiplier,
            weatherMultiplier = weatherMultiplier,
            generatedAt = Instant.now()
        )
    }
    
    override suspend fun saveHydrationSchedule(schedule: HydrationSchedule) {
        hydrationSchedules.value = hydrationSchedules.value + (schedule.userId to schedule)
    }
    
    private fun generateHydrationReminders(dailyTarget: Int): List<HydrationReminder> {
        val reminders = mutableListOf<HydrationReminder>()
        val reminderTimes = listOf(
            LocalTime.of(7, 0) to "Start your day with water! 💧",
            LocalTime.of(9, 0) to "Mid-morning hydration break",
            LocalTime.of(11, 0) to "Stay hydrated before lunch",
            LocalTime.of(13, 0) to "Post-lunch water time",
            LocalTime.of(15, 0) to "Afternoon hydration boost",
            LocalTime.of(17, 0) to "Pre-dinner water break",
            LocalTime.of(19, 0) to "Evening hydration",
            LocalTime.of(21, 0) to "Last water of the day"
        )
        
        val amountPerReminder = dailyTarget / reminderTimes.size
        
        reminderTimes.forEach { (time, message) ->
            reminders.add(HydrationReminder(
                id = UUID.randomUUID().toString(),
                time = time,
                amountMl = amountPerReminder,
                message = message
            ))
        }
        
        return reminders
    }
    
    override suspend fun logWaterIntake(userId: String, amountMl: Int): WaterIntake {
        val intake = WaterIntake(
            id = UUID.randomUUID().toString(),
            userId = userId,
            date = LocalDate.now(),
            amountMl = amountMl,
            loggedAt = Instant.now()
        )
        
        waterIntakeLogs.value = waterIntakeLogs.value + intake
        return intake
    }
    
    override fun getHydrationSummary(userId: String, date: LocalDate): Flow<HydrationSummary> {
        return waterIntakeLogs.map { logs ->
            val dayLogs = logs.filter { it.userId == userId && it.date == date }
            val consumed = dayLogs.sumOf { it.amountMl }
            val schedule = hydrationSchedules.value[userId]
            val target = schedule?.dailyTargetMl ?: BASE_WATER_ML
            
            HydrationSummary(
                date = date,
                targetMl = target,
                consumedMl = consumed,
                percentComplete = (consumed.toFloat() / target * 100).coerceAtMost(100f),
                intakeLogs = dayLogs
            )
        }
    }
    
    override fun getWaterIntakeLogs(userId: String, date: LocalDate): Flow<List<WaterIntake>> {
        return waterIntakeLogs.map { logs ->
            logs.filter { it.userId == userId && it.date == date }
        }
    }
    
    // ============================================
    // PLAN REGENERATION
    // ============================================
    
    override suspend fun regenerateAllPlans(input: PlanGenerationInput) {
        // Generate and save all plans
        val homePlan = generateWorkoutPlan(input, WorkoutType.HOME)
        saveWorkoutPlan(homePlan)
        
        val gymPlan = generateWorkoutPlan(input, WorkoutType.GYM)
        saveWorkoutPlan(gymPlan)
        
        val dietPlan = generateDietPlan(input)
        saveDietPlan(dietPlan)
        
        val hydrationSchedule = generateHydrationSchedule(input)
        saveHydrationSchedule(hydrationSchedule)
    }
    
    override suspend fun shouldRegeneratePlans(userId: String): Boolean {
        val workoutPlan = workoutPlans.value.values.find { it.userId == userId }
        val dietPlan = dietPlans.value[userId]
        
        // Check if plans are expired or don't exist
        val now = Instant.now()
        
        if (workoutPlan == null || dietPlan == null) return true
        if (workoutPlan.expiresAt.isBefore(now)) return true
        if (dietPlan.expiresAt.isBefore(now)) return true
        
        return false
    }
}
