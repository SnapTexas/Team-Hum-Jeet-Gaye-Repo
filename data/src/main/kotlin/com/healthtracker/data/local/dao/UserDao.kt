package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.healthtracker.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for User operations.
 */
@Dao
interface UserDao {
    
    /**
     * Inserts a new user or replaces existing one.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    /**
     * Updates an existing user.
     */
    @Update
    suspend fun updateUser(user: UserEntity)
    
    /**
     * Gets the current user as a Flow for reactive updates.
     * Assumes single user per device.
     */
    @Query("SELECT * FROM users LIMIT 1")
    fun getUserFlow(): Flow<UserEntity?>
    
    /**
     * Gets the current user synchronously.
     */
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUser(): UserEntity?
    
    /**
     * Gets a user by ID.
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?
    
    /**
     * Deletes all users (for logout/account deletion).
     */
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
    
    /**
     * Deletes a specific user.
     */
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)
    
    /**
     * Checks if any user exists (for onboarding check).
     */
    @Query("SELECT COUNT(*) > 0 FROM users")
    suspend fun hasUser(): Boolean
    
    /**
     * Gets users that need to be synced.
     */
    @Query("SELECT * FROM users WHERE needsSync = 1")
    suspend fun getUsersNeedingSync(): List<UserEntity>
    
    /**
     * Marks user as synced.
     */
    @Query("UPDATE users SET needsSync = 0 WHERE id = :userId")
    suspend fun markAsSynced(userId: String)
}
