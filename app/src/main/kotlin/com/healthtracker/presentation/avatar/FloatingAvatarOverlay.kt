package com.healthtracker.presentation.avatar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.healthtracker.R
import com.healthtracker.presentation.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

/**
 * Floating AI Avatar Overlay Component
 * 
 * Features:
 * - Peek animation from side (like peeking from window)
 * - Voice command recognition
 * - Quick action buttons (Mic, Camera, Actions)
 * - LLM-style fast responses
 * - Appears on reminders
 */
@Composable
fun FloatingAvatarOverlay(
    isEnabled: Boolean,
    onDismiss: () -> Unit,
    onSetReminder: (String) -> Unit,
    onHealthIssue: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Avatar states
    var isVisible by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var avatarMessage by remember { mutableStateOf("Hi! How can I help you?") }
    var userInput by remember { mutableStateOf("") }
    
    // Animation states
    val peekOffset by animateIntOffsetAsState(
        targetValue = if (isVisible) IntOffset(0, 0) else IntOffset(300, 0),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "peek"
    )
    
    val avatarScale by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Speech Recognition
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    // Initialize TTS
    LaunchedEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.1f) // Slightly faster
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            tts?.shutdown()
        }
    }
    
    // Double tap detector for entire screen
    if (isEnabled && !isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            isVisible = true
                            avatarMessage = "Hi! How can I help you today?"
                        }
                    )
                }
        )
    }

    // Process voice command
    fun processCommand(command: String) {
        val lowerCommand = command.lowercase()
        scope.launch {
            // Fast response simulation (<80ms feel)
            delay(50)
            
            when {
                // Reminder commands
                lowerCommand.contains("reminder") || lowerCommand.contains("remind me") -> {
                    avatarMessage = "Sure! Setting a reminder for you..."
                    tts?.speak(avatarMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(500)
                    onSetReminder(command)
                    avatarMessage = "Reminder set! I'll notify you."
                }
                
                // Health issues
                lowerCommand.contains("not feeling well") || 
                lowerCommand.contains("sick") ||
                lowerCommand.contains("pain") ||
                lowerCommand.contains("headache") ||
                lowerCommand.contains("fever") -> {
                    avatarMessage = "I'm sorry to hear that. Let me help you track this..."
                    tts?.speak(avatarMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(500)
                    onHealthIssue(command)
                    avatarMessage = "I've logged your symptoms. Would you like me to suggest nearby clinics?"
                }
                
                // Navigation commands
                lowerCommand.contains("show progress") || lowerCommand.contains("my progress") -> {
                    avatarMessage = "Opening your progress..."
                    onNavigateToScreen("progress")
                }
                
                lowerCommand.contains("diet") || lowerCommand.contains("food") -> {
                    avatarMessage = "Let's track your meal!"
                    onNavigateToScreen("diet")
                }
                
                lowerCommand.contains("medical") || lowerCommand.contains("records") -> {
                    avatarMessage = "Opening medical records..."
                    onNavigateToScreen("medical")
                }
                
                // Greetings
                lowerCommand.contains("hello") || lowerCommand.contains("hi") -> {
                    avatarMessage = "Hello! I'm your health assistant. How can I help?"
                    tts?.speak(avatarMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                }
                
                // Steps query
                lowerCommand.contains("steps") || lowerCommand.contains("walked") -> {
                    avatarMessage = "Let me check your steps..."
                    onNavigateToScreen("dashboard")
                }
                
                // Default response
                else -> {
                    avatarMessage = "I heard: \"$command\". How can I assist you with that?"
                    tts?.speak(avatarMessage, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }
    }
    
    // Start listening
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            avatarMessage = "Please grant microphone permission"
            return
        }
        
        isListening = true
        avatarMessage = "Listening..."
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    userInput = matches[0]
                    processCommand(matches[0])
                }
                isListening = false
            }
            
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    userInput = matches[0]
                }
            }
            
            override fun onError(error: Int) {
                isListening = false
                avatarMessage = "Couldn't hear you. Tap mic to try again."
            }
            
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer?.startListening(intent)
    }

    // Avatar UI
    AnimatedVisibility(
        visible = isVisible && isEnabled,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Message bubble (when expanded)
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Avatar message
                            Text(
                                text = avatarMessage,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            
                            // User input display
                            if (userInput.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "You: $userInput",
                                    color = GlowWhite,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Quick action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionButton(
                                    icon = Icons.Default.Mic,
                                    label = "Voice",
                                    color = if (isListening) Color(0xFFFF6B6B) else ElectricBlue,
                                    isActive = isListening,
                                    onClick = { startListening() }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.CameraAlt,
                                    label = "Scan",
                                    color = CyberGreen,
                                    onClick = { onNavigateToScreen("diet") }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Alarm,
                                    label = "Remind",
                                    color = NeonPurple,
                                    onClick = { onNavigateToScreen("medical") }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // More actions row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionButton(
                                    icon = Icons.Default.DirectionsWalk,
                                    label = "Steps",
                                    color = ElectricBlue,
                                    onClick = { onNavigateToScreen("dashboard") }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Insights,
                                    label = "Progress",
                                    color = Color(0xFFF59E0B),
                                    onClick = { onNavigateToScreen("progress") }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Close,
                                    label = "Close",
                                    color = Color(0xFFFF6B6B),
                                    onClick = { 
                                        isExpanded = false
                                        isVisible = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Avatar circle (always visible when shown) - GIF Avatar
                Box(
                    modifier = Modifier
                        .size(if (isExpanded) 70.dp else 60.dp)
                        .scale(if (isListening) pulseScale else avatarScale)
                        .clip(CircleShape)
                        .background(
                            if (isListening) Color(0xFFFF6B6B)
                            else ElectricBlue
                        )
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    // GIF Avatar
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(R.raw.avatar_animation)
                            .decoderFactory(
                                if (android.os.Build.VERSION.SDK_INT >= 28) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                            .build()
                    )
                    
                    Image(
                        painter = painter,
                        contentDescription = "AI Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    
                    // Overlay icon when listening
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listening",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(color.copy(alpha = if (isActive) 0.3f else 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Reminder notification avatar popup
 * Shows when a reminder triggers
 */
@Composable
fun ReminderAvatarPopup(
    reminderTitle: String,
    reminderMessage: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    val context = LocalContext.current
    val offsetX by animateIntOffsetAsState(
        targetValue = if (isVisible) IntOffset(0, 0) else IntOffset(400, 0),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "reminderPeek"
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar GIF
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(context)
                            .data(R.raw.avatar_animation)
                            .decoderFactory(
                                if (android.os.Build.VERSION.SDK_INT >= 28) {
                                    ImageDecoderDecoder.Factory()
                                } else {
                                    GifDecoder.Factory()
                                }
                            )
                            .build()
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = "AI Avatar",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reminderTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = reminderMessage,
                            color = GlowWhite,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = GlowWhite
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Snooze", color = GlowWhite)
                    }
                }
            }
        }
    }
}
