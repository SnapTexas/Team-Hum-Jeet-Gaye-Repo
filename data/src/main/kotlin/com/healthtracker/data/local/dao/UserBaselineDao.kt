package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtracker.data.local.entity.UserBaselineEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user baseline operations.
 */
@Dao
interface UserBaselineDao {
    
    /**
     * Gets the baseline for a user.
     * 
     * @param userId User ID
     * @return Flow emitting the baseline or null
     */
    @Query("SELECT * FROM user_baseline WHERE userId = :userId")
    fun getBaseline(userId: String): Flow<UserBaselineEntity?>
    
    /**
     * Gets the baseline synchronously.
     * 
     * @param userId User ID
     * @return The baseline or null
     */
    @Query("SELECT * FROM user_baseline WHERE userId = :userId")
    suspend fun getBaselineSync(userId: String): UserBaselineEntity?
    
    /**
     * Inserts or replaces a user baseline.
     * 
     * @param baseline The baseline to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaseline(baseline: UserBaselineEntity)
    
    /**
     * Deletes the baseline for a user.
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM user_baseline WHERE userId = :userId")
    suspend fun deleteBaseline(userId: String)
}
