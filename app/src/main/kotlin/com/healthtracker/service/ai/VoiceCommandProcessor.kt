package com.healthtracker.service.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice Command Processor using Local LLM (Qwen 500M)
 * 
 * Analyzes user voice input and converts it to actionable commands.
 * Uses a small, fast model for quick response (<500ms).
 * 
 * FEATURES:
 * - Fast pattern matching for instant commands (<50ms)
 * - LLM-based understanding for complex queries
 * - Supports Hindi/Hinglish commands
 * - Token-limited for fast response
 */
@Singleton
class VoiceCommandProcessor @Inject constructor(
    private val context: Context,
    private val localLLMService: LocalLLMService
) {
    
    /**
     * Supported command types
     */
    enum class CommandType {
        // Health Data Queries
        STEPS_QUERY,
        CALORIES_QUERY,
        DISTANCE_QUERY,
        HEART_RATE_QUERY,
        SLEEP_QUERY,
        WATER_QUERY,
        WEIGHT_QUERY,
        PROGRESS_QUERY,
        
        // Navigation
        NAVIGATE_DASHBOARD,
        NAVIGATE_PROGRESS,
        NAVIGATE_PLANNING,
        NAVIGATE_SOCIAL,
        NAVIGATE_MEDICAL,
        NAVIGATE_DIET,
        NAVIGATE_GAMIFICATION,
        
        // Actions
        SET_REMINDER,
        LOG_WATER,
        LOG_MEAL,
        LOG_WORKOUT,
        START_WORKOUT,
        
        // Health Issues
        REPORT_SYMPTOM,
        FIND_DOCTOR,
        EMERGENCY,
        
        // Avatar Control
        HIDE_AVATAR,
        SHOW_AVATAR,
        
        // Unknown
        UNKNOWN
    }
    
    /**
     * Command result with action details
     */
    data class CommandResult(
        val type: CommandType,
        val confidence: Float,
        val parameters: Map<String, String> = emptyMap(),
        val naturalResponse: String
    )
    
    private var isInitialized = false
    
    /**
     * Initialize the voice command processor
     * DISABLED - No LLM needed
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Disabled - using pattern matching only, no LLM needed
        isInitialized = true
        Timber.d("VoiceCommandProcessor initialized (pattern matching only)")
    }
    
    /**
     * Process voice command and return actionable result
     */
    suspend fun processCommand(voiceInput: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val cleanInput = voiceInput.trim().lowercase()
            
            // First try fast pattern matching for instant commands
            val fastResult = tryFastMatching(cleanInput)
            if (fastResult != null && fastResult.confidence > 0.8f) {
                return@withContext fastResult
            }
            
            // Use LLM for complex understanding
            val llmResult = analyzeWithLLM(cleanInput)
            return@withContext llmResult
            
        } catch (e: Exception) {
            Timber.e(e, "Error processing voice command")
            return@withContext CommandResult(
                type = CommandType.UNKNOWN,
                confidence = 0f,
                naturalResponse = "Sorry, I couldn't understand that. Can you try again?"
            )
        }
    }
    
    /**
     * Fast pattern matching for instant commands (<50ms)
     */
    private fun tryFastMatching(input: String): CommandResult? {
        // Steps queries
        if (input.matches(Regex(".*(steps?|walk|walking|kitne kadam|kadam|chala|chalun).*"))) {
            return CommandResult(
                type = CommandType.STEPS_QUERY,
                confidence = 0.95f,
                naturalResponse = "Let me check your steps..."
            )
        }
        
        // Calories queries
        if (input.matches(Regex(".*(calorie|calories|burn|kitni calorie|calorie burn|jali).*"))) {
            return CommandResult(
                type = CommandType.CALORIES_QUERY,
                confidence = 0.95f,
                naturalResponse = "Checking your calories burned..."
            )
        }
        
        // Distance queries
        if (input.matches(Regex(".*(distance|kilometer|km|kitna chala|kitni duri|meter).*"))) {
            return CommandResult(
                type = CommandType.DISTANCE_QUERY,
                confidence = 0.95f,
                naturalResponse = "Let me check your distance..."
            )
        }
        
        // Progress queries
        if (input.matches(Regex(".*(progress|summary|today|aaj ka|mera progress|report).*"))) {
            return CommandResult(
                type = CommandType.PROGRESS_QUERY,
                confidence = 0.9f,
                naturalResponse = "Here's your progress today..."
            )
        }
        
        // Water logging
        if (input.matches(Regex(".*(water|pani|drink|drank|piya|glass).*"))) {
            return CommandResult(
                type = CommandType.LOG_WATER,
                confidence = 0.9f,
                parameters = extractWaterAmount(input),
                naturalResponse = "Logging your water intake..."
            )
        }
        
        // Navigation - Dashboard
        if (input.matches(Regex(".*(dashboard|home|main|ghar|wapas).*"))) {
            return CommandResult(
                type = CommandType.NAVIGATE_DASHBOARD,
                confidence = 0.9f,
                naturalResponse = "Opening dashboard..."
            )
        }
        
        // Navigation - Progress
        if (input.matches(Regex(".*(progress|stats|statistics|report|analysis).*"))) {
            return CommandResult(
                type = CommandType.NAVIGATE_PROGRESS,
                confidence = 0.85f,
                naturalResponse = "Opening progress screen..."
            )
        }
        
        // Health issues
        if (input.matches(Regex(".*(sick|ill|not feeling well|bimar|tabiyat|doctor|hospital|pain|dard).*"))) {
            return CommandResult(
                type = CommandType.REPORT_SYMPTOM,
                confidence = 0.9f,
                parameters = mapOf("symptom" to input),
                naturalResponse = "I'm sorry to hear that. Let me help you..."
            )
        }
        
        // Emergency
        if (input.matches(Regex(".*(emergency|urgent|help|bachao|911|ambulance).*"))) {
            return CommandResult(
                type = CommandType.EMERGENCY,
                confidence = 1.0f,
                naturalResponse = "Finding emergency services nearby..."
            )
        }
        
        // Hide avatar
        if (input.matches(Regex(".*(hide|close|dismiss|band karo|chup|bye|goodbye).*"))) {
            return CommandResult(
                type = CommandType.HIDE_AVATAR,
                confidence = 0.95f,
                naturalResponse = "Okay, I'll hide now. Double tap to call me back!"
            )
        }
        
        return null
    }
    
    /**
     * Analyze command using Local LLM for complex understanding
     * DISABLED - Using pattern matching only
     */
    private suspend fun analyzeWithLLM(input: String): CommandResult {
        // Disabled - use pattern matching only
        Timber.d("LLM analysis disabled, using pattern matching fallback")
        return CommandResult(
            type = CommandType.UNKNOWN,
            confidence = 0.3f,
            naturalResponse = "I'm not sure what you mean. Try asking about steps, calories, or health."
        )
    }
    
    /**
     * Build a focused prompt for command classification
     */
    private fun buildCommandClassificationPrompt(input: String): String {
        return """
Classify this health app voice command:
User: "$input"

Categories:
STEPS - steps/walking query
CALORIES - calories burned query
DISTANCE - distance traveled query
PROGRESS - overall progress/summary
WATER - log water intake
MEAL - log food/meal
WORKOUT - start/log workout
SYMPTOM - health issue/symptom
DOCTOR - find doctor/hospital
NAVIGATE - open screen
REMINDER - set reminder
HIDE - hide assistant
UNKNOWN - unclear

Reply with category only:""".trimIndent()
    }
    
    /**
     * Parse LLM response to extract command type
     */
    private fun parseLLMResponse(originalInput: String, llmResponse: String): CommandResult {
        val response = llmResponse.trim().uppercase()
        
        // Extract category
        val category = when {
            response.contains("STEPS") -> "STEPS"
            response.contains("CALORIES") || response.contains("CALORIE") -> "CALORIES"
            response.contains("DISTANCE") -> "DISTANCE"
            response.contains("PROGRESS") -> "PROGRESS"
            response.contains("WATER") -> "WATER"
            response.contains("MEAL") -> "MEAL"
            response.contains("WORKOUT") -> "WORKOUT"
            response.contains("SYMPTOM") -> "SYMPTOM"
            response.contains("DOCTOR") -> "DOCTOR"
            response.contains("NAVIGATE") -> "NAVIGATE"
            response.contains("REMINDER") -> "REMINDER"
            response.contains("HIDE") -> "HIDE"
            else -> "UNKNOWN"
        }
        
        val confidence = 0.7f // LLM-based confidence
        
        // Map to CommandType
        val commandType = when (category) {
            "STEPS" -> CommandType.STEPS_QUERY
            "CALORIES" -> CommandType.CALORIES_QUERY
            "DISTANCE" -> CommandType.DISTANCE_QUERY
            "PROGRESS" -> CommandType.PROGRESS_QUERY
            "WATER" -> CommandType.LOG_WATER
            "MEAL" -> CommandType.LOG_MEAL
            "WORKOUT" -> CommandType.START_WORKOUT
            "SYMPTOM" -> CommandType.REPORT_SYMPTOM
            "DOCTOR" -> CommandType.FIND_DOCTOR
            "NAVIGATE" -> determineNavigationTarget(originalInput)
            "REMINDER" -> CommandType.SET_REMINDER
            "HIDE" -> CommandType.HIDE_AVATAR
            else -> CommandType.UNKNOWN
        }
        
        return CommandResult(
            type = commandType,
            confidence = confidence,
            parameters = extractParameters(originalInput, commandType),
            naturalResponse = generateNaturalResponse(commandType, originalInput)
        )
    }
    
    /**
     * Determine navigation target from input
     */
    private fun determineNavigationTarget(input: String): CommandType {
        return when {
            input.contains("dashboard") || input.contains("home") -> CommandType.NAVIGATE_DASHBOARD
            input.contains("progress") || input.contains("stats") -> CommandType.NAVIGATE_PROGRESS
            input.contains("plan") || input.contains("workout") -> CommandType.NAVIGATE_PLANNING
            input.contains("social") || input.contains("friend") -> CommandType.NAVIGATE_SOCIAL
            input.contains("medical") || input.contains("doctor") -> CommandType.NAVIGATE_MEDICAL
            input.contains("diet") || input.contains("food") -> CommandType.NAVIGATE_DIET
            input.contains("badge") || input.contains("achievement") -> CommandType.NAVIGATE_GAMIFICATION
            else -> CommandType.NAVIGATE_DASHBOARD
        }
    }
    
    /**
     * Extract parameters from input based on command type
     */
    private fun extractParameters(input: String, type: CommandType): Map<String, String> {
        return when (type) {
            CommandType.LOG_WATER -> extractWaterAmount(input)
            CommandType.LOG_MEAL -> extractMealInfo(input)
            CommandType.SET_REMINDER -> extractReminderInfo(input)
            CommandType.REPORT_SYMPTOM -> mapOf("symptom" to input)
            else -> emptyMap()
        }
    }
    
    /**
     * Extract water amount from input
     */
    private fun extractWaterAmount(input: String): Map<String, String> {
        // Look for numbers followed by ml, liter, glass, etc.
        val amountMatch = Regex("([0-9]+)\\s*(ml|liter|litre|glass|glasses|cup|cups)?").find(input)
        val amount = amountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 250 // Default 1 glass
        val unit = amountMatch?.groupValues?.get(2) ?: "ml"
        
        // Convert to ml
        val amountInMl = when (unit.lowercase()) {
            "liter", "litre" -> amount * 1000
            "glass", "glasses" -> amount * 250
            "cup", "cups" -> amount * 200
            else -> amount
        }
        
        return mapOf("amount" to amountInMl.toString(), "unit" to "ml")
    }
    
    /**
     * Extract meal information from input
     */
    private fun extractMealInfo(input: String): Map<String, String> {
        val mealType = when {
            input.contains("breakfast") || input.contains("nashta") -> "breakfast"
            input.contains("lunch") || input.contains("dopahar") -> "lunch"
            input.contains("dinner") || input.contains("raat") -> "dinner"
            input.contains("snack") -> "snack"
            else -> "meal"
        }
        
        return mapOf("mealType" to mealType, "description" to input)
    }
    
    /**
     * Extract reminder information from input
     */
    private fun extractReminderInfo(input: String): Map<String, String> {
        // Extract time if present
        val timeMatch = Regex("([0-9]{1,2})\\s*(am|pm|o'clock|baje)?").find(input)
        val time = timeMatch?.value ?: ""
        
        return mapOf("time" to time, "message" to input)
    }
    
    /**
     * Generate natural response based on command type
     */
    private fun generateNaturalResponse(type: CommandType, input: String): String {
        return when (type) {
            CommandType.STEPS_QUERY -> "Let me check your steps for today..."
            CommandType.CALORIES_QUERY -> "Checking how many calories you've burned..."
            CommandType.DISTANCE_QUERY -> "Let me see how far you've walked..."
            CommandType.PROGRESS_QUERY -> "Here's your progress summary..."
            CommandType.LOG_WATER -> "Great! Logging your water intake..."
            CommandType.LOG_MEAL -> "Noted! Logging your meal..."
            CommandType.START_WORKOUT -> "Let's start your workout!"
            CommandType.REPORT_SYMPTOM -> "I'm sorry you're not feeling well. Let me help..."
            CommandType.FIND_DOCTOR -> "Finding nearby doctors and hospitals..."
            CommandType.SET_REMINDER -> "Setting a reminder for you..."
            CommandType.HIDE_AVATAR -> "Okay, I'll hide now. Double tap to call me back!"
            CommandType.NAVIGATE_DASHBOARD -> "Opening dashboard..."
            CommandType.NAVIGATE_PROGRESS -> "Opening progress screen..."
            CommandType.NAVIGATE_PLANNING -> "Opening planning screen..."
            CommandType.NAVIGATE_SOCIAL -> "Opening social screen..."
            CommandType.NAVIGATE_MEDICAL -> "Opening medical records..."
            CommandType.NAVIGATE_DIET -> "Opening diet tracking..."
            CommandType.NAVIGATE_GAMIFICATION -> "Opening achievements..."
            CommandType.EMERGENCY -> "Finding emergency services nearby..."
            else -> "I heard: $input. How can I help?"
        }
    }
}
