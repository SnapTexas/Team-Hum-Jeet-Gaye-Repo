package com.healthtracker.data.repository

import android.content.Context
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.mapper.UserMapper
import com.healthtracker.data.mapper.UserMapper.toDomain
import com.healthtracker.data.mapper.UserMapper.toEntity
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.User
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.UserSettings
import com.healthtracker.domain.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UserRepository.
 * 
 * Uses local-first strategy:
 * 1. Always save to local Room database first
 * 2. Mark for sync with Firebase
 * 3. Sync in background when online
 * 
 * This ensures app works offline and data is never lost.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    @ApplicationContext private val context: Context
) : UserRepository {
    
    companion object {
        private const val PREFS_NAME = "health_tracker_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    override suspend fun createUser(profile: UserProfile): Result<User> = 
        withContext(Dispatchers.IO) {
            try {
                // Generate unique ID
                val userId = UUID.randomUUID().toString()
                
                // Create entity
                val entity = UserMapper.createUserEntity(
                    id = userId,
                    profile = profile,
                    settings = UserSettings()
                )
                
                // Save to local database
                userDao.insertUser(entity)
                
                Timber.d("User created locally: $userId")
                
                // Convert back to domain model
                val user = entity.toDomain()
                
                // TODO: Sync to Firebase in background
                // This will be implemented when Firebase is configured
                
                Result.Success(user)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create user")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to save user: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override fun getUser(): Flow<User?> {
        return userDao.getUserFlow().map { entity ->
            entity?.toDomain()
        }
    }
    
    override suspend fun getCurrentUser(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val entity = userDao.getUser()
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Error(
                    AppException.AuthException("No user found")
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current user")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to get user: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun updateUser(profile: UserProfile): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val existingEntity = userDao.getUser()
                if (existingEntity == null) {
                    return@withContext Result.Error(
                        AppException.AuthException("No user found to update")
                    )
                }
                
                // Update entity with new profile
                val updatedEntity = existingEntity.copy(
                    name = profile.name,
                    age = profile.age,
                    weightKg = profile.weight,
                    heightCm = profile.height,
                    goal = profile.goal.name,
                    dietPreference = profile.dietPreference?.name,
                    updatedAt = System.currentTimeMillis(),
                    needsSync = true
                )
                
                userDao.updateUser(updatedEntity)
                
                Timber.d("User updated: ${existingEntity.id}")
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update user")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to update user: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun deleteUser(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            userDao.deleteAllUsers()
            
            // Clear onboarding flag
            prefs.edit().remove(KEY_ONBOARDING_COMPLETE).apply()
            
            Timber.d("User deleted")
            
            // TODO: Delete from Firebase
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete user")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to delete user: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun isOnboardingComplete(): Boolean = withContext(Dispatchers.IO) {
        // Check both SharedPreferences flag and database
        val prefComplete = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        val hasUser = userDao.hasUser()
        
        prefComplete && hasUser
    }
    
    override suspend fun setOnboardingComplete() = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        Timber.d("Onboarding marked as complete")
    }
}
