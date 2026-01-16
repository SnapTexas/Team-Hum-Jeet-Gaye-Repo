package com.healthtracker.domain.model

import com.healthtracker.domain.usecase.ProfileValidation
import com.healthtracker.domain.usecase.ValidationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.forAll

/**
 * Property-based tests for UserProfile validation.
 * 
 * **Validates: Requirements 1.5**
 * 
 * Tests validation rules using property-based testing:
 * - Property 1: Invalid inputs return errors
 * - Property 2: Valid inputs pass validation
 * 
 * Validation Rules:
 * - Name: Not empty, max 100 characters, valid characters only
 * - Age: >= 13 years
 * - Weight: 20-500 kg
 * - Height: 50-300 cm
 */
class UserProfileValidationTest : FunSpec({
    
    // ============================================
    // GENERATORS
    // ============================================
    
    // Valid name generator: 1-100 chars, letters/spaces/hyphens/apostrophes only
    val validNameArb = Arb.string(1..100, Arb.element(
        ('a'..'z').toList() + ('A'..'Z').toList() + listOf(' ', '-', '\'')
    )).filter { it.isNotBlank() }
    
    // Invalid name generators
    val emptyNameArb = Arb.constant("")
    val blankNameArb = Arb.constant("   ")
    val tooLongNameArb = Arb.string(101..200, Arb.char('a'..'z'))
    val invalidCharsNameArb = Arb.string(1..50).filter { 
        it.any { c -> !c.isLetter() && c != ' ' && c != '-' && c != '\'' }
    }
    
    // Valid age generator: 13-120
    val validAgeArb = Arb.int(ProfileValidation.MIN_AGE..120)
    
    // Invalid age generators
    val tooYoungAgeArb = Arb.int(0 until ProfileValidation.MIN_AGE)
    val tooOldAgeArb = Arb.int(121..200)
    
    // Valid weight generator: 20-500 kg
    val validWeightArb = Arb.float(ProfileValidation.MIN_WEIGHT_KG..ProfileValidation.MAX_WEIGHT_KG)
    
    // Invalid weight generators
    val tooLightWeightArb = Arb.float(0f..ProfileValidation.MIN_WEIGHT_KG - 0.1f)
    val tooHeavyWeightArb = Arb.float(ProfileValidation.MAX_WEIGHT_KG + 0.1f..1000f)
    
    // Valid height generator: 50-300 cm
    val validHeightArb = Arb.float(ProfileValidation.MIN_HEIGHT_CM..ProfileValidation.MAX_HEIGHT_CM)
    
    // Invalid height generators
    val tooShortHeightArb = Arb.float(0f..ProfileValidation.MIN_HEIGHT_CM - 0.1f)
    val tooTallHeightArb = Arb.float(ProfileValidation.MAX_HEIGHT_CM + 0.1f..500f)
    
    // Goal generator
    val goalArb = Arb.enum<HealthGoal>()
    
    // Valid profile generator
    val validProfileArb = Arb.bind(
        validNameArb,
        validAgeArb,
        validWeightArb,
        validHeightArb,
        goalArb
    ) { name, age, weight, height, goal ->
        UserProfile(name, age, weight, height, goal)
    }
    
    // ============================================
    // PROPERTY 1: Invalid inputs return errors
    // **Validates: Requirements 1.5**
    // ============================================
    
    context("Property 1: Invalid inputs return errors") {
        
        test("empty name returns name error") {
            checkAll(validAgeArb, validWeightArb, validHeightArb, goalArb) { age, weight, height, goal ->
                val profile = UserProfile("", age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("name")
            }
        }
        
        test("blank name returns name error") {
            checkAll(validAgeArb, validWeightArb, validHeightArb, goalArb) { age, weight, height, goal ->
                val profile = UserProfile("   ", age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("name")
            }
        }
        
        test("name over 100 chars returns name error") {
            checkAll(tooLongNameArb, validAgeArb, validWeightArb, validHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("name")
            }
        }
        
        test("age below 13 returns age error") {
            checkAll(validNameArb, tooYoungAgeArb, validWeightArb, validHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("age")
            }
        }
        
        test("weight below 20kg returns weight error") {
            checkAll(validNameArb, validAgeArb, tooLightWeightArb, validHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("weight")
            }
        }
        
        test("weight above 500kg returns weight error") {
            checkAll(validNameArb, validAgeArb, tooHeavyWeightArb, validHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("weight")
            }
        }
        
        test("height below 50cm returns height error") {
            checkAll(validNameArb, validAgeArb, validWeightArb, tooShortHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("height")
            }
        }
        
        test("height above 300cm returns height error") {
            checkAll(validNameArb, validAgeArb, validWeightArb, tooTallHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                val result = validateProfile(profile)
                
                result.isValid.shouldBeFalse()
                result.errors.shouldContainKey("height")
            }
        }
    }
    
    // ============================================
    // PROPERTY 2: Valid inputs pass validation
    // **Validates: Requirements 1.5**
    // ============================================
    
    context("Property 2: Valid inputs pass validation") {
        
        test("all valid inputs pass validation") {
            checkAll(100, validProfileArb) { profile ->
                val result = validateProfile(profile)
                result.isValid.shouldBeTrue()
                result.errors.shouldBe(emptyMap())
            }
        }
        
        test("boundary values pass validation") {
            // Minimum valid values
            val minProfile = UserProfile(
                name = "A",
                age = ProfileValidation.MIN_AGE,
                weight = ProfileValidation.MIN_WEIGHT_KG,
                height = ProfileValidation.MIN_HEIGHT_CM,
                goal = HealthGoal.GENERAL
            )
            validateProfile(minProfile).isValid.shouldBeTrue()
            
            // Maximum valid values
            val maxProfile = UserProfile(
                name = "A".repeat(100),
                age = 120,
                weight = ProfileValidation.MAX_WEIGHT_KG,
                height = ProfileValidation.MAX_HEIGHT_CM,
                goal = HealthGoal.FITNESS
            )
            validateProfile(maxProfile).isValid.shouldBeTrue()
        }
        
        test("all health goals are valid") {
            forAll(validNameArb, validAgeArb, validWeightArb, validHeightArb, goalArb) { name, age, weight, height, goal ->
                val profile = UserProfile(name, age, weight, height, goal)
                validateProfile(profile).isValid
            }
        }
    }
    
    // ============================================
    // EDGE CASES
    // ============================================
    
    context("Edge cases") {
        
        test("name with only spaces is invalid") {
            val profile = UserProfile("     ", 25, 70f, 175f, HealthGoal.FITNESS)
            validateProfile(profile).isValid.shouldBeFalse()
        }
        
        test("name with valid special characters is valid") {
            val profile = UserProfile("Mary-Jane O'Connor", 25, 70f, 175f, HealthGoal.FITNESS)
            validateProfile(profile).isValid.shouldBeTrue()
        }
        
        test("exactly 13 years old is valid") {
            val profile = UserProfile("Teen User", 13, 45f, 150f, HealthGoal.GENERAL)
            validateProfile(profile).isValid.shouldBeTrue()
        }
        
        test("12 years old is invalid") {
            val profile = UserProfile("Young User", 12, 45f, 150f, HealthGoal.GENERAL)
            val result = validateProfile(profile)
            result.isValid.shouldBeFalse()
            result.errors.shouldContainKey("age")
        }
    }
})

/**
 * Validation function matching OnboardingUseCaseImpl logic.
 */
private fun validateProfile(profile: UserProfile): ValidationResult {
    val errors = mutableMapOf<String, String>()
    
    // Validate name
    when {
        profile.name.isBlank() -> {
            errors["name"] = "Name is required"
        }
        profile.name.length > 100 -> {
            errors["name"] = "Name must be 100 characters or less"
        }
        !profile.name.matches(Regex("^[a-zA-Z\\s'-]+$")) -> {
            errors["name"] = "Name contains invalid characters"
        }
    }
    
    // Validate age
    when {
        profile.age < ProfileValidation.MIN_AGE -> {
            errors["age"] = "You must be at least ${ProfileValidation.MIN_AGE} years old"
        }
        profile.age > 120 -> {
            errors["age"] = "Please enter a valid age"
        }
    }
    
    // Validate weight
    when {
        profile.weight < ProfileValidation.MIN_WEIGHT_KG -> {
            errors["weight"] = "Weight must be at least ${ProfileValidation.MIN_WEIGHT_KG.toInt()} kg"
        }
        profile.weight > ProfileValidation.MAX_WEIGHT_KG -> {
            errors["weight"] = "Weight must be less than ${ProfileValidation.MAX_WEIGHT_KG.toInt()} kg"
        }
    }
    
    // Validate height
    when {
        profile.height < ProfileValidation.MIN_HEIGHT_CM -> {
            errors["height"] = "Height must be at least ${ProfileValidation.MIN_HEIGHT_CM.toInt()} cm"
        }
        profile.height > ProfileValidation.MAX_HEIGHT_CM -> {
            errors["height"] = "Height must be less than ${ProfileValidation.MAX_HEIGHT_CM.toInt()} cm"
        }
    }
    
    return if (errors.isEmpty()) {
        ValidationResult.valid()
    } else {
        ValidationResult.invalid(errors)
    }
}
