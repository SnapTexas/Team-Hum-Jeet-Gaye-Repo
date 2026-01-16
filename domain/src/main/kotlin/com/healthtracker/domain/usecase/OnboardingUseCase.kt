package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.UserProfile

/**
 * Use case for handling user onboarding.
 * 
 * Validates user input and creates the user profile.
 */
interface OnboardingUseCase {
    
    /**
     * Completes the onboarding process with the given profile.
     * 
     * @param profile The user's profile information
     * @return Result containing the created User or validation errors
     */
    suspend fun completeOnboarding(profile: UserProfile): Result<User>
    
    /**
     * Validates a user profile without saving.
     * 
     * @param profile The profile to validate
     * @return ValidationResult with any errors found
     */
    fun validateProfile(profile: UserProfile): ValidationResult
    
    /**
     * Checks if onboarding has been completed.
     * 
     * @return true if onboarding is complete
     */
    suspend fun isOnboardingComplete(): Boolean
}

/**
 * Result of profile validation.
 * 
 * @property isValid Whether the profile is valid
 * @property errors Map of field names to error messages
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: Map<String, String> = emptyMap()
) {
    companion object {
        fun valid() = ValidationResult(isValid = true)
        
        fun invalid(errors: Map<String, String>) = ValidationResult(
            isValid = false,
            errors = errors
        )
    }
}

/**
 * Constants for profile validation.
 */
object ProfileValidation {
    const val MIN_AGE = 13
    const val MIN_WEIGHT_KG = 20f
    const val MAX_WEIGHT_KG = 500f
    const val MIN_HEIGHT_CM = 50f
    const val MAX_HEIGHT_CM = 300f
}
