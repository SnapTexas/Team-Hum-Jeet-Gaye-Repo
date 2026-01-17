package com.healthtracker.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * AI Chat Screen with GPT-4o-mini integration
 * 
 * Features:
 * - Real-time chat with AI
 * - Voice input support
 * - TTS for AI responses
 * - Health-focused conversations
 * - Action suggestions (show steps, find doctors, etc.)
 */
@Composable
fun AIChatScreen(
    viewModel: AIChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    
    val darkBg = Color(0xFF0D0D1A)
    val cardBg = Color(0xFF1A1A2E)
    val primaryBlue = Color(0xFF3B82F6)
    val accentPurple = Color(0xFF8B5CF6)
    
    Scaffold(
        topBar = {
            ChatTopBar(
                onNavigateBack = onNavigateBack,
                onClearChat = { viewModel.clearChat() }
            )
        },
        containerColor = darkBg
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatMessageBubble(message = message)
                }
                
                // Typing indicator
                if (uiState.isLoading) {
                    item {
                        TypingIndicator()
                    }
                }
            }
            
            // Input Area
            ChatInputArea(
                message = uiState.currentMessage,
                onMessageChange = { viewModel.updateMessage(it) },
                onSend = { viewModel.sendMessage() },
                onVoiceInput = { viewModel.startVoiceInput() },
                isLoading = uiState.isLoading,
                isListening = uiState.isListening
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    onNavigateBack: () -> Unit,
    onClearChat: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "YouFit AI",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Powered by GPT-4o-mini",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        actions = {
            IconButton(onClick = onClearChat) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear chat",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1A1A2E)
        )
    )
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    
    val bubbleColor = if (isUser) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF1A1A2E))
        )
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            Box(
                modifier = Modifier.background(bubbleColor)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = message.timestamp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        
        // Action buttons for AI suggestions
        if (!isUser && message.suggestedAction != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ActionButton(action = message.suggestedAction)
        }
    }
}

@Composable
private fun ActionButton(action: String) {
    val (icon, label, color) = when (action) {
        "SHOW_STEPS" -> Triple(Icons.Default.DirectionsWalk, "View Steps", Color(0xFF10B981))
        "SHOW_CALORIES" -> Triple(Icons.Default.LocalFireDepartment, "View Calories", Color(0xFFEF4444))
        "FIND_DOCTOR" -> Triple(Icons.Default.LocalHospital, "Find Doctors", Color(0xFFEC4899))
        "SHOW_WORKOUT" -> Triple(Icons.Default.FitnessCenter, "View Workouts", Color(0xFFF59E0B))
        "SHOW_DIET" -> Triple(Icons.Default.Restaurant, "View Diet", Color(0xFF8B5CF6))
        "SHOW_PROGRESS" -> Triple(Icons.Default.TrendingUp, "View Progress", Color(0xFF06B6D4))
        else -> return
    }
    
    Button(
        onClick = { /* Handle action */ },
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChatInputArea(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    isLoading: Boolean,
    isListening: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Input Button
            IconButton(
                onClick = onVoiceInput,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) Color(0xFFEF4444) else Color(0xFF3B82F6).copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Voice input",
                    tint = if (isListening) Color.White else Color(0xFF3B82F6)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text Input
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "Ask about health, fitness...",
                        color = Color.White.copy(alpha = 0.5f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Send Button
            IconButton(
                onClick = onSend,
                enabled = message.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isNotBlank() && !isLoading)
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                            )
                        else
                            Brush.horizontalGradient(
                                colors = listOf(Color.Gray, Color.Gray)
                            )
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(start = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A2E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotOffset$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offset.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6))
            )
            
            if (index < 2) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

// Data classes
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: String,
    val suggestedAction: String? = null
)
