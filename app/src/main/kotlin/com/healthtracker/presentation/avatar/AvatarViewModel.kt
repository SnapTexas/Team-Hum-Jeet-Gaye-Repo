package com.healthtracker.presentation.avatar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.domain.model.AvatarResponse
import com.healthtracker.domain.model.AvatarState
import com.healthtracker.domain.model.ResponseType
import com.healthtracker.domain.usecase.AvatarUseCase
import com.healthtracker.service.ai.EdgeTTSService
import com.healthtracker.service.ai.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the AI Avatar screen.
 * Manages avatar state, query processing, and conversation history.
 */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val avatarUseCase: AvatarUseCase,
    private val featureFlagManager: FeatureFlagManager,
    private val openAIService: OpenAIService,
    private val edgeTTSService: EdgeTTSService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AvatarUiState())
    val uiState: StateFlow<AvatarUiState> = _uiState.asStateFlow()
    
    // Default user ID - in production, get from auth
    private val userId = "default_user"
    
    init {
        checkFeatureAvailability()
        if (featureFlagManager.isAvatarEnabled()) {
            observeAvatarState()
            loadConversationHistory()
            showGreeting()
        } else {
            _uiState.update { it.copy(
                error = "AI Avatar is temporarily unavailable. Please check back later.",
                isFeatureDisabled = true
            )}
        }
    }
    
    private fun checkFeatureAvailability() {
        _uiState.update { it.copy(
            isFeatureDisabled = !featureFlagManager.isAvatarEnabled()
        )}
    }
    
    /**
     * Observes avatar state changes from the use case.
     */
    private fun observeAvatarState() {
        viewModelScope.launch {
            avatarUseCase.getAvatarState().collect { state ->
                _uiState.update { it.copy(avatarState = state) }
            }
        }
    }
    
    /**
     * Loads existing conversation history.
     */
    private fun loadConversationHistory() {
        viewModelScope.launch {
            val history = avatarUseCase.getConversationHistory()
            _uiState.update { it.copy(conversationHistory = history) }
        }
    }
    
    /**
     * Shows initial greeting when avatar is opened - USING OPENAI
     */
    private fun showGreeting() {
        viewModelScope.launch {
            try {
                Timber.d("Avatar: Showing greeting via OpenAI")
                
                val result = openAIService.generateResponse("Hello", includeHistory = false)
                
                result.onSuccess { responseText ->
                    val avatarResponse = AvatarResponse(
                        id = UUID.randomUUID().toString(),
                        queryId = UUID.randomUUID().toString(),
                        text = responseText,
                        type = ResponseType.GREETING,
                        metrics = null,
                        suggestions = getDefaultSuggestions(),
                        timestamp = Instant.now()
                    )
                    
                    _uiState.update { state ->
                        state.copy(
                            currentResponse = avatarResponse,
                            suggestions = getDefaultSuggestions(),
                            avatarState = AvatarState.IDLE
                        )
                    }
                    
                    // Speak greeting
                    speakText(responseText)
                }
                
                result.onFailure { error ->
                    Timber.e(error, "Avatar: Greeting failed")
                    _uiState.update { state ->
                        state.copy(
                            error = "Failed to load greeting: ${error.message}",
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Avatar: Greeting exception")
                _uiState.update { it.copy(
                    error = "Failed to initialize avatar",
                    avatarState = AvatarState.IDLE
                )}
            }
        }
    }
    
    /**
     * Submits a text query to the avatar - USING OPENAI DIRECTLY
     */
    fun submitQuery(query: String) {
        if (query.isBlank()) return
        
        if (!featureFlagManager.isAvatarEnabled()) {
            _uiState.update { it.copy(
                error = "AI Avatar is temporarily unavailable.",
                isFeatureDisabled = true
            )}
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(
                currentQuery = query,
                avatarState = AvatarState.PROCESSING,
                error = null
            )}
            
            try {
                Timber.d("Avatar: Sending query to OpenAI: $query")
                
                // Use OpenAI directly - no rule-based patterns
                val result = openAIService.generateResponse(query, includeHistory = true)
                
                result.onSuccess { responseText ->
                    Timber.d("Avatar: Received OpenAI response: $responseText")
                    
                    // Create avatar response
                    val avatarResponse = AvatarResponse(
                        id = UUID.randomUUID().toString(),
                        queryId = UUID.randomUUID().toString(),
                        text = responseText,
                        type = ResponseType.METRIC_DATA,
                        metrics = null,
                        suggestions = getDefaultSuggestions(),
                        timestamp = Instant.now()
                    )
                    
                    _uiState.update { state ->
                        // Move current response to history
                        val updatedHistory = state.currentResponse?.let {
                            state.conversationHistory + it
                        } ?: state.conversationHistory
                        
                        state.copy(
                            currentResponse = avatarResponse,
                            conversationHistory = updatedHistory.takeLast(20),
                            suggestions = getDefaultSuggestions(),
                            currentQuery = "",
                            avatarState = AvatarState.IDLE
                        )
                    }
                    
                    // Speak response
                    speakText(responseText)
                }
                
                result.onFailure { error ->
                    Timber.e(error, "Avatar: OpenAI failed")
                    
                    val errorMessage = when {
                        error.message?.contains("API key") == true -> 
                            "⚠️ OpenAI API key issue. Please check configuration."
                        error.message?.contains("Rate limit") == true -> 
                            "⏳ Too many requests. Please wait a moment."
                        else -> 
                            "❌ AI service error: ${error.message}. Please try again."
                    }
                    
                    _uiState.update { state ->
                        state.copy(
                            error = errorMessage,
                            currentQuery = "",
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Avatar: Exception during query")
                _uiState.update { state ->
                    state.copy(
                        error = "Failed to process query: ${e.message}",
                        currentQuery = "",
                        avatarState = AvatarState.IDLE
                    )
                }
            }
        }
    }
    
    /**
     * Speak text using Edge TTS
     */
    private fun speakText(text: String) {
        viewModelScope.launch {
            try {
                Timber.d("Avatar: Speaking text")
                edgeTTSService.speak(text)
            } catch (e: Exception) {
                Timber.e(e, "Avatar: TTS failed")
            }
        }
    }
    
    /**
     * Starts voice input mode.
     */
    fun startVoiceInput() {
        viewModelScope.launch {
            avatarUseCase.updateAvatarState(AvatarState.LISTENING)
            _uiState.update { it.copy(avatarState = AvatarState.LISTENING) }
            
            // Voice input would be handled by platform speech recognition
            // For now, show a message that voice is not yet implemented
            kotlinx.coroutines.delay(2000)
            
            _uiState.update { state ->
                state.copy(
                    error = "Voice input coming soon! Please use text for now.",
                    avatarState = AvatarState.IDLE
                )
            }
        }
    }
    
    /**
     * Clears the conversation history.
     */
    fun clearHistory() {
        viewModelScope.launch {
            avatarUseCase.clearConversationHistory()
            _uiState.update { it.copy(
                conversationHistory = emptyList(),
                currentResponse = null
            )}
            showGreeting()
        }
    }
    
    /**
     * Updates the avatar state.
     */
    fun updateAvatarState(state: AvatarState) {
        viewModelScope.launch {
            avatarUseCase.updateAvatarState(state)
        }
    }
    
    /**
     * Clears any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun getDefaultSuggestions(): List<String> {
        return listOf(
            "How many steps today?",
            "How did I sleep?",
            "Show my health summary",
            "Give me health advice"
        )
    }
}

/**
 * UI state for the Avatar screen.
 */
data class AvatarUiState(
    val avatarState: AvatarState = AvatarState.IDLE,
    val currentQuery: String = "",
    val currentResponse: AvatarResponse? = null,
    val conversationHistory: List<AvatarResponse> = emptyList(),
    val suggestions: List<String> = listOf(
        "How many steps today?",
        "How did I sleep?",
        "Show my health summary"
    ),
    val error: String? = null,
    val isVoiceInputAvailable: Boolean = false,
    val isFeatureDisabled: Boolean = false
)
