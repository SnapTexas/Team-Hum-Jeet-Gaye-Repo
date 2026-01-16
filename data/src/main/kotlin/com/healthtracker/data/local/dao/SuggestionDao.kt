package com.healthtracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthtracker.data.local.entity.SuggestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for suggestion operations.
 */
@Dao
interface SuggestionDao {
    
    /**
     * Gets suggestions for a specific date.
     * 
     * @param userId User ID
     * @param forDate Date timestamp (epoch millis)
     * @return Flow emitting list of suggestions
     */
    @Query("SELECT * FROM suggestions WHERE userId = :userId AND forDate = :forDate ORDER BY priority ASC")
    fun getSuggestionsByDate(userId: String, forDate: Long): Flow<List<SuggestionEntity>>
    
    /**
     * Gets active (non-dismissed) suggestions for a date.
     * 
     * @param userId User ID
     * @param forDate Date timestamp (epoch millis)
     * @return Flow emitting list of active suggestions
     */
    @Query("SELECT * FROM suggestions WHERE userId = :userId AND forDate = :forDate AND dismissed = 0 ORDER BY priority ASC")
    fun getActiveSuggestions(userId: String, forDate: Long): Flow<List<SuggestionEntity>>
    
    /**
     * Gets suggestions synchronously for a date.
     * 
     * @param userId User ID
     * @param forDate Date timestamp (epoch millis)
     * @return List of suggestions
     */
    @Query("SELECT * FROM suggestions WHERE userId = :userId AND forDate = :forDate ORDER BY priority ASC")
    suspend fun getSuggestionsByDateSync(userId: String, forDate: Long): List<SuggestionEntity>
    
    /**
     * Checks if suggestions exist for a date.
     * 
     * @param userId User ID
     * @param forDate Date timestamp (epoch millis)
     * @return Count of suggestions
     */
    @Query("SELECT COUNT(*) FROM suggestions WHERE userId = :userId AND forDate = :forDate")
    suspend fun countSuggestionsForDate(userId: String, forDate: Long): Int
    
    /**
     * Inserts a suggestion.
     * 
     * @param suggestion The suggestion to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: SuggestionEntity)
    
    /**
     * Inserts multiple suggestions.
     * 
     * @param suggestions List of suggestions to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSuggestions(suggestions: List<SuggestionEntity>)
    
    /**
     * Dismisses a suggestion.
     * 
     * @param id Suggestion ID
     */
    @Query("UPDATE suggestions SET dismissed = 1 WHERE id = :id")
    suspend fun dismissSuggestion(id: String)
    
    /**
     * Marks a suggestion as completed.
     * 
     * @param id Suggestion ID
     */
    @Query("UPDATE suggestions SET completed = 1 WHERE id = :id")
    suspend fun completeSuggestion(id: String)
    
    /**
     * Deletes suggestions older than a date.
     * 
     * @param cutoffDate Cutoff date timestamp (epoch millis)
     */
    @Query("DELETE FROM suggestions WHERE forDate < :cutoffDate")
    suspend fun deleteOldSuggestions(cutoffDate: Long)
    
    /**
     * Deletes all suggestions for a user.
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM suggestions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
    
    /**
     * Deletes all suggestions for a user (alias for data deletion).
     * 
     * @param userId User ID
     */
    @Query("DELETE FROM suggestions WHERE userId = :userId")
    suspend fun deleteAllSuggestionsForUser(userId: String)
}
