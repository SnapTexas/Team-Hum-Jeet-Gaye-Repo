package com.healthtracker.presentation.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.service.ai.EdgeTTSService
import com.healthtracker.service.ai.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for AI Chat Screen
 * 
 * Handles:
 * - Chat with GPT-5-nano
 * - Voice input (Speech-to-Text)
 * - Voice output (Edge TTS)
 * - Action suggestions
 */
@HiltViewModel
class AIChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAIService: OpenAIService,
    private val edgeTTSService: EdgeTTSService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    init {
        // Add welcome message
        addMessage(
            content = "Hi! I'm YouFit AI. Ask me anything about health, fitness, nutrition, or wellness!",
            isUser = false
        )
    }
    
    /**
     * Update current message
     */
    fun updateMessage(message: String) {
        _uiState.update { it.copy(currentMessage = message) }
    }
    
    /**
     * Send message to AI
     */
    fun sendMessage() {
        val message = _uiState.value.currentMessage.trim()
        if (message.isEmpty()) return
        
        // Add user message
        addMessage(content = message, isUser = true)
        
        // Clear input
        _uiState.update { it.copy(currentMessage = "", isLoading = true) }
        
        // Get AI response
        viewModelScope.launch {
            try {
                Timber.d("Sending message to OpenAI: $message")
                
                val result = openAIService.generateResponse(message)
                
                result.onSuccess { response ->
                    Timber.d("Received response from OpenAI: $response")
                    
                    // Analyze for suggested actions
                    val action = openAIService.analyzeHealthQuery(message)
                    val actionString = if (action != OpenAIService.HealthAction.CHAT_ONLY) {
                        action.name
                    } else null
                    
                    // Add AI response
                    addMessage(
                        content = response,
                        isUser = false,
                        suggestedAction = actionString
                    )
                    
                    // Speak response
                    Timber.d("Starting TTS for response")
                    speakText(response)
                }
                
                result.onFailure { error ->
                    Timber.e(error, "OpenAI API failed")
                    
                    val errorMessage = when {
                        error.message?.contains("API key") == true -> 
                            "⚠️ OpenAI API key issue. Please check your configuration."
                        error.message?.contains("Rate limit") == true -> 
                            "⏳ Too many requests. Please wait a moment and try again."
                        error.message?.contains("network") == true || error.message?.contains("timeout") == true -> 
                            "🌐 Network error. Please check your internet connection."
                        else -> 
                            "❌ AI service error: ${error.message ?: "Unknown error"}. Please try again."
                    }
                    
                    addMessage(
                        content = errorMessage,
                        isUser = false
                    )
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to get AI response")
                addMessage(
                    content = "❌ Something went wrong. Please check your internet connection and try again.\n\nError: ${e.message}",
                    isUser = false
                )
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    /**
     * Start voice input
     */
    fun startVoiceInput() {
        // Check microphone permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            addMessage(
                content = "Please grant microphone permission to use voice input.",
                isUser = false
            )
            return
        }
        
        _uiState.update { it.copy(isListening = !it.isListening) }
        
        // TODO: Implement Speech-to-Text
        // For now, show a message
        if (_uiState.value.isListening) {
            addMessage(
                content = "Voice input is being implemented. Please type your message for now.",
                isUser = false
            )
            _uiState.update { it.copy(isListening = false) }
        }
    }
    
    /**
     * Speak text using Edge TTS
     */
    private fun speakText(text: String) {
        viewModelScope.launch {
            try {
                Timber.d("Attempting to speak: $text")
                val result = edgeTTSService.speak(text)
                
                result.onSuccess {
                    Timber.d("TTS completed successfully")
                }
                
                result.onFailure { error ->
                    Timber.e(error, "TTS failed")
                    // Don't show error to user, TTS is optional
                }
            } catch (e: Exception) {
                Timber.e(e, "TTS exception")
            }
        }
    }
    
    /**
     * Add message to chat
     */
    private fun addMessage(
        content: String,
        isUser: Boolean,
        suggestedAction: String? = null
    ) {
        val message = ChatMessage(
            content = content,
            isUser = isUser,
            timestamp = dateFormat.format(Date()),
            suggestedAction = suggestedAction
        )
        
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }
    
    /**
     * Clear chat history
     */
    fun clearChat() {
        openAIService.clearHistory()
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        content = "Chat cleared! How can I help you today?",
                        isUser = false,
                        timestamp = dateFormat.format(Date())
                    )
                )
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        edgeTTSService.cleanup()
    }
}

/**
 * UI State for Chat Screen
 */
data class AIChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentMessage: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false
)
