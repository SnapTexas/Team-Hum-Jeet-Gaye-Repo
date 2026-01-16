package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionGenerationResult
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Use case for AI-powered suggestion generation and management.
 * 
 * Generates personalized health suggestions based on 24-hour health data
 * analysis. Uses ML model with fallback to rule-based suggestions.
 * 
 * CRITICAL: All ML operations are wrapped in MLResult for graceful fallback.
 */
interface AISuggestionUseCase {
    
    /**
     * Generates suggestions for the next day based on today's health data.
     * 
     * Analyzes the previous 24 hours of health data and generates
     * personalized suggestions for activity, sleep, nutrition, and hydration.
     * 
     * Uses ML model when available, falls back to rule-based generation.
     * 
     * @return SuggestionGenerationResult containing generated suggestions
     */
    suspend fun generateDailySuggestions(): SuggestionGenerationResult
    
    /**
     * Gets today's active suggestions.
     * 
     * @return Flow emitting list of non-dismissed suggestions
     */
    fun getTodaySuggestions(): Flow<List<Suggestion>>
    
    /**
     * Gets suggestions for a specific date.
     * 
     * @param date The date to get suggestions for
     * @return Flow emitting list of suggestions
     */
    fun getSuggestions(date: LocalDate): Flow<List<Suggestion>>
    
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
     * Checks if suggestions have been generated for today.
     * 
     * @return True if suggestions exist for today
     */
    suspend fun hasTodaySuggestions(): Boolean
    
    /**
     * Cleans up old suggestions.
     * 
     * @param daysToKeep Number of days of suggestions to retain
     */
    suspend fun cleanupOldSuggestions(daysToKeep: Int = 7)
}
