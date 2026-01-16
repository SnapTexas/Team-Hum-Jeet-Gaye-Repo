package com.healthtracker.presentation.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.domain.model.AvatarQueryInput
import com.healthtracker.domain.model.AvatarQueryResult
import com.healthtracker.domain.model.AvatarResponse
import com.healthtracker.domain.model.AvatarState
import com.healthtracker.domain.usecase.AvatarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for the AI Avatar screen.
 * Manages avatar state, query processing, and conversation history.
 */
@HiltViewModel
class AvatarViewModel @Inject constructor(
    private val avatarUseCase: AvatarUseCase,
    private val featureFlagManager: FeatureFlagManager
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
     * Shows initial greeting when avatar is opened.
     */
    private fun showGreeting() {
        viewModelScope.launch {
            val input = AvatarQueryInput(
                query = "hello",
                userId = userId,
                currentDate = LocalDate.now()
            )
            
            when (val result = avatarUseCase.processQuery(input)) {
                is AvatarQueryResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            currentResponse = result.response,
                            suggestions = result.response.suggestions ?: getDefaultSuggestions(),
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
                is AvatarQueryResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            error = result.message,
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Submits a text query to the avatar.
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
            
            val input = AvatarQueryInput(
                query = query,
                userId = userId,
                currentDate = LocalDate.now()
            )
            
            when (val result = avatarUseCase.processQuery(input)) {
                is AvatarQueryResult.Success -> {
                    _uiState.update { state ->
                        // Move current response to history
                        val updatedHistory = state.currentResponse?.let {
                            state.conversationHistory + it
                        } ?: state.conversationHistory
                        
                        state.copy(
                            currentResponse = result.response,
                            conversationHistory = updatedHistory.takeLast(20),
                            suggestions = result.response.suggestions ?: getDefaultSuggestions(),
                            currentQuery = "",
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
                is AvatarQueryResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            error = result.message,
                            currentQuery = "",
                            avatarState = AvatarState.IDLE
                        )
                    }
                }
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
