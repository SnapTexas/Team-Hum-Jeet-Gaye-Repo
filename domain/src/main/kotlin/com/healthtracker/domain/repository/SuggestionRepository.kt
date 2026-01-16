package com.healthtracker.domain.repository

import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionGenerationResult
import com.healthtracker.domain.model.SuggestionInput
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for suggestion operations.
 * 
 * Handles storage and retrieval of AI-generated health suggestions.
 */
interface SuggestionRepository {
    
    /**
     * Gets suggestions for a specific date.
     * 
     * @param date The date to get suggestions for
     * @return Flow emitting list of suggestions
     */
    fun getSuggestions(date: LocalDate): Flow<List<Suggestion>>
    
    /**
     * Gets today's active (non-dismissed) suggestions.
     * 
     * @return Flow emitting list of active suggestions
     */
    fun getTodaySuggestions(): Flow<List<Suggestion>>
    
    /**
     * Gets suggestions synchronously for a specific date.
     * 
     * @param date The date to get suggestions for
     * @return List of suggestions
     */
    suspend fun getSuggestionsSync(date: LocalDate): List<Suggestion>
    
    /**
     * Saves generated suggestions to the database.
     * 
     * @param suggestions List of suggestions to save
     */
    suspend fun saveSuggestions(suggestions: List<Suggestion>)
    
    /**
     * Dismisses a suggestion.
     * 
     * @param suggestionId ID of the suggestion to dismiss
     * @return Result indicating success or failure
     */
    suspend fun dismissSuggestion(suggestionId: String): Result<Unit>
    
    /**
     * Marks a suggestion as completed.
     * 
     * @param suggestionId ID of the suggestion to mark complete
     * @return Result indicating success or failure
     */
    suspend fun completeSuggestion(suggestionId: String): Result<Unit>
    
    /**
     * Deletes old suggestions (older than specified days).
     * 
     * @param daysToKeep Number of days of suggestions to retain
     */
    suspend fun deleteOldSuggestions(daysToKeep: Int = 7)
    
    /**
     * Checks if suggestions have been generated for a specific date.
     * 
     * @param date The date to check
     * @return True if suggestions exist for the date
     */
    suspend fun hasSuggestionsForDate(date: LocalDate): Boolean
}
