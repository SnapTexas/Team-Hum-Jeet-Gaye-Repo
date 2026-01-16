package com.healthtracker.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtracker.presentation.theme.*

// ============================================
// FLOATING AVATAR BUTTON
// ============================================

/**
 * Premium floating avatar button with breathing animation and glow.
 */
@Composable
fun PremiumFloatingAvatar(
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    isActive: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarBreathing")
    
    // Breathing scale animation
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    
    // Glow pulse animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // Rotation for active state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(size + 20.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer glow aura
        Box(
            modifier = Modifier
                .size(size + 16.dp)
                .scale(breathScale)
                .blur(16.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricBlue.copy(alpha = glowAlpha),
                            NeonPurple.copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Holographic ring (visible when active)
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(size + 8.dp)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                ElectricBlue,
                                NeonPurple,
                                CyberGreen,
                                ElectricBlue
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        
        // Main avatar button
        Box(
            modifier = Modifier
                .size(size)
                .scale(breathScale * 0.98f)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = ElectricBlue.copy(alpha = 0.4f),
                    spotColor = ElectricBlue.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricBlue,
                            NeonPurple.copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Avatar icon/emoji
            Text(
                text = "🤖",
                fontSize = (size.value * 0.5f).sp
            )
        }
    }
}

// ============================================
// AVATAR CHAT INTERFACE
// ============================================

/**
 * Premium glassmorphic chat interface for avatar.
 */
@Composable
fun PremiumAvatarChatInterface(
    isVisible: Boolean,
    messages: List<ChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isListening: Boolean = false
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Glassmorphic container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = ElectricBlue.copy(alpha = 0.2f),
                        spotColor = ElectricBlue.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassDarkStrong)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                GlassBorder,
                                GlassBorderDark
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                // Header
                ChatHeader(onClose = onClose)
                
                // Messages
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    messages.forEach { message ->
                        ChatBubble(message = message)
                    }
                }
                
                // Input area
                ChatInputArea(
                    inputText = inputText,
                    onInputChange = onInputChange,
                    onSend = onSend,
                    onVoiceInput = onVoiceInput,
                    isListening = isListening
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(ElectricBlue, NeonPurple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🤖", fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = "Health Assistant",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Online",
                    color = CyberGreen,
                    fontSize = 12.sp
                )
            }
        }
        
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = GlowWhite
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    ),
                    ambientColor = if (isUser) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent,
                    spotColor = if (isUser) ElectricBlue.copy(alpha = 0.3f) else Color.Transparent
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.horizontalGradient(
                            colors = listOf(ElectricBlue, ElectricBlue.copy(alpha = 0.8f))
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(GlassDark, GlassDark)
                        )
                    }
                )
                .border(
                    width = if (!isUser) 1.dp else 0.dp,
                    color = if (!isUser) GlassBorderDark else Color.Transparent,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    isListening: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Text input
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassDark)
                .border(
                    width = 1.dp,
                    color = GlassBorderDark,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(ElectricBlue),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask me anything...",
                            color = GlowWhite.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Voice input button
        VoiceInputButton(
            isListening = isListening,
            onClick = onVoiceInput
        )
        
        // Send button
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = ElectricBlue.copy(alpha = 0.3f),
                    spotColor = ElectricBlue.copy(alpha = 0.4f)
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricBlue, ElectricBlue.copy(alpha = 0.8f))
                    )
                )
                .clickable(
                    enabled = inputText.isNotBlank(),
                    onClick = onSend
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun VoiceInputButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(if (isListening) pulseScale else 1f)
            .shadow(
                elevation = if (isListening) 12.dp else 4.dp,
                shape = CircleShape,
                ambientColor = if (isListening) Color(0xFFFF6B6B).copy(alpha = 0.4f) else Color.Transparent,
                spotColor = if (isListening) Color(0xFFFF6B6B).copy(alpha = 0.5f) else Color.Transparent
            )
            .clip(CircleShape)
            .background(
                if (isListening) Color(0xFFFF6B6B) else GlassDark
            )
            .border(
                width = 1.dp,
                color = if (isListening) Color(0xFFFF6B6B) else GlassBorderDark,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop" else "Voice input",
            tint = if (isListening) Color.White else GlowWhite,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ============================================
// VOICE WAVEFORM VISUALIZATION
// ============================================

/**
 * Animated voice waveform visualization.
 */
@Composable
fun VoiceWaveform(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    color: Color = ElectricBlue
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "wave$index")
            val height by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + index * 100,
                        easing = EaseInOutCubic
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "height$index"
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (isActive) height.dp else 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

// ============================================
// TYPING INDICATOR
// ============================================

/**
 * Animated typing indicator dots.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = GlowWhite
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "dot$index")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 200,
                        easing = EaseInOutCubic
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

// ============================================
// DATA CLASSES
// ============================================

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ============================================
// UTILITY EASING
// ============================================

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
