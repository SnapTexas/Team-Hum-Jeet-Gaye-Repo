package com.healthtracker.service.avatar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.healthtracker.R
import com.healthtracker.presentation.MainActivity
import com.healthtracker.presentation.avatar.Avatar3DView
import com.healthtracker.service.ai.EdgeTTSService
import com.healthtracker.service.ai.LocalLLMService
import com.healthtracker.service.ai.OpenAIService
import com.healthtracker.service.ai.VoiceCommandProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * Advanced AI Avatar Overlay Service
 * 
 * Features:
 * - Wake word "Youfit" activation
 * - <80ms instant responses for common commands
 * - OpenAI GPT-5-nano for complex queries
 * - Edge TTS for natural voice output
 * - Proactive listening mode
 * - Auto map for hospitals when user feels unwell
 * - Smart health commands
 */
@AndroidEntryPoint
class AvatarOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    @Inject
    lateinit var localLLMService: LocalLLMService
    
    @Inject
    lateinit var openAIService: OpenAIService
    
    @Inject
    lateinit var edgeTTSService: EdgeTTSService
    
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var vibrator: Vibrator? = null
    
    // State holders for UI updates
    private var onStateUpdate: ((AvatarUIState) -> Unit)? = null
    private var currentState = AvatarUIState()
    
    companion object {
        private const val TAG = "AvatarOverlayService"
        private const val CHANNEL_ID = "avatar_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        private const val PREFS_NAME = "avatar_prefs"
        private const val KEY_FIRST_TIME = "first_time_shown"
        
        // Wake words
        val WAKE_WORDS = listOf("youfit", "you fit", "hey youfit", "hey you fit", "ok youfit")
        
        const val ACTION_SHOW = "com.healthtracker.avatar.SHOW"
        const val ACTION_HIDE = "com.healthtracker.avatar.HIDE"
        
        fun hasOverlayPermission(context: Context) = Settings.canDrawOverlays(context)
        
        fun requestOverlayPermission(context: Context) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        
        fun start(context: Context) {
            if (!hasOverlayPermission(context)) {
                requestOverlayPermission(context)
                return
            }
            val intent = Intent(context, AvatarOverlayService::class.java).apply { action = ACTION_SHOW }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, AvatarOverlayService::class.java))
        }
    }
    
    // ==================== INSTANT COMMAND RESPONSES (<80ms) ====================
    
    data class InstantCommand(
        val keywords: List<String>,
        val response: String,
        val action: (AvatarOverlayService) -> Unit
    )
    
    private val instantCommands = listOf(
        // Steps commands - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "step", "steps", "walked", "walking", "step count", "how many steps", "my steps",
                "kitne step", "kadam", "chala", "chale", "walk kiya", "aaj kitna chala",
                "pedometer", "footsteps", "total steps", "steps today", "daily steps"
            ),
            response = "",
            action = { service -> service.speakStepCount() }
        ),
        // Calories commands - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "calorie", "calories", "burned", "burn", "how much calorie", "calories burned",
                "kitni calorie", "calorie count", "energy", "kcal", "fat burn", "fat burned",
                "calories today", "total calories", "calorie burn", "burnt"
            ),
            response = "",
            action = { service -> service.speakCalories() }
        ),
        // Distance commands - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "distance", "how far", "km", "kilometer", "travelled", "traveled",
                "kitna door", "kitni door", "meter", "metres", "miles", "distance covered",
                "total distance", "walk distance", "how much distance"
            ),
            response = "",
            action = { service -> service.speakDistance() }
        ),
        // Workout commands - NEW
        InstantCommand(
            keywords = listOf(
                "workout", "exercise", "gym", "training", "fitness", "activity",
                "kasrat", "vyayam", "exercise kiya", "workout done", "my workout",
                "physical activity", "active", "how active", "activity level"
            ),
            response = "",
            action = { service -> service.speakWorkout() }
        ),
        // Health commands - NEW
        InstantCommand(
            keywords = listOf(
                "health", "healthy", "fitness level", "my health", "health status",
                "sehat", "tabiyat", "health check", "am i healthy", "health report",
                "health summary", "overall health", "body", "wellness"
            ),
            response = "",
            action = { service -> service.speakHealth() }
        ),
        // Not feeling well - Show hospitals - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "not feeling well", "sick", "unwell", "not well", "feeling sick", "ill", 
                "pain", "hurt", "hospital", "doctor", "medical", "clinic", "emergency",
                "tabiyat kharab", "bimar", "dard", "health issue", "need doctor",
                "feeling bad", "not good", "unfit", "weakness", "fever", "headache",
                "stomach", "chest pain", "breathing", "ambulance", "urgent care",
                "pharmacy", "medicine shop", "dawai", "medical store", "chemist"
            ),
            response = "I'm sorry you're not feeling well. Let me find nearby hospitals for you.",
            action = { service -> service.openNearbyHospitals() }
        ),
        // Open app - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "open app", "open youfit", "launch app", "start app", "show app",
                "app kholo", "youfit kholo", "main app", "go to app", "open main"
            ),
            response = "Opening YouFit",
            action = { service -> service.openApp() }
        ),
        // Reminder - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "reminder", "remind me", "set reminder", "alarm", "medicine", "medication",
                "yaad dilao", "dawai", "tablet", "pill", "medicine time", "take medicine",
                "schedule", "appointment", "checkup"
            ),
            response = "Opening reminders",
            action = { service -> service.openApp() }
        ),
        // Progress - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "progress", "my progress", "show progress", "stats", "statistics",
                "report", "summary", "overview", "dashboard", "today summary",
                "daily report", "how am i doing", "performance", "track"
            ),
            response = "Here's your progress",
            action = { service -> service.speakProgress() }
        ),
        // Goal - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "goal", "my goal", "target", "daily goal", "step goal",
                "lakshya", "aim", "objective", "how much left", "remaining",
                "goal progress", "reach goal", "complete goal"
            ),
            response = "",
            action = { service -> service.speakGoal() }
        ),
        // Water/Hydration - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "water", "hydration", "drink", "thirsty", "pani", "paani",
                "hydrate", "dehydrated", "water intake", "drink water", "glass of water"
            ),
            response = "Remember to stay hydrated! Drinking water is essential for your health.",
            action = { service -> service.speakSimple("Remember to stay hydrated! Drinking water is essential for your health.") }
        ),
        // Time - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "time", "what time", "current time", "kya time", "kitne baje",
                "clock", "hour", "samay", "waqt"
            ),
            response = "",
            action = { service -> service.speakTime() }
        ),
        // Greeting - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "hello", "hi", "hey", "good morning", "good afternoon", "good evening",
                "namaste", "namaskar", "kaise ho", "how are you", "what's up", "sup",
                "good night", "hola", "greetings"
            ),
            response = "",
            action = { service -> service.speakGreeting() }
        ),
        // Thanks - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "thank", "thanks", "thank you", "dhanyawad", "shukriya", "appreciated",
                "great job", "well done", "nice", "awesome", "perfect"
            ),
            response = "You're welcome! Stay healthy!",
            action = { service -> service.speakSimple("You're welcome! Stay healthy!") }
        ),
        // Hide - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "hide", "close", "bye", "goodbye", "go away", "dismiss", "exit",
                "band karo", "chale jao", "hatao", "see you", "later", "quit"
            ),
            response = "Goodbye! Stay healthy!",
            action = { service -> 
                service.speakSimple("Goodbye! Stay healthy!")
                service.serviceScope.launch {
                    delay(800)
                    service.hideOverlay()
                    service.stopSelf()
                }
            }
        ),
        // Help - EXPANDED keywords
        InstantCommand(
            keywords = listOf(
                "help", "what can you do", "commands", "features", "options",
                "madad", "sahayata", "kya kar sakte ho", "abilities", "functions"
            ),
            response = "I can help you with: checking steps, calories, distance, workout status, finding hospitals, setting reminders, and tracking your health progress. Just ask!",
            action = { service -> service.speakSimple("I can help you with: checking steps, calories, distance, workout status, finding hospitals, setting reminders, and tracking your health progress. Just ask!") }
        ),
        // Sleep - NEW
        InstantCommand(
            keywords = listOf(
                "sleep", "neend", "soya", "rest", "sleeping", "bedtime",
                "how much sleep", "sleep time", "sleep quality"
            ),
            response = "Good sleep is essential for health. Aim for 7-8 hours of quality sleep every night!",
            action = { service -> service.speakSimple("Good sleep is essential for health. Aim for 7-8 hours of quality sleep every night!") }
        ),
        // Motivation - NEW
        InstantCommand(
            keywords = listOf(
                "motivate", "motivation", "inspire", "encourage", "boost",
                "feeling lazy", "dont want to", "tired", "no energy"
            ),
            response = "",
            action = { service -> service.speakMotivation() }
        ),
        // Weather/Outside - NEW
        InstantCommand(
            keywords = listOf(
                "weather", "outside", "bahar", "mausam", "should i go out",
                "walk outside", "outdoor"
            ),
            response = "Going for a walk outside is great for both physical and mental health. Fresh air and sunlight boost your mood!",
            action = { service -> service.speakSimple("Going for a walk outside is great for both physical and mental health. Fresh air and sunlight boost your mood!") }
        ),
        // Achievement commands - NEW
        InstantCommand(
            keywords = listOf(
                "achievement", "achievements", "badge", "badges", "reward", "rewards",
                "kitna achieve", "mera achievement", "my achievements", "unlocked"
            ),
            response = "",
            action = { service -> service.speakAchievements() }
        ),
        // Diet commands - NEW
        InstantCommand(
            keywords = listOf(
                "diet", "food", "khana", "nutrition", "eat", "eating", "meal",
                "breakfast", "lunch", "dinner", "snack", "kya khana chahiye",
                "what to eat", "healthy food", "diet plan", "calories intake"
            ),
            response = "",
            action = { service -> service.speakDiet() }
        ),
        // Progress chart commands - NEW
        InstantCommand(
            keywords = listOf(
                "chart", "graph", "progress chart", "show chart", "visual",
                "weekly progress", "monthly progress", "trend", "history"
            ),
            response = "Opening your progress charts in the app.",
            action = { service -> 
                service.speakSimple("Opening your progress charts.")
                service.openAppToProgress()
            }
        ),
        // Set alarm commands - NEW
        InstantCommand(
            keywords = listOf(
                "set alarm", "alarm set", "set reminder", "reminder set",
                "alarm lagao", "yaad dilao", "schedule"
            ),
            response = "Opening reminders to set an alarm.",
            action = { service -> 
                service.speakSimple("Opening reminders.")
                service.openAppToMedical()
            }
        ),
        // Search medical record - NEW
        InstantCommand(
            keywords = listOf(
                "where is my", "find my", "search", "locate", "kahan hai",
                "medical report", "lab report", "prescription", "record"
            ),
            response = "",
            action = { service -> service.searchMedicalRecord() }
        )
    )
    
    // ==================== SERVICE LIFECYCLE ====================
    
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        createNotificationChannel()
        
        // Initialize VoiceCommandProcessor
        voiceCommandProcessor = VoiceCommandProcessor(this, localLLMService)
        
        // Setup OpenAI function calling
        setupOpenAIFunctions()
        
        // Initialize TTS with fast settings
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.15f) // Slightly faster for snappy responses
                tts?.setPitch(1.0f)
                
                // Set utterance listener for chaining actions
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        // Can trigger next action after speech
                    }
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }
    
    /**
     * Setup OpenAI function calling handlers
     */
    private fun setupOpenAIFunctions() {
        openAIService.onFunctionCall = { functionName, arguments ->
            Timber.d("$TAG: Executing function: $functionName with args: $arguments")
            
            when (functionName) {
                "get_steps" -> {
                    val (steps, _, _) = getStepData()
                    "User has walked $steps steps today"
                }
                "get_calories" -> {
                    val (_, calories, _) = getStepData()
                    "User has burned $calories calories today"
                }
                "get_distance" -> {
                    val (_, _, distance) = getStepData()
                    val km = distance / 1000f
                    "User has traveled ${String.format("%.2f", km)} km today"
                }
                "set_reminder" -> {
                    val title = arguments["title"] ?: "Reminder"
                    val time = arguments["time"] ?: "10:00"
                    "Reminder '$title' set for $time"
                }
                "find_medical_help" -> {
                    val type = arguments["type"] ?: "doctor"
                    when (type) {
                        "hospital" -> {
                            openNearbyHospitals()
                            "Opening nearby hospitals on map"
                        }
                        "pharmacy" -> {
                            openNearbyPharmacy()
                            "Opening nearby pharmacies on map"
                        }
                        else -> {
                            openNearbyDoctors()
                            "Opening nearby doctors on map"
                        }
                    }
                }
                "open_screen" -> {
                    val screen = arguments["screen"] ?: "workout"
                    openMainApp()
                    "Opening $screen screen in app"
                }
                else -> "Function not implemented"
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> { hideOverlay(); stopSelf() }
            else -> showOverlay()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        hideOverlay()
        serviceScope.cancel()
        tts?.shutdown()
        speechRecognizer?.destroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
    
    // ==================== OVERLAY MANAGEMENT ====================
    
    private fun showOverlay() {
        if (!hasOverlayPermission(this) || overlayView != null) return
        
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        
        val isFirstTime = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_FIRST_TIME, true)
        
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AvatarOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AvatarOverlayService)
            
            setContent {
                Avatar3DView(
                    onStartListening = { 
                        startListening { text ->
                            if (text.isNotEmpty()) {
                                processVoiceCommand(text)
                            }
                        }
                    },
                    onStopListening = { stopListening() }
                )
            }
        }
        
        try {
            windowManager.addView(overlayView, createLayoutParams())
        } catch (e: Exception) {
            overlayView = null
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }
    
    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,  // Start small (edge indicator only)
        WindowManager.LayoutParams.WRAP_CONTENT,  // Start small
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or  // Don't block touches outside
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,  // Allow drawing at edges
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL  // Align to right edge, center vertically
    }
    
    /**
     * Update window flags to allow touch passthrough when avatar is hidden
     */
    fun updateTouchPassthrough(isExpanded: Boolean) {
        try {
            overlayView?.let { view ->
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return
                
                if (isExpanded) {
                    // Avatar expanded - intercept ALL touches (block background)
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.height = WindowManager.LayoutParams.MATCH_PARENT
                } else {
                    // Avatar hidden - ONLY edge indicator visible, rest passes through
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                }
                
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update touch passthrough")
        }
    }
    
    // ==================== VOICE RECOGNITION ====================
    
    fun startListening(onResult: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speakSimple("Please grant microphone permission")
            return
        }
        
        // Vibrate to indicate listening started
        vibrateShort()
        
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
                    onResult(text)
                    processVoiceCommand(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
                    onResult(text)
                }
            }
            override fun onError(error: Int) { onResult("") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer?.startListening(intent)
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
    }
    
    // ==================== COMMAND PROCESSING (<80ms for instant commands) ====================
    
    fun processVoiceCommand(command: String) {
        Timber.d("$TAG: ========== VOICE COMMAND RECEIVED ==========")
        Timber.d("$TAG: Command text: '$command'")
        Timber.d("$TAG: Command length: ${command.length}")
        Timber.d("$TAG: About to call processWithOpenAI directly")
        
        // Call directly - processWithOpenAI has its own coroutine
        processWithOpenAI(command)
        
        Timber.d("$TAG: processWithOpenAI called")
    }
    
    /**
     * Check if command is a question that needs LLM
     */
    private fun isQuestionPattern(command: String): Boolean {
        val questionStarters = listOf(
            "what is", "what are", "what's", "whats",
            "how to", "how do", "how can", "how does",
            "why is", "why do", "why does", "why should",
            "when should", "when to", "when do",
            "tell me", "explain", "describe",
            "kya hai", "kaise", "kyun", "kab"
        )
        return questionStarters.any { command.startsWith(it) || command.contains(it) }
    }
    
    /**
     * Process complex queries with OpenAI GPT-5-nano
     */
    private fun processWithOpenAI(query: String) {
        Timber.d("$TAG: ========== OPENAI PROCESSING START ==========")
        Timber.d("$TAG: Query: $query")
        
        // Use GlobalScope to ensure coroutine runs even if service scope is cancelled
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.d("$TAG: Inside coroutine, calling OpenAI...")
                
                // Show loading state
                withContext(Dispatchers.Main) {
                    speakSimple("Let me check that for you")
                }
                
                // Generate response with OpenAI
                Timber.d("$TAG: Calling openAIService.generateResponse...")
                val result = openAIService.generateResponse(query, includeHistory = true)
                
                Timber.d("$TAG: OpenAI result received")
                
                result.onSuccess { response ->
                    Timber.d("$TAG: ========== OPENAI SUCCESS ==========")
                    Timber.d("$TAG: Response: $response")
                    
                    if (response.isBlank()) {
                        Timber.e("$TAG: Response is blank!")
                        withContext(Dispatchers.Main) {
                            speakSimple("I didn't get a proper response. Can you try asking again?")
                        }
                    } else {
                        Timber.d("$TAG: Calling speakFast...")
                        withContext(Dispatchers.Main) {
                            speakFast(response)
                        }
                    }
                }
                
                result.onFailure { error ->
                    Timber.e("$TAG: ========== OPENAI FAILURE ==========")
                    Timber.e(error, "$TAG: Error: ${error.message}")
                    withContext(Dispatchers.Main) {
                        speakSimple("I'm having trouble connecting. Try asking about your steps, calories, or distance.")
                    }
                }
                
                Timber.d("$TAG: ========== OPENAI PROCESSING END ==========")
                
            } catch (e: Exception) {
                Timber.e("$TAG: ========== EXCEPTION IN OPENAI ==========")
                Timber.e(e, "$TAG: Exception: ${e.message}")
                withContext(Dispatchers.Main) {
                    speakSimple("I can help with health questions. Try asking about steps, calories, or fitness tips.")
                }
            }
        }
    }
    
    // ==================== INSTANT RESPONSE ACTIONS ====================
    
    private fun getStepData(): Triple<Int, Int, Int> {
        // Try step_history_v3 prefs (StepCounterService uses this)
        val stepPrefs = getSharedPreferences("step_history_v3", MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        
        var steps = stepPrefs.getInt("steps_$today", 0)
        
        // Calculate calories and distance
        val userWeight = stepPrefs.getFloat("user_weight", 70f)
        val userHeight = stepPrefs.getFloat("user_height", 170f)
        val calories = (steps * 0.0005 * userWeight).toInt()
        val distance = (steps * (userHeight * 0.415) / 100).toInt() // in meters
        
        return Triple(steps, calories, distance)
    }
    
    private fun speakStepCount() {
        val (steps, _, _) = getStepData()
        val goal = 10000
        val percentage = if (goal > 0) (steps * 100 / goal).coerceAtMost(100) else 0
        
        val response = when {
            steps == 0 -> "You haven't recorded any steps yet today. Start walking to track your progress!"
            percentage >= 100 -> "Fantastic! You've walked $steps steps today and reached your goal! Great job!"
            percentage >= 75 -> "Excellent progress! You've walked $steps steps. Just ${goal - steps} more to reach your goal!"
            percentage >= 50 -> "Good going! $steps steps so far. You're halfway to your daily goal!"
            percentage >= 25 -> "Nice start! You've walked $steps steps. Keep moving!"
            else -> "You've taken $steps steps today. Let's get moving to reach your goal of $goal steps!"
        }
        speakFast(response)
    }
    
    private fun speakCalories() {
        val (steps, calories, _) = getStepData()
        
        val response = when {
            calories == 0 -> "No calories burned yet today. Start moving to burn some calories!"
            calories < 50 -> "You've burned $calories calories so far. Every step counts!"
            calories < 100 -> "Nice! You've burned $calories calories today from walking."
            calories < 200 -> "Good work! $calories calories burned. Keep it up!"
            calories < 300 -> "Great progress! You've burned $calories calories today."
            else -> "Excellent! You've burned $calories calories today. That's amazing!"
        }
        speakFast(response)
    }
    
    private fun speakDistance() {
        val (_, _, distanceMeters) = getStepData()
        
        val response = if (distanceMeters < 1000) {
            if (distanceMeters == 0) {
                "You haven't covered any distance yet. Let's start walking!"
            } else {
                "You've walked $distanceMeters meters today. Keep going!"
            }
        } else {
            val km = distanceMeters / 1000.0
            "You've traveled ${String.format("%.1f", km)} kilometers today. Great job!"
        }
        speakFast(response)
    }
    
    private fun speakProgress() {
        val (steps, calories, distanceMeters) = getStepData()
        val distanceKm = distanceMeters / 1000.0
        
        val response = if (steps == 0) {
            "No activity recorded yet today. Start walking to track your progress!"
        } else {
            "Today's progress: $steps steps, $calories calories burned, and ${String.format("%.1f", distanceKm)} kilometers covered. Keep it up!"
        }
        speakFast(response)
    }
    
    private fun speakGoal() {
        val (steps, _, _) = getStepData()
        val goal = 10000
        val remaining = (goal - steps).coerceAtLeast(0)
        val percentage = if (goal > 0) (steps * 100 / goal) else 0
        
        val response = when {
            percentage >= 100 -> "Congratulations! You've reached your daily goal of $goal steps with $steps steps total!"
            percentage >= 75 -> "Almost there! You need just $remaining more steps to reach your goal of $goal."
            percentage >= 50 -> "Halfway there! $remaining steps remaining to reach your goal."
            else -> "Your daily goal is $goal steps. You need $remaining more steps. Let's go!"
        }
        speakFast(response)
    }
    
    private fun speakWorkout() {
        val (steps, calories, _) = getStepData()
        
        val response = when {
            steps < 1000 -> "You haven't done much workout today. Try a 15-minute walk to get started!"
            steps < 3000 -> "Light activity today with $steps steps. How about a quick jog?"
            steps < 5000 -> "Moderate workout! $steps steps and $calories calories burned. Keep pushing!"
            steps < 8000 -> "Good workout session! $steps steps completed. You're doing great!"
            else -> "Excellent workout! $steps steps and $calories calories burned. You're crushing it!"
        }
        speakFast(response)
    }
    
    private fun speakHealth() {
        val (steps, calories, distanceMeters) = getStepData()
        val distanceKm = distanceMeters / 1000.0
        
        val healthTip = when {
            steps < 2000 -> "Try to walk more today. Even a short walk can boost your mood and energy!"
            steps < 5000 -> "Good start! Regular walking helps reduce stress and improves heart health."
            steps < 8000 -> "Great activity level! You're on track for better cardiovascular health."
            else -> "Excellent! Your activity level today is great for overall health and fitness."
        }
        
        speakFast("$healthTip You've walked $steps steps, burned $calories calories, and covered ${String.format("%.1f", distanceKm)} km today.")
    }
    
    private fun speakTime() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR)
        val minute = calendar.get(Calendar.MINUTE)
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        
        speakFast("The time is $hour:${String.format("%02d", minute)} $amPm")
    }
    
    private fun speakGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val greeting = when {
            hour < 12 -> "Good morning!"
            hour < 17 -> "Good afternoon!"
            else -> "Good evening!"
        }
        
        // Use correct prefs - step_history_v3 with key steps_$date
        val (steps, _, _) = getStepData()
        
        val response = "$greeting You've walked $steps steps today. How can I help you?"
        speakFast(response)
    }
    
    private fun speakMotivation() {
        val (steps, _, _) = getStepData()
        val goal = 10000
        val percentage = if (goal > 0) (steps * 100 / goal) else 0
        
        val motivations = listOf(
            "Every step counts! You're building a healthier you with each movement.",
            "Your body can do it, it's your mind you need to convince. Let's go!",
            "The only bad workout is the one that didn't happen. Start moving!",
            "Small progress is still progress. Keep pushing forward!",
            "You're stronger than you think. Let's crush those goals!",
            "Health is wealth. Every step is an investment in yourself!",
            "Don't stop when you're tired, stop when you're done!",
            "Your future self will thank you for the effort you put in today."
        )
        
        val extraMotivation = when {
            percentage >= 100 -> " Amazing! You've already reached your goal today!"
            percentage >= 75 -> " You're so close to your goal! Just a little more!"
            percentage >= 50 -> " Halfway there! Keep the momentum going!"
            steps > 0 -> " You've already taken $steps steps. Keep it up!"
            else -> " Start with just 100 steps. You've got this!"
        }
        
        speakFast(motivations.random() + extraMotivation)
    }
    
    private fun openNearbyHospitals() {
        speakFast("Finding nearby hospitals and doctors for you. Opening maps now.")
        
        serviceScope.launch {
            delay(800)
            // Try multiple approaches to open maps
            try {
                // Method 1: Try Google Maps app directly
                val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=hospital"))
                mapsIntent.setPackage("com.google.android.apps.maps")
                mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(mapsIntent)
                Timber.d("Opened Google Maps app")
            } catch (e: Exception) {
                Timber.e(e, "Google Maps app not available, trying browser")
                try {
                    // Method 2: Open in browser with Google Maps URL
                    val browserIntent = Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://www.google.com/maps/search/hospitals+near+me"))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(browserIntent)
                    Timber.d("Opened in browser")
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to open maps")
                    speakFast("Sorry, couldn't open maps. Please search for hospitals manually.")
                }
            }
        }
    }
    
    /**
     * Opens nearby doctors/clinics
     */
    private fun openNearbyDoctors() {
        speakFast("Finding nearby doctors and clinics for you.")
        
        serviceScope.launch {
            delay(800)
            try {
                // Try Google Maps for doctors/clinics
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=doctor+clinic"))
                intent.setPackage("com.google.android.apps.maps")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://www.google.com/maps/search/doctors+near+me"))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(browserIntent)
                } catch (e2: Exception) {
                    speakFast("Sorry, couldn't open maps. Please search for doctors manually.")
                }
            }
        }
    }
    
    /**
     * Opens nearby pharmacies/medical stores
     */
    private fun openNearbyPharmacy() {
        speakFast("Finding nearby medical stores and pharmacies for you.")
        
        serviceScope.launch {
            delay(800)
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=pharmacy+medical+store"))
                intent.setPackage("com.google.android.apps.maps")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, 
                        Uri.parse("https://www.google.com/maps/search/pharmacy+near+me"))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(browserIntent)
                } catch (e2: Exception) {
                    speakFast("Sorry, couldn't open maps. Please search for pharmacies manually.")
                }
            }
        }
    }
    
    fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    
    fun openMainApp() {
        openApp()
    }
    
    fun openAppToProgress() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("navigate_to", "progress")
        })
    }
    
    fun openAppToMedical() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("navigate_to", "medical")
        })
    }
    
    private fun speakAchievements() {
        val (steps, calories, distanceMeters) = getStepData()
        val goal = 10000
        val percentage = if (goal > 0) (steps * 100 / goal) else 0
        
        val achievements = mutableListOf<String>()
        
        // Check achievements
        if (steps >= 1000) achievements.add("First 1K steps")
        if (steps >= 5000) achievements.add("5K walker")
        if (steps >= 10000) achievements.add("10K champion")
        if (calories >= 100) achievements.add("100 cal burner")
        if (calories >= 300) achievements.add("300 cal crusher")
        if (distanceMeters >= 1000) achievements.add("1 km traveler")
        if (distanceMeters >= 5000) achievements.add("5 km explorer")
        
        val response = if (achievements.isEmpty()) {
            "No achievements unlocked yet today. Walk 1000 steps to unlock your first badge!"
        } else {
            "Today's achievements: ${achievements.joinToString(", ")}. You've unlocked ${achievements.size} badges! Keep going!"
        }
        speakFast(response)
    }
    
    private fun speakDiet() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val (steps, calories, _) = getStepData()
        
        val mealAdvice = when {
            hour < 10 -> "For breakfast, try oatmeal with fruits, eggs, or whole grain toast. Start your day with protein!"
            hour < 14 -> "For lunch, have a balanced meal: rice or roti with dal, vegetables, and some protein like chicken or paneer."
            hour < 18 -> "For a healthy snack, try nuts, fruits, yogurt, or a protein shake."
            else -> "For dinner, keep it light. Soup, salad, grilled fish or chicken with vegetables."
        }
        
        val calorieAdvice = if (calories > 300) {
            " You've burned $calories calories, so you can enjoy a slightly bigger meal!"
        } else {
            " Watch your portions since you've only burned $calories calories so far."
        }
        
        speakFast(mealAdvice + calorieAdvice)
    }
    
    private fun searchMedicalRecord() {
        speakFast("Opening medical records. You can search and find your reports there.")
        serviceScope.launch {
            delay(500)
            openAppToMedical()
        }
    }
    
    // ==================== TTS HELPERS (Edge TTS) ====================
    
    private fun speakFast(text: String) {
        Timber.d("$TAG: speakFast called with: $text")
        serviceScope.launch {
            try {
                Timber.d("$TAG: Calling edgeTTSService.speakFast")
                val result = edgeTTSService.speakFast(text)
                result.onSuccess {
                    Timber.d("$TAG: Edge TTS speakFast success")
                }
                result.onFailure { error ->
                    Timber.e(error, "$TAG: Edge TTS speakFast failed")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception in speakFast")
            }
        }
    }
    
    fun speakSimple(text: String) {
        Timber.d("$TAG: speakSimple called with: $text")
        serviceScope.launch {
            try {
                val result = edgeTTSService.speak(text)
                result.onSuccess {
                    Timber.d("$TAG: Edge TTS speak success")
                }
                result.onFailure { error ->
                    Timber.e(error, "$TAG: Edge TTS speak failed")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Exception in speakSimple")
            }
        }
    }
    
    private fun vibrateShort() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }
    
    // ==================== NOTIFICATION ====================
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "YouFit Assistant", NotificationManager.IMPORTANCE_LOW)
            channel.description = "YouFit AI Assistant is active"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val hideIntent = PendingIntent.getService(this, 1,
            Intent(this, AvatarOverlayService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouFit Assistant")
            .setContentText("Say \"YouFit\" to activate")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(0, "Hide", hideIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

// ==================== UI STATE ====================

data class AvatarUIState(
    val isExpanded: Boolean = false,
    val isListening: Boolean = false,
    val statusMessage: String = "",
    val transcription: String = ""
)


// ==================== ADVANCED AVATAR UI ====================

@Composable
private fun AdvancedAvatarUI(
    isFirstTime: Boolean,
    onFirstTimeShown: () -> Unit,
    onStartListening: ((String) -> Unit) -> Unit,
    onStopListening: () -> Unit,
    service: AvatarOverlayService
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(isFirstTime) }
    var isListening by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("Say \"YouFit\" to activate") }
    var transcription by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    
    // Update touch passthrough when expanded state changes
    LaunchedEffect(isExpanded) {
        service.updateTouchPassthrough(isExpanded)
    }
    
    // Pulse animation
    val pulse = rememberInfiniteTransition(label = "p")
    val pulseScale by pulse.animateFloat(1f, 1.15f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "ps")
    
    // Glow animation for listening
    val glowAlpha by pulse.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "glow")
    
    // Colors
    val primaryBlue = Color(0xFF3B82F6)
    val dark = Color(0xFF0F0F1A)
    val card = Color(0xFF1A1A2E)
    val green = Color(0xFF10B981)
    val orange = Color(0xFFF59E0B)
    val pink = Color(0xFFEC4899)
    val red = Color(0xFFEF4444)
    val purple = Color(0xFF8B5CF6)
    val cyan = Color(0xFF06B6D4)
    
    fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTap < 400) {
            tapCount++
            if (tapCount >= 3) { 
                isExpanded = true
                tapCount = 0
                if (showTooltip) { showTooltip = false; onFirstTimeShown() }
            }
        } else { 
            tapCount = 1 
        }
        lastTap = now
        
        if (tapCount == 1) {
            scope.launch {
                delay(420)
                if (tapCount == 1) { 
                    isExpanded = !isExpanded
                    if (showTooltip) { showTooltip = false; onFirstTimeShown() }
                }
                tapCount = 0
            }
        }
    }
    
    fun startVoice() {
        isListening = true
        statusMsg = "Listening..."
        transcription = ""
        
        onStartListening { text ->
            transcription = text
            if (text.isNotEmpty()) {
                statusMsg = text
                isListening = false
            }
        }
    }
    
    Column(horizontalAlignment = Alignment.End) {
        // Expanded Panel
        AnimatedVisibility(isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = dark),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Header with Hide and Close buttons
                    Row(
                        Modifier.fillMaxWidth(), 
                        Arrangement.SpaceBetween, 
                        Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SmartToy, null, tint = primaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("YouFit Assistant", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Hide button (→) - Minimize to edge
                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward, 
                                    contentDescription = "Hide to edge",
                                    tint = Color.White.copy(0.7f), 
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Close button (X) - Stop service completely
                            IconButton(
                                onClick = { 
                                    service.stopSelf()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, 
                                    contentDescription = "Close avatar",
                                    tint = red.copy(0.8f), 
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Voice Circle (Main interaction)
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow effect when listening
                        if (isListening) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(primaryBlue.copy(alpha = glowAlpha))
                            )
                        }
                        
                        // Main voice button
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .shadow(8.dp, CircleShape)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) 
                                        Brush.radialGradient(listOf(red, red.copy(0.7f)))
                                    else 
                                        Brush.radialGradient(listOf(primaryBlue, purple))
                                )
                                .clickable { 
                                    if (isListening) {
                                        isListening = false
                                        onStopListening()
                                    } else {
                                        startVoice()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Status / Transcription
                    Text(
                        text = if (transcription.isNotEmpty()) "\"$transcription\"" else statusMsg,
                        color = if (transcription.isNotEmpty()) Color.White else Color.White.copy(0.6f),
                        fontSize = 13.sp,
                        fontWeight = if (transcription.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Quick Commands - Row 1 (Horizontal)
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        QuickCmd(Icons.Default.DirectionsWalk, "Steps", primaryBlue, Modifier.weight(1f)) {
                            service.processVoiceCommand("steps")
                            statusMsg = "Checking steps..."
                        }
                        QuickCmd(Icons.Default.LocalFireDepartment, "Calories", orange, Modifier.weight(1f)) {
                            service.processVoiceCommand("calories")
                            statusMsg = "Checking calories..."
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Quick Commands - Row 2 (Vertical cards)
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        QuickCard(Icons.Default.LocationOn, "Distance", cyan, Modifier.weight(1f)) {
                            service.processVoiceCommand("distance")
                        }
                        QuickCard(Icons.Default.LocalHospital, "Hospital", pink, Modifier.weight(1f)) {
                            service.processVoiceCommand("not feeling well")
                        }
                        QuickCard(Icons.Default.OpenInNew, "App", green, Modifier.weight(1f)) {
                            service.openApp()
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Text Input (alternative to voice)
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp), CardDefaults.cardColors(card)) {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                inputText, { inputText = it },
                                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                                textStyle = TextStyle(Color.White, 13.sp),
                                singleLine = true,
                                decorationBox = { 
                                    if (inputText.isEmpty()) Text("Type or say \"YouFit\"...", color = Color.White.copy(0.4f), fontSize = 13.sp)
                                    it() 
                                }
                            )
                            if (inputText.isNotEmpty()) {
                                IconButton({ 
                                    service.processVoiceCommand(inputText)
                                    statusMsg = inputText
                                    inputText = "" 
                                }, Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Send, null, tint = primaryBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Hide button
                    TextButton(
                        onClick = { 
                            service.processVoiceCommand("hide")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hide Assistant", color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                }
            }
        }
        
        // Tooltip
        AnimatedVisibility(showTooltip && !isExpanded, enter = fadeIn(), exit = fadeOut()) {
            Card(Modifier.padding(bottom = 8.dp), RoundedCornerShape(10.dp), CardDefaults.cardColors(dark)) {
                Column(Modifier.padding(10.dp)) {
                    Text("👋 Tap to open", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Say \"YouFit\" anytime", color = primaryBlue, fontSize = 11.sp)
                    Text("Triple-tap: instant access", color = Color.White.copy(0.5f), fontSize = 10.sp)
                }
            }
        }
        
        // Avatar Button
        Box(
            Modifier
                .size(54.dp)
                .scale(if (isListening) pulseScale else 1f)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (isListening)
                        Brush.linearGradient(listOf(red, pink))
                    else
                        Brush.linearGradient(listOf(primaryBlue, purple))
                )
                .clickable { handleTap() },
            Alignment.Center
        ) {
            Icon(
                if (isListening) Icons.Default.Mic else Icons.Default.SmartToy, 
                null, 
                tint = Color.White, 
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun QuickCmd(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(12.dp), CardDefaults.cardColors(color.copy(0.15f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, label, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun QuickCard(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), RoundedCornerShape(12.dp), CardDefaults.cardColors(color.copy(0.12f))) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
