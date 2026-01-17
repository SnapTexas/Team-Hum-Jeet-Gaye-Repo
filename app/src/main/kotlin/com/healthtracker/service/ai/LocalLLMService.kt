package com.healthtracker.service.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local LLM Service using Qwen 0.5B via MediaPipe GenAI
 * 
 * Features:
 * - Instant responses for common patterns (<80ms)
 * - Local Qwen 0.5B model for complex queries via MediaPipe
 * - Minimal token usage for fast inference (<500ms)
 * - Health-focused responses
 * - Fully offline - no cloud dependency
 * - Integrated with TTS for voice responses
 */
@Singleton
class LocalLLMService @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocalLLMService"
        
        // Use Gemma 2B (smaller, faster) or Phi-2 (2.7B) for better performance
        // Qwen 0.5B GGUF is not directly supported by MediaPipe
        // We'll use Gemma 2B IT which is optimized for mobile
        private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"
        
        // System prompt for health assistant (minimal tokens)
        private const val SYSTEM_PROMPT = "You are YouFit health assistant. Give brief health advice in 1-2 sentences."
        
        // Common patterns for instant response (<80ms) - no LLM needed
        private val INSTANT_PATTERNS = mapOf(
            // What is questions
            "what is step" to "Steps measure walking. Each foot strike = 1 step. Aim for 10,000 daily.",
            "what is calorie" to "Calories = energy. Walking burns ~0.04 cal/step. 10K steps = 400 cal.",
            "what is bmi" to "BMI = weight(kg) / height(m)². Normal: 18.5-24.9.",
            "what is heart rate" to "Heart rate = beats/min. Normal rest: 60-100 BPM.",
            "what is sleep" to "Sleep repairs body. Adults need 7-9 hours nightly.",
            "what is workout" to "Workout = exercise. Aim 30 min daily activity.",
            "what is health" to "Health = physical + mental + social wellbeing.",
            "what is protein" to "Protein builds muscle. Eat eggs, chicken, dal, paneer.",
            "what is cardio" to "Cardio strengthens heart. Walk, run, cycle.",
            
            // How to questions  
            "how to lose weight" to "Eat less, move more. 10K steps + healthy food.",
            "how to gain muscle" to "Lift weights 3x/week. Eat protein. Sleep 8hrs.",
            "how to sleep better" to "Same bedtime daily. No screens before bed. Dark room.",
            "how to reduce stress" to "Deep breathing. Walk. Meditate. Rest.",
            "how to stay healthy" to "Walk daily. Eat balanced. Sleep 7-8hrs. Drink water.",
            "how to burn calories" to "Walk, run, any activity. 10K steps = 400-500 cal.",
            "how to drink more water" to "Keep bottle nearby. Set reminders. 8 glasses/day.",
            
            // Hindi/Hinglish
            "kya hai step" to "Step = kadam. Har pair ka touch = 1 step. 10,000 daily.",
            "kaise weight kam kare" to "Kam khao, zyada chalo. 10K steps + healthy food.",
            "kaise healthy rahe" to "Roz chalo. Balanced khao. 7-8 ghante so.",
            
            // Quick responses
            "hello" to "Hello! I'm YouFit. Ask about steps, calories, or health!",
            "hi" to "Hi! How can I help with your health today?",
            "help" to "I answer health questions: steps, calories, exercise, sleep, nutrition.",
            "thanks" to "You're welcome! Stay healthy!",
            "bye" to "Goodbye! Keep walking!"
        )
        
        // Keywords for smart matching when exact pattern not found
        private val KEYWORD_RESPONSES = mapOf(
            listOf("step", "walk", "kadam") to "Walking is great! Aim 10K steps daily = 400 cal burned.",
            listOf("calorie", "burn", "kcal") to "Calories = energy. Balance intake with activity.",
            listOf("weight", "fat", "mota") to "Healthy weight: balanced diet + daily walking.",
            listOf("sleep", "neend", "rest") to "Sleep 7-8 hours. Same schedule daily.",
            listOf("water", "pani", "hydrat") to "Drink 8 glasses (2L) water daily.",
            listOf("exercise", "workout", "gym") to "30 min activity daily. Mix cardio + strength.",
            listOf("muscle", "strength") to "Strength train 2-3x/week. Eat protein.",
            listOf("stress", "tension") to "Deep breathing, walking, meditation help.",
            listOf("energy", "tired") to "Sleep well, eat balanced, stay hydrated."
        )
    }
    
    // LLM state
    private var llmInference: LlmInference? = null
    private var isModelLoaded = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Model state flow
    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    
    sealed class ModelState {
        object NotLoaded : ModelState()
        data class Downloading(val progress: Int) : ModelState()
        object Loading : ModelState()
        object Ready : ModelState()
        data class Error(val message: String) : ModelState()
    }
    
    /**
     * Check if model is ready
     */
    fun isReady(): Boolean = false // Disabled - using OpenAI instead
    
    /**
     * Initialize and load the model using MediaPipe
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Disabled - using OpenAI instead
        Timber.d("$TAG: LocalLLM disabled, using OpenAI")
    }
    
    /**
     * Generate response - DISABLED, using OpenAI only
     */
    suspend fun generateResponse(
        query: String,
        maxTokens: Int = 64,
        temperature: Float = 0.7f
    ): String {
        // Disabled - always use OpenAI for consistent responses
        Timber.d("$TAG: LocalLLM disabled, query should go to OpenAI")
        return "" // Return empty to force OpenAI usage
    }
    
    /**
     * Stream response (disabled - using OpenAI)
     */
    fun generateResponseStream(query: String): Flow<String> = flow<String> {
        // Disabled - always use OpenAI for consistent responses
        Timber.d("$TAG: LocalLLM stream disabled, query should go to OpenAI")
        // Return empty to force OpenAI usage
    }.flowOn(Dispatchers.IO)
    
    private fun findInstantResponse(query: String): String? {
        for ((pattern, response) in INSTANT_PATTERNS) {
            if (query.contains(pattern)) return response
        }
        return null
    }
    
    private fun findKeywordResponse(query: String): String? {
        for ((keywords, response) in KEYWORD_RESPONSES) {
            if (keywords.any { query.contains(it) }) return response
        }
        return null
    }
    
    private fun generateSmartFallback(query: String): String {
        return when {
            query.startsWith("what") || query.contains("kya") -> 
                "Good question! Ask about steps, calories, exercise, sleep, or nutrition."
            query.startsWith("how") || query.contains("kaise") -> 
                "For health tips: walk 10K steps, drink 8 glasses water, sleep 7-8 hours."
            query.startsWith("why") -> 
                "Exercise improves mood, sleep repairs body, water keeps organs working."
            else -> 
                "I help with health questions! Ask about steps, calories, exercise, or sleep."
        }
    }
    
    private fun getModelFile(): File {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()
        return File(modelDir, MODEL_FILENAME)
    }
    
    fun cleanup() {
        llmInference = null
        isModelLoaded = false
    }
}
