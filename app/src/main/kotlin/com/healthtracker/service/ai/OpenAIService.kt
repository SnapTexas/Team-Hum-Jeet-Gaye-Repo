package com.healthtracker.service.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI Service for GPT-5-nano integration
 * 
 * Features:
 * - Fast responses using GPT-5-nano (CHEAPEST model)
 * - Health-focused system prompt
 * - Streaming support for real-time responses
 * - Token optimization for cost efficiency
 * - Integrated with TTS for voice output
 */
@Singleton
class OpenAIService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "OpenAIService"
        private const val API_BASE_URL = "https://api.openai.com/v1"
        
        // Use gpt-4o-mini - Fast and affordable model
        private const val MODEL = "gpt-4o-mini"
        
        // Strict token limits for maximum cost efficiency
        private const val MAX_INPUT_TOKENS = 300  // Increased for better context
        private const val MAX_OUTPUT_TOKENS = 150  // Increased for detailed health responses
        private const val TEMPERATURE = 0.8f  // Slightly higher for more natural responses
        
        // Enhanced health-focused system prompt
        private const val SYSTEM_PROMPT = """You are YouFit Health AI - a personal health and fitness assistant integrated into a health tracking mobile app.

YOUR CORE IDENTITY:
- You are a health & fitness expert, nutritionist, and wellness coach
- You ONLY discuss health, fitness, wellness, nutrition, exercise, mental health, and medical topics
- You have access to user's real-time health data (steps, calories, heart rate, distance)
- You can control app functions to help users track and improve their health

YOUR CAPABILITIES:
1. Track & analyze: steps, calories, distance, heart rate, sleep, workouts
2. Provide: workout plans, diet advice, nutrition tips, exercise guidance
3. Medical help: symptom analysis, medication reminders, find doctors/hospitals
4. Motivation: encourage healthy habits, celebrate achievements, set goals
5. App control: open screens, set reminders, log activities

STRICT RULES:
- ONLY answer health/fitness/wellness related questions
- If user asks non-health topics (politics, news, entertainment, general knowledge), politely redirect: "I'm your health assistant. I can help with fitness, nutrition, workouts, or medical questions. What health goal can I help you with?"
- Always relate responses to user's health journey
- Use available functions when user asks about their data
- Keep responses conversational, friendly, and under 3 sentences
- Use motivational language to encourage healthy habits
- For medical emergencies, always advise consulting a doctor

RESPONSE STYLE:
- Friendly, supportive, and motivating
- Use simple language (mix of English and Hindi if needed)
- Be concise but helpful
- Celebrate user achievements
- Provide actionable health advice

Remember: You are a HEALTH assistant. Everything you say should help users become healthier and fitter!"""
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<Message>()
    private val maxHistorySize = 4 // Keep only last 4 messages (minimize input tokens)
    
    // Callback for function execution
    var onFunctionCall: ((String, Map<String, String>) -> String)? = null
    
    data class Message(
        val role: String, // "system", "user", "assistant"
        val content: String
    )
    
    /**
     * Get available functions for OpenAI function calling
     */
    private fun getAvailableFunctions(): JSONArray {
        return JSONArray().apply {
            // Get steps count
            put(JSONObject().apply {
                put("name", "get_steps")
                put("description", "Get user's step count for today")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
            
            // Get calories
            put(JSONObject().apply {
                put("name", "get_calories")
                put("description", "Get calories burned today")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
            
            // Get distance
            put(JSONObject().apply {
                put("name", "get_distance")
                put("description", "Get distance traveled today")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
            
            // Set reminder
            put(JSONObject().apply {
                put("name", "set_reminder")
                put("description", "Set a health reminder (medicine, workout, etc)")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("type", "string")
                            put("description", "Reminder title")
                        })
                        put("time", JSONObject().apply {
                            put("type", "string")
                            put("description", "Time in HH:mm format")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("title")
                        put("time")
                    })
                })
            })
            
            // Find doctor/hospital
            put(JSONObject().apply {
                put("name", "find_medical_help")
                put("description", "Find nearby doctors, hospitals, or pharmacies")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("type", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().apply {
                                put("doctor")
                                put("hospital")
                                put("pharmacy")
                            })
                            put("description", "Type of medical facility")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("type")
                    })
                })
            })
            
            // Open app screen
            put(JSONObject().apply {
                put("name", "open_screen")
                put("description", "Open a specific screen in the app")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("screen", JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray().apply {
                                put("workout")
                                put("diet")
                                put("medical")
                                put("progress")
                                put("social")
                            })
                            put("description", "Screen to open")
                        })
                    })
                    put("required", JSONArray().apply {
                        put("screen")
                    })
                })
            })
        }
    }
    
    /**
     * Get API key from BuildConfig
     */
    private fun getApiKey(): String {
        return try {
            val key = com.healthtracker.BuildConfig.OPENAI_API_KEY
            if (key.isEmpty()) {
                Timber.e("$TAG: API key is EMPTY in BuildConfig!")
            } else {
                Timber.d("$TAG: API key loaded successfully (length: ${key.length})")
            }
            key
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to get API key from BuildConfig")
            ""
        }
    }
    
    /**
     * Generate response from GPT-4o-mini
     */
    suspend fun generateResponse(
        userMessage: String,
        includeHistory: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Timber.d("$TAG: generateResponse called with: $userMessage")
            
            val apiKey = getApiKey()
            if (apiKey.isEmpty()) {
                val errorMsg = "OpenAI API key not configured. Please add OPENAI_API_KEY to local.properties"
                Timber.e("$TAG: $errorMsg")
                return@withContext Result.failure(Exception(errorMsg))
            }
            
            Timber.d("$TAG: Building request...")
            
            // Build messages array
            val messages = JSONArray()
            
            // Add system prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            
            // Add conversation history if requested (only last 4 messages to save tokens)
            if (includeHistory) {
                conversationHistory.takeLast(4).forEach { msg ->
                    messages.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }
            
            // Add current user message
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
            
            // Build request body with function calling
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("functions", getAvailableFunctions())
                put("function_call", "auto")  // Let OpenAI decide
                put("max_tokens", MAX_OUTPUT_TOKENS)
                put("temperature", TEMPERATURE)
                put("top_p", 0.9)
            }
            
            Timber.d("$TAG: Sending request to OpenAI...")
            
            val request = Request.Builder()
                .url("$API_BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            Timber.d("$TAG: Response code: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Timber.e("$TAG: OpenAI API error: ${response.code} - $errorBody")
                
                val userMessage = when (response.code) {
                    401 -> "Invalid API key. Please check your OpenAI API key."
                    429 -> "Rate limit exceeded. Please try again in a moment."
                    500, 502, 503 -> "OpenAI service is temporarily unavailable. Please try again."
                    else -> "Failed to connect to AI service (Error ${response.code})"
                }
                
                return@withContext Result.failure(Exception(userMessage))
            }
            
            val responseBody = response.body?.string() ?: ""
            Timber.d("$TAG: Response received, parsing...")
            
            val jsonResponse = JSONObject(responseBody)
            val choice = jsonResponse.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            
            // Check if OpenAI wants to call a function
            if (message.has("function_call")) {
                val functionCall = message.getJSONObject("function_call")
                val functionName = functionCall.getString("name")
                val argumentsStr = functionCall.optString("arguments", "{}")
                
                Timber.d("$TAG: Function call requested: $functionName")
                
                // Parse arguments
                val arguments = try {
                    val argsJson = JSONObject(argumentsStr)
                    val map = mutableMapOf<String, String>()
                    argsJson.keys().forEach { key ->
                        map[key] = argsJson.getString(key)
                    }
                    map
                } catch (e: Exception) {
                    emptyMap()
                }
                
                // Execute function via callback
                val functionResult = onFunctionCall?.invoke(functionName, arguments) ?: "Function not available"
                
                Timber.d("$TAG: Function result: $functionResult")
                
                // Add function result to conversation and get final response
                val messagesWithFunction = JSONArray().apply {
                    // Add all previous messages
                    for (i in 0 until messages.length()) {
                        put(messages.getJSONObject(i))
                    }
                    // Add assistant's function call
                    put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", JSONObject.NULL)
                        put("function_call", functionCall)
                    })
                    // Add function result
                    put(JSONObject().apply {
                        put("role", "function")
                        put("name", functionName)
                        put("content", functionResult)
                    })
                }
                
                // Get final response from OpenAI
                val finalRequestBody = JSONObject().apply {
                    put("model", MODEL)
                    put("messages", messagesWithFunction)
                    put("max_tokens", MAX_OUTPUT_TOKENS)
                    put("temperature", TEMPERATURE)
                }
                
                val finalRequest = Request.Builder()
                    .url("$API_BASE_URL/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(finalRequestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                val finalResponse = client.newCall(finalRequest).execute()
                
                if (!finalResponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to get final response"))
                }
                
                val finalResponseBody = finalResponse.body?.string() ?: ""
                val finalJsonResponse = JSONObject(finalResponseBody)
                
                val assistantMessage = finalJsonResponse
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
                
                Timber.d("$TAG: Final assistant message: $assistantMessage")
                
                // Update conversation history
                conversationHistory.add(Message("user", userMessage))
                conversationHistory.add(Message("assistant", assistantMessage))
                
                if (conversationHistory.size > maxHistorySize * 2) {
                    conversationHistory.removeAt(0)
                    conversationHistory.removeAt(0)
                }
                
                Timber.d("$TAG: Response generated successfully")
                return@withContext Result.success(assistantMessage)
            }
            
            // No function call - direct response
            val assistantMessage = message.getString("content").trim()
            
            Timber.d("$TAG: Assistant message: $assistantMessage")
            
            // Update conversation history
            conversationHistory.add(Message("user", userMessage))
            conversationHistory.add(Message("assistant", assistantMessage))
            
            // Keep history size manageable
            if (conversationHistory.size > maxHistorySize * 2) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }
            
            Timber.d("$TAG: Response generated successfully")
            Result.success(assistantMessage)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to generate response")
            Result.failure(e)
        }
    }
    
    /**
     * Generate streaming response for real-time output
     */
    fun generateResponseStream(userMessage: String): Flow<String> = flow {
        try {
            val apiKey = getApiKey()
            if (apiKey.isEmpty()) {
                emit("Error: API key not configured")
                return@flow
            }
            
            // Build messages
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            
            conversationHistory.takeLast(4).forEach { msg ->
                messages.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
            
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("max_tokens", MAX_OUTPUT_TOKENS)  // Strict 50 token limit
                put("temperature", TEMPERATURE)
                put("stream", true)
            }
            
            val request = Request.Builder()
                .url("$API_BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit("Error: ${response.code}")
                return@flow
            }
            
            val fullResponse = StringBuilder()
            
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") break
                        
                        try {
                            val json = JSONObject(data)
                            val delta = json
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .optJSONObject("delta")
                            
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullResponse.append(content)
                                emit(content)
                            }
                        } catch (e: Exception) {
                            // Skip malformed JSON
                        }
                    }
                }
            }
            
            // Update history
            conversationHistory.add(Message("user", userMessage))
            conversationHistory.add(Message("assistant", fullResponse.toString()))
            
            if (conversationHistory.size > maxHistorySize * 2) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Streaming failed")
            emit("Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Clear conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        Timber.d("$TAG: Conversation history cleared")
    }
    
    /**
     * Get conversation history
     */
    fun getHistory(): List<Message> = conversationHistory.toList()
    
    /**
     * Analyze health query and suggest actions
     */
    suspend fun analyzeHealthQuery(query: String): HealthAction {
        return withContext(Dispatchers.IO) {
            val lowerQuery = query.lowercase()
            
            when {
                // Steps related
                lowerQuery.contains("step") || lowerQuery.contains("walk") || 
                lowerQuery.contains("kadam") -> HealthAction.SHOW_STEPS
                
                // Calories related
                lowerQuery.contains("calorie") || lowerQuery.contains("burn") ||
                lowerQuery.contains("kcal") -> HealthAction.SHOW_CALORIES
                
                // Medical/Health issues
                lowerQuery.contains("sick") || lowerQuery.contains("pain") ||
                lowerQuery.contains("doctor") || lowerQuery.contains("hospital") ||
                lowerQuery.contains("bimar") || lowerQuery.contains("dard") -> HealthAction.FIND_DOCTOR
                
                // Exercise/Workout
                lowerQuery.contains("exercise") || lowerQuery.contains("workout") ||
                lowerQuery.contains("gym") -> HealthAction.SHOW_WORKOUT
                
                // Diet/Food
                lowerQuery.contains("food") || lowerQuery.contains("diet") ||
                lowerQuery.contains("eat") || lowerQuery.contains("khana") -> HealthAction.SHOW_DIET
                
                // Progress/Stats
                lowerQuery.contains("progress") || lowerQuery.contains("stats") ||
                lowerQuery.contains("report") -> HealthAction.SHOW_PROGRESS
                
                else -> HealthAction.CHAT_ONLY
            }
        }
    }
    
    enum class HealthAction {
        SHOW_STEPS,
        SHOW_CALORIES,
        FIND_DOCTOR,
        SHOW_WORKOUT,
        SHOW_DIET,
        SHOW_PROGRESS,
        CHAT_ONLY
    }
}
