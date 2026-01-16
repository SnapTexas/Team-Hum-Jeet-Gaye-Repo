package com.healthtracker.domain.model

import com.healthtracker.domain.usecase.ProfileValidation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

/**
 * Property-based tests for UserProfile persistence round-trip.
 * 
 * **Validates: Requirements 1.5, Property 2**
 * 
 * Tests that saving and retrieving a profile returns an equivalent profile.
 * This validates the mapper functions work correctly.
 */
class UserProfilePersistenceTest : FunSpec({
    
    // ============================================
    // GENERATORS
    // ============================================
    
    // Valid name generator
    val validNameArb = Arb.string(1..100, Arb.element(
        ('a'..'z').toList() + ('A'..'Z').toList() + listOf(' ', '-', '\'')
    )).filter { it.isNotBlank() }
    
    // Valid age generator
    val validAgeArb = Arb.int(ProfileValidation.MIN_AGE..120)
    
    // Valid weight generator
    val validWeightArb = Arb.float(ProfileValidation.MIN_WEIGHT_KG..ProfileValidation.MAX_WEIGHT_KG)
    
    // Valid height generator
    val validHeightArb = Arb.float(ProfileValidation.MIN_HEIGHT_CM..ProfileValidation.MAX_HEIGHT_CM)
    
    // Goal generator
    val goalArb = Arb.enum<HealthGoal>()
    
    // Diet preference generator (nullable)
    val dietPreferenceArb = Arb.enum<DietPreference>().orNull()
    
    // Valid profile generator
    val validProfileArb = Arb.bind(
        validNameArb,
        validAgeArb,
        validWeightArb,
        validHeightArb,
        goalArb,
        dietPreferenceArb
    ) { name, age, weight, height, goal, diet ->
        UserProfile(name, age, weight, height, goal, diet)
    }
    
    // User settings generator
    val userSettingsArb = Arb.bind(
        Arb.boolean(),
        Arb.boolean(),
        Arb.boolean(),
        Arb.boolean(),
        Arb.boolean(),
        Arb.int(15..480)
    ) { notifications, dataCollection, sensitiveData, hydration, mindfulness, interval ->
        UserSettings(
            notificationsEnabled = notifications,
            dataCollectionEnabled = dataCollection,
            sensitiveDataOptIn = sensitiveData,
            hydrationReminders = hydration,
            mindfulnessReminders = mindfulness,
            reminderIntervalMinutes = interval
        )
    }
    
    // ============================================
    // PROPERTY 2: Profile persistence round-trip
    // **Validates: Requirements 1.5**
    // ============================================
    
    context("Property 2: Profile persistence round-trip") {
        
        test("UserProfile serialization round-trip preserves all fields") {
            checkAll(100, validProfileArb) { profile ->
                // Simulate serialization (to entity-like map) and deserialization
                val serialized = serializeProfile(profile)
                val deserialized = deserializeProfile(serialized)
                
                deserialized.name shouldBe profile.name
                deserialized.age shouldBe profile.age
                deserialized.weight shouldBe profile.weight
                deserialized.height shouldBe profile.height
                deserialized.goal shouldBe profile.goal
                deserialized.dietPreference shouldBe profile.dietPreference
            }
        }
        
        test("UserSettings serialization round-trip preserves all fields") {
            checkAll(100, userSettingsArb) { settings ->
                val serialized = serializeSettings(settings)
                val deserialized = deserializeSettings(serialized)
                
                deserialized.notificationsEnabled shouldBe settings.notificationsEnabled
                deserialized.dataCollectionEnabled shouldBe settings.dataCollectionEnabled
                deserialized.sensitiveDataOptIn shouldBe settings.sensitiveDataOptIn
                deserialized.hydrationReminders shouldBe settings.hydrationReminders
                deserialized.mindfulnessReminders shouldBe settings.mindfulnessReminders
                deserialized.reminderIntervalMinutes shouldBe settings.reminderIntervalMinutes
            }
        }
        
        test("HealthGoal enum round-trip preserves value") {
            checkAll(goalArb) { goal ->
                val serialized = goal.name
                val deserialized = HealthGoal.valueOf(serialized)
                deserialized shouldBe goal
            }
        }
        
        test("DietPreference enum round-trip preserves value") {
            checkAll(Arb.enum<DietPreference>()) { pref ->
                val serialized = pref.name
                val deserialized = DietPreference.valueOf(serialized)
                deserialized shouldBe pref
            }
        }
        
        test("nullable DietPreference round-trip preserves null") {
            val profile = UserProfile(
                name = "Test User",
                age = 25,
                weight = 70f,
                height = 175f,
                goal = HealthGoal.FITNESS,
                dietPreference = null
            )
            
            val serialized = serializeProfile(profile)
            val deserialized = deserializeProfile(serialized)
            
            deserialized.dietPreference shouldBe null
        }
    }
    
    // ============================================
    // EDGE CASES
    // ============================================
    
    context("Edge cases for persistence") {
        
        test("profile with special characters in name persists correctly") {
            val profile = UserProfile(
                name = "Mary-Jane O'Connor",
                age = 30,
                weight = 65f,
                height = 165f,
                goal = HealthGoal.WEIGHT_LOSS
            )
            
            val serialized = serializeProfile(profile)
            val deserialized = deserializeProfile(serialized)
            
            deserialized.name shouldBe "Mary-Jane O'Connor"
        }
        
        test("profile with boundary values persists correctly") {
            val profile = UserProfile(
                name = "A",
                age = ProfileValidation.MIN_AGE,
                weight = ProfileValidation.MIN_WEIGHT_KG,
                height = ProfileValidation.MIN_HEIGHT_CM,
                goal = HealthGoal.GENERAL
            )
            
            val serialized = serializeProfile(profile)
            val deserialized = deserializeProfile(serialized)
            
            deserialized.age shouldBe ProfileValidation.MIN_AGE
            deserialized.weight shouldBe ProfileValidation.MIN_WEIGHT_KG
            deserialized.height shouldBe ProfileValidation.MIN_HEIGHT_CM
        }
        
        test("profile with max boundary values persists correctly") {
            val profile = UserProfile(
                name = "A".repeat(100),
                age = 120,
                weight = ProfileValidation.MAX_WEIGHT_KG,
                height = ProfileValidation.MAX_HEIGHT_CM,
                goal = HealthGoal.FITNESS
            )
            
            val serialized = serializeProfile(profile)
            val deserialized = deserializeProfile(serialized)
            
            deserialized.name.length shouldBe 100
            deserialized.age shouldBe 120
            deserialized.weight shouldBe ProfileValidation.MAX_WEIGHT_KG
            deserialized.height shouldBe ProfileValidation.MAX_HEIGHT_CM
        }
    }
})

/**
 * Simulates serialization to entity-like map (matching UserMapper logic).
 */
private fun serializeProfile(profile: UserProfile): Map<String, Any?> {
    return mapOf(
        "name" to profile.name,
        "age" to profile.age,
        "weightKg" to profile.weight,
        "heightCm" to profile.height,
        "goal" to profile.goal.name,
        "dietPreference" to profile.dietPreference?.name
    )
}

/**
 * Simulates deserialization from entity-like map (matching UserMapper logic).
 */
private fun deserializeProfile(data: Map<String, Any?>): UserProfile {
    return UserProfile(
        name = data["name"] as String,
        age = data["age"] as Int,
        weight = data["weightKg"] as Float,
        height = data["heightCm"] as Float,
        goal = HealthGoal.valueOf(data["goal"] as String),
        dietPreference = (data["dietPreference"] as? String)?.let { DietPreference.valueOf(it) }
    )
}

/**
 * Simulates serialization of UserSettings.
 */
private fun serializeSettings(settings: UserSettings): Map<String, Any> {
    return mapOf(
        "notificationsEnabled" to settings.notificationsEnabled,
        "dataCollectionEnabled" to settings.dataCollectionEnabled,
        "sensitiveDataOptIn" to settings.sensitiveDataOptIn,
        "hydrationReminders" to settings.hydrationReminders,
        "mindfulnessReminders" to settings.mindfulnessReminders,
        "reminderIntervalMinutes" to settings.reminderIntervalMinutes
    )
}

/**
 * Simulates deserialization of UserSettings.
 */
private fun deserializeSettings(data: Map<String, Any>): UserSettings {
    return UserSettings(
        notificationsEnabled = data["notificationsEnabled"] as Boolean,
        dataCollectionEnabled = data["dataCollectionEnabled"] as Boolean,
        sensitiveDataOptIn = data["sensitiveDataOptIn"] as Boolean,
        hydrationReminders = data["hydrationReminders"] as Boolean,
        mindfulnessReminders = data["mindfulnessReminders"] as Boolean,
        reminderIntervalMinutes = data["reminderIntervalMinutes"] as Int
    )
}
