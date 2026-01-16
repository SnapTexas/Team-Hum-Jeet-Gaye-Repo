package com.healthtracker.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthtracker.domain.model.DailyNutritionSummary
import com.healthtracker.domain.model.FoodCategory
import com.healthtracker.domain.model.FoodItem
import com.healthtracker.domain.model.LogMealInput
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.domain.model.NutritionInfo
import com.healthtracker.domain.repository.DietRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DietRepository with SharedPreferences persistence.
 */
@Singleton
class DietRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DietRepository {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("diet_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // In-memory storage backed by SharedPreferences
    private val loggedMeals = MutableStateFlow<List<LoggedMeal>>(loadMealsFromPrefs())
    private val recentFoodNames = MutableStateFlow<List<String>>(emptyList())
    
    // Food database with common foods and their nutrition
    private val foodDatabase: Map<String, NutritionInfo> = buildFoodDatabase()
    
    init {
        android.util.Log.d("DietRepo", "Repository initialized with ${loggedMeals.value.size} meals from storage")
    }
    
    // ============================================
    // PERSISTENCE HELPERS
    // ============================================
    
    private fun loadMealsFromPrefs(): List<LoggedMeal> {
        return try {
            val json = prefs.getString("meals", null) ?: return emptyList()
            val type = object : TypeToken<List<MealData>>() {}.type
            val mealDataList: List<MealData> = gson.fromJson(json, type)
            mealDataList.map { it.toLoggedMeal() }
        } catch (e: Exception) {
            android.util.Log.e("DietRepo", "Error loading meals: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveMealsToPrefs(meals: List<LoggedMeal>) {
        try {
            val mealDataList = meals.map { MealData.fromLoggedMeal(it) }
            val json = gson.toJson(mealDataList)
            prefs.edit().putString("meals", json).apply()
            android.util.Log.d("DietRepo", "Saved ${meals.size} meals to storage")
        } catch (e: Exception) {
            android.util.Log.e("DietRepo", "Error saving meals: ${e.message}")
        }
    }
    
    // Simple data class for JSON serialization (LocalDate and Instant don't serialize well)
    private data class MealData(
        val id: String,
        val userId: String,
        val dateStr: String,
        val mealType: String,
        val foodName: String,
        val servings: Float,
        val calories: Int,
        val protein: Float,
        val carbs: Float,
        val fat: Float,
        val fiber: Float,
        val imageUri: String?,
        val wasAutoClassified: Boolean,
        val classificationConfidence: Float?,
        val loggedAtMillis: Long
    ) {
        fun toLoggedMeal(): LoggedMeal = LoggedMeal(
            id = id,
            userId = userId,
            date = LocalDate.parse(dateStr),
            mealType = LoggedMealType.valueOf(mealType),
            foodName = foodName,
            servings = servings,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
            imageUri = imageUri,
            wasAutoClassified = wasAutoClassified,
            classificationConfidence = classificationConfidence,
            loggedAt = Instant.ofEpochMilli(loggedAtMillis)
        )
        
        companion object {
            fun fromLoggedMeal(meal: LoggedMeal): MealData = MealData(
                id = meal.id,
                userId = meal.userId,
                dateStr = meal.date.toString(),
                mealType = meal.mealType.name,
                foodName = meal.foodName,
                servings = meal.servings,
                calories = meal.calories,
                protein = meal.protein,
                carbs = meal.carbs,
                fat = meal.fat,
                fiber = meal.fiber,
                imageUri = meal.imageUri,
                wasAutoClassified = meal.wasAutoClassified,
                classificationConfidence = meal.classificationConfidence,
                loggedAtMillis = meal.loggedAt.toEpochMilli()
            )
        }
    }
    
    // ============================================
    // MEAL LOGGING
    // ============================================
    
    override suspend fun logMeal(input: LogMealInput): LoggedMeal {
        val meal = LoggedMeal(
            id = UUID.randomUUID().toString(),
            userId = input.userId,
            date = input.date,
            mealType = input.mealType,
            foodName = input.foodName,
            servings = input.servings,
            calories = input.nutrition.calories,
            protein = input.nutrition.protein,
            carbs = input.nutrition.carbs,
            fat = input.nutrition.fat,
            fiber = input.nutrition.fiber,
            imageUri = input.imageUri,
            wasAutoClassified = input.wasAutoClassified,
            classificationConfidence = input.classificationConfidence,
            loggedAt = Instant.now()
        )
        
        android.util.Log.d("DietRepo", "Logging meal: ${meal.foodName}, Calories: ${meal.calories}")
        val updatedMeals = loggedMeals.value + meal
        loggedMeals.value = updatedMeals
        saveMealsToPrefs(updatedMeals)
        android.util.Log.d("DietRepo", "Total meals now: ${updatedMeals.size}")
        
        // Track recent foods
        val recent = recentFoodNames.value.toMutableList()
        recent.remove(input.foodName)
        recent.add(0, input.foodName)
        recentFoodNames.value = recent.take(20)
        
        return meal
    }
    
    override fun getMeals(userId: String, date: LocalDate): Flow<List<LoggedMeal>> {
        android.util.Log.d("DietRepo", "getMeals called - date: $date, total meals: ${loggedMeals.value.size}")
        return loggedMeals.map { meals ->
            val filtered = meals.filter { it.date == date }
            android.util.Log.d("DietRepo", "Returning ${filtered.size} meals for date $date")
            filtered.sortedBy { it.loggedAt }
        }
    }
    
    override fun getMealsByType(
        userId: String,
        date: LocalDate,
        mealType: LoggedMealType
    ): Flow<List<LoggedMeal>> {
        return loggedMeals.map { meals ->
            meals.filter { 
                it.userId == userId && it.date == date && it.mealType == mealType 
            }.sortedBy { it.loggedAt }
        }
    }
    
    override suspend fun deleteMeal(mealId: String) {
        val updatedMeals = loggedMeals.value.filter { it.id != mealId }
        loggedMeals.value = updatedMeals
        saveMealsToPrefs(updatedMeals)
    }
    
    override suspend fun updateMeal(meal: LoggedMeal) {
        val updatedMeals = loggedMeals.value.map { 
            if (it.id == meal.id) meal else it 
        }
        loggedMeals.value = updatedMeals
        saveMealsToPrefs(updatedMeals)
    }
    
    // ============================================
    // NUTRITION SUMMARY
    // ============================================
    
    override fun getDailyNutritionSummary(
        userId: String,
        date: LocalDate
    ): Flow<DailyNutritionSummary> {
        android.util.Log.d("DietRepo", "getDailyNutritionSummary called for date: $date")
        return getMeals(userId, date).map { meals ->
            val summary = createNutritionSummary(date, meals)
            android.util.Log.d("DietRepo", "Summary: ${summary.totalCalories} calories from ${meals.size} meals")
            summary
        }
    }
    
    override fun getNutritionSummaryRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DailyNutritionSummary>> {
        return loggedMeals.map { allMeals ->
            val userMeals = allMeals.filter { it.userId == userId }
            
            generateSequence(startDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endDate) }
                .map { date ->
                    val dayMeals = userMeals.filter { it.date == date }
                    createNutritionSummary(date, dayMeals)
                }
                .toList()
        }
    }
    
    private fun createNutritionSummary(date: LocalDate, meals: List<LoggedMeal>): DailyNutritionSummary {
        return DailyNutritionSummary(
            date = date,
            totalCalories = meals.sumOf { it.calories },
            totalProtein = meals.sumOf { it.protein.toDouble() }.toFloat(),
            totalCarbs = meals.sumOf { it.carbs.toDouble() }.toFloat(),
            totalFat = meals.sumOf { it.fat.toDouble() }.toFloat(),
            totalFiber = meals.sumOf { it.fiber.toDouble() }.toFloat(),
            meals = meals,
            calorieTarget = 2000, // Would come from user profile
            proteinTarget = 120f,
            carbsTarget = 250f,
            fatTarget = 65f
        )
    }
    
    // ============================================
    // FOOD DATABASE
    // ============================================
    
    override suspend fun searchFood(query: String): List<FoodItem> {
        val lowerQuery = query.lowercase()
        return foodDatabase.entries
            .filter { (name, _) -> 
                name.lowercase().contains(lowerQuery) 
            }
            .take(20)
            .map { (name, nutrition) ->
                FoodItem(
                    id = name.hashCode().toString(),
                    name = name,
                    category = categorizeFood(name),
                    nutrition = nutrition
                )
            }
    }
    
    override suspend fun getNutritionInfo(foodName: String): NutritionInfo? {
        // Try exact match first
        foodDatabase[foodName]?.let { return it }
        
        // Try case-insensitive match
        val lowerName = foodName.lowercase()
        return foodDatabase.entries
            .find { it.key.lowercase() == lowerName }
            ?.value
    }
    
    override suspend fun getRecentFoods(userId: String, limit: Int): List<FoodItem> {
        return recentFoodNames.value
            .take(limit)
            .mapNotNull { name ->
                foodDatabase[name]?.let { nutrition ->
                    FoodItem(
                        id = name.hashCode().toString(),
                        name = name,
                        category = categorizeFood(name),
                        nutrition = nutrition
                    )
                }
            }
    }
    
    override suspend fun getFrequentFoods(userId: String, limit: Int): List<FoodItem> {
        // Count frequency of each food in logged meals
        val frequency = loggedMeals.value
            .filter { it.userId == userId }
            .groupingBy { it.foodName }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
        
        return frequency.mapNotNull { (name, _) ->
            foodDatabase[name]?.let { nutrition ->
                FoodItem(
                    id = name.hashCode().toString(),
                    name = name,
                    category = categorizeFood(name),
                    nutrition = nutrition
                )
            }
        }
    }
    
    private fun categorizeFood(name: String): FoodCategory {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("apple") || lowerName.contains("banana") || 
            lowerName.contains("orange") || lowerName.contains("berry") -> FoodCategory.FRUITS
            
            lowerName.contains("salad") || lowerName.contains("broccoli") ||
            lowerName.contains("carrot") || lowerName.contains("spinach") -> FoodCategory.VEGETABLES
            
            lowerName.contains("rice") || lowerName.contains("bread") ||
            lowerName.contains("pasta") || lowerName.contains("oat") -> FoodCategory.GRAINS
            
            lowerName.contains("chicken") || lowerName.contains("beef") ||
            lowerName.contains("fish") || lowerName.contains("egg") ||
            lowerName.contains("salmon") || lowerName.contains("tofu") -> FoodCategory.PROTEIN
            
            lowerName.contains("milk") || lowerName.contains("cheese") ||
            lowerName.contains("yogurt") -> FoodCategory.DAIRY
            
            lowerName.contains("cookie") || lowerName.contains("cake") ||
            lowerName.contains("candy") || lowerName.contains("chocolate") -> FoodCategory.SWEETS
            
            lowerName.contains("coffee") || lowerName.contains("tea") ||
            lowerName.contains("juice") || lowerName.contains("smoothie") -> FoodCategory.BEVERAGES
            
            else -> FoodCategory.OTHER
        }
    }
    
    /**
     * Builds the food database with common foods and their nutrition info.
     */
    private fun buildFoodDatabase(): Map<String, NutritionInfo> {
        return mapOf(
            // Fruits
            "Apple" to NutritionInfo("Apple", "1 medium", 182f, 95, 0.5f, 25f, 0.3f, 4.4f, 19f, 2f),
            "Banana" to NutritionInfo("Banana", "1 medium", 118f, 105, 1.3f, 27f, 0.4f, 3.1f, 14f, 1f),
            "Orange" to NutritionInfo("Orange", "1 medium", 131f, 62, 1.2f, 15f, 0.2f, 3.1f, 12f, 0f),
            "Strawberries" to NutritionInfo("Strawberries", "1 cup", 144f, 46, 1f, 11f, 0.4f, 2.9f, 7f, 1f),
            "Blueberries" to NutritionInfo("Blueberries", "1 cup", 148f, 84, 1.1f, 21f, 0.5f, 3.6f, 15f, 1f),
            "Grapes" to NutritionInfo("Grapes", "1 cup", 151f, 104, 1.1f, 27f, 0.2f, 1.4f, 23f, 3f),
            
            // Vegetables
            "Broccoli" to NutritionInfo("Broccoli", "1 cup", 91f, 31, 2.5f, 6f, 0.3f, 2.4f, 2f, 30f),
            "Spinach" to NutritionInfo("Spinach", "1 cup raw", 30f, 7, 0.9f, 1.1f, 0.1f, 0.7f, 0.1f, 24f),
            "Carrot" to NutritionInfo("Carrot", "1 medium", 61f, 25, 0.6f, 6f, 0.1f, 1.7f, 3f, 42f),
            "Tomato" to NutritionInfo("Tomato", "1 medium", 123f, 22, 1.1f, 4.8f, 0.2f, 1.5f, 3.2f, 6f),
            "Cucumber" to NutritionInfo("Cucumber", "1 cup", 104f, 16, 0.7f, 3.8f, 0.1f, 0.5f, 1.7f, 2f),
            "Bell Pepper" to NutritionInfo("Bell Pepper", "1 medium", 119f, 24, 1f, 6f, 0.2f, 2.1f, 4f, 4f),
            
            // Proteins
            "Chicken Breast" to NutritionInfo("Chicken Breast", "100g", 100f, 165, 31f, 0f, 3.6f, 0f, 0f, 74f),
            "Salmon" to NutritionInfo("Salmon", "100g", 100f, 208, 20f, 0f, 13f, 0f, 0f, 59f),
            "Egg" to NutritionInfo("Egg", "1 large", 50f, 78, 6f, 0.6f, 5f, 0f, 0.6f, 62f),
            "Beef Steak" to NutritionInfo("Beef Steak", "100g", 100f, 271, 26f, 0f, 18f, 0f, 0f, 54f),
            "Tofu" to NutritionInfo("Tofu", "100g", 100f, 76, 8f, 1.9f, 4.8f, 0.3f, 0.6f, 7f),
            "Tuna" to NutritionInfo("Tuna", "100g", 100f, 132, 28f, 0f, 1f, 0f, 0f, 42f),
            
            // Grains
            "White Rice" to NutritionInfo("White Rice", "1 cup cooked", 158f, 206, 4.3f, 45f, 0.4f, 0.6f, 0f, 1f),
            "Brown Rice" to NutritionInfo("Brown Rice", "1 cup cooked", 195f, 216, 5f, 45f, 1.8f, 3.5f, 0.7f, 10f),
            "Pasta" to NutritionInfo("Pasta", "1 cup cooked", 140f, 221, 8.1f, 43f, 1.3f, 2.5f, 0.8f, 1f),
            "Bread" to NutritionInfo("Bread", "1 slice", 30f, 79, 2.7f, 15f, 1f, 0.6f, 1.5f, 147f),
            "Oatmeal" to NutritionInfo("Oatmeal", "1 cup cooked", 234f, 158, 6f, 27f, 3.2f, 4f, 1.1f, 115f),
            "Quinoa" to NutritionInfo("Quinoa", "1 cup cooked", 185f, 222, 8.1f, 39f, 3.6f, 5.2f, 1.6f, 13f),
            
            // Dairy
            "Milk" to NutritionInfo("Milk", "1 cup", 244f, 149, 8f, 12f, 8f, 0f, 12f, 105f),
            "Greek Yogurt" to NutritionInfo("Greek Yogurt", "1 cup", 245f, 100, 17f, 6f, 0.7f, 0f, 4f, 61f),
            "Cheese" to NutritionInfo("Cheese", "1 oz", 28f, 113, 7f, 0.4f, 9f, 0f, 0.1f, 174f),
            "Cottage Cheese" to NutritionInfo("Cottage Cheese", "1 cup", 226f, 206, 28f, 6f, 9f, 0f, 6f, 918f),
            
            // Mixed/Prepared Foods
            "Pizza" to NutritionInfo("Pizza", "1 slice", 107f, 285, 12f, 36f, 10f, 2.5f, 4f, 640f),
            "Hamburger" to NutritionInfo("Hamburger", "1 burger", 226f, 540, 34f, 40f, 27f, 2f, 8f, 791f),
            "Salad" to NutritionInfo("Salad", "1 bowl", 200f, 150, 5f, 15f, 8f, 4f, 5f, 300f),
            "Sandwich" to NutritionInfo("Sandwich", "1 sandwich", 200f, 350, 15f, 40f, 14f, 3f, 5f, 600f),
            "Burrito" to NutritionInfo("Burrito", "1 burrito", 300f, 450, 20f, 50f, 18f, 6f, 4f, 900f),
            "Sushi" to NutritionInfo("Sushi", "6 pieces", 180f, 280, 12f, 38f, 8f, 2f, 6f, 500f),
            
            // Snacks
            "Almonds" to NutritionInfo("Almonds", "1 oz", 28f, 164, 6f, 6f, 14f, 3.5f, 1.2f, 0f),
            "Peanut Butter" to NutritionInfo("Peanut Butter", "2 tbsp", 32f, 188, 8f, 6f, 16f, 1.9f, 3f, 136f),
            "Protein Bar" to NutritionInfo("Protein Bar", "1 bar", 60f, 200, 20f, 22f, 7f, 3f, 6f, 150f),
            "Granola Bar" to NutritionInfo("Granola Bar", "1 bar", 35f, 140, 3f, 23f, 5f, 2f, 8f, 80f),
            
            // Beverages
            "Coffee" to NutritionInfo("Coffee", "1 cup", 240f, 2, 0.3f, 0f, 0f, 0f, 0f, 5f),
            "Orange Juice" to NutritionInfo("Orange Juice", "1 cup", 248f, 112, 1.7f, 26f, 0.5f, 0.5f, 21f, 2f),
            "Smoothie" to NutritionInfo("Smoothie", "1 cup", 250f, 180, 5f, 35f, 2f, 3f, 25f, 50f),
            "Protein Shake" to NutritionInfo("Protein Shake", "1 serving", 300f, 150, 25f, 8f, 2f, 1f, 3f, 200f)
        )
    }
}
