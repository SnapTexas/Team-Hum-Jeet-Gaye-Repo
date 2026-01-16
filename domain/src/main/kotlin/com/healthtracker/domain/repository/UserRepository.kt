package com.healthtracker.domain.repository

import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user data operations.
 * 
 * Implementations should handle:
 * - Local persistence (Room database)
 * - Remote sync (Firebase)
 * - Offline-first strategy
 */
interface UserRepository {
    
    /**
     * Creates a new user with the given profile.
     * 
     * @param profile The user's profile information
     * @return Result containing the created User or an error
     */
    suspend fun createUser(profile: UserProfile): Result<User>
    
    /**
     * Gets the current user as a Flow for reactive updates.
     * 
     * @return Flow emitting the current user or null if not logged in
     */
    fun getUser(): Flow<User?>
    
    /**
     * Gets the current user synchronously.
     * 
     * @return Result containing the current User or an error
     */
    suspend fun getCurrentUser(): Result<User>
    
    /**
     * Updates the user's profile.
     * 
     * @param profile The updated profile information
     * @return Result indicating success or failure
     */
    suspend fun updateUser(profile: UserProfile): Result<Unit>
    
    /**
     * Deletes the current user and all associated data.
     * 
     * @return Result indicating success or failure
     */
    suspend fun deleteUser(): Result<Unit>
    
    /**
     * Checks if onboarding has been completed.
     * 
     * @return true if onboarding is complete, false otherwise
     */
    suspend fun isOnboardingComplete(): Boolean
    
    /**
     * Marks onboarding as complete.
     */
    suspend fun setOnboardingComplete()
}
