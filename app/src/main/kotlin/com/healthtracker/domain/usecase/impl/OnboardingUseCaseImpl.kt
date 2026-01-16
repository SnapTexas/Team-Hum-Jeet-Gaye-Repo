package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.OnboardingUseCase
import com.healthtracker.domain.usecase.ProfileValidation
import com.healthtracker.domain.usecase.ValidationResult
import javax.inject.Inject

/**
 * Implementation of OnboardingUseCase.
 * 
 * Handles user profile validation and creation during onboarding.
 * 
 * Validation Rules (from Requirements 1.5):
 * - Name: Not empty, max 100 characters
 * - Age: >= 13 years
 * - Weight: 20-500 kg
 * - Height: 50-300 cm
 * - Goal: Must be selected
 */
class OnboardingUseCaseImpl @Inject constructor(
    private val userRepository: UserRepository
) : OnboardingUseCase {
    
    override suspend fun completeOnboarding(profile: UserProfile): Result<User> {
        // Validate profile first
        val validation = validateProfile(profile)
        if (!validation.isValid) {
            val errorMessage = validation.errors.values.firstOrNull() 
                ?: "Invalid profile data"
            return Result.Error(
                AppException.ValidationException(
                    field = validation.errors.keys.firstOrNull() ?: "profile",
                    reason = errorMessage
                )
            )
        }
        
        // Create user
        return try {
            val result = userRepository.createUser(profile)
            if (result.isSuccess) {
                userRepository.setOnboardingComplete()
            }
            result
        } catch (e: Exception) {
            Result.Error(
                AppException.UnknownException(
                    message = "Failed to create user: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override fun validateProfile(profile: UserProfile): ValidationResult {
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
    
    override suspend fun isOnboardingComplete(): Boolean {
        return userRepository.isOnboardingComplete()
    }
}
