package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.AvatarQueryInput
import com.healthtracker.domain.model.AvatarQueryResult
import com.healthtracker.domain.model.AvatarResponse
import com.healthtracker.domain.model.AvatarState
import kotlinx.coroutines.flow.Flow

/**
 * Use case interface for AI Avatar functionality.
 * 
 * Handles processing of user queries (text and voice) and generates
 * contextual responses based on user's health metrics.
 */
interface AvatarUseCase {
    
    /**
     * Processes a text query from the user.
     * 
     * @param input The query input containing the text and user context
     * @return Result containing the avatar response or error
     */
    suspend fun processQuery(input: AvatarQueryInput): AvatarQueryResult
    
    /**
     * Processes voice input from the user.
     * 
     * @param audioData Raw audio data from speech recognition
     * @param userId The user's ID for context
     * @return Result containing the avatar response or error
     */
    suspend fun processVoiceInput(audioData: ByteArray, userId: String): AvatarQueryResult
    
    /**
     * Gets the current avatar state as a Flow.
     * 
     * @return Flow emitting avatar state changes
     */
    fun getAvatarState(): Flow<AvatarState>
    
    /**
     * Updates the avatar state.
     * 
     * @param state The new avatar state
     */
    suspend fun updateAvatarState(state: AvatarState)
    
    /**
     * Gets conversation history for the current session.
     * 
     * @return List of recent avatar responses
     */
    suspend fun getConversationHistory(): List<AvatarResponse>
    
    /**
     * Clears the conversation history.
     */
    suspend fun clearConversationHistory()
}
