package com.healthtracker.presentation.avatar

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.healthtracker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 3D Human-like Avatar with Smart Interactions
 * 
 * FEATURES:
 * - Hidden by default (edge indicator only)
 * - Double-tap to reveal full avatar
 * - Animated 3D-style avatar (human-like)
 * - Talking animation when speaking
 * - Listening animation with pulse
 * - Smooth slide-in/out animations
 */

enum class AvatarState {
    HIDDEN,      // Only edge indicator visible
    IDLE,        // Avatar visible but not active
    LISTENING,   // Listening to user
    THINKING,    // Processing command
    SPEAKING     // Speaking response
}

@Composable
fun Avatar3DView(
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    var avatarState by remember { mutableStateOf(AvatarState.HIDDEN) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    
    // Colors
    val primaryBlue = Color(0xFF3B82F6)
    val accentPurple = Color(0xFF8B5CF6)
    val darkBg = Color(0xFF0F0F1A)
    val cardBg = Color(0xFF1A1A2E)
    
    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    
    // Pulse for listening
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Glow for listening
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    // Breathing animation for idle
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    
    // Handle double tap
    fun handleTap() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime < 500) {
            // Double tap detected
            avatarState = when (avatarState) {
                AvatarState.HIDDEN -> AvatarState.IDLE
                else -> AvatarState.HIDDEN
            }
        }
        lastTapTime = currentTime
    }
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { handleTap() }
                )
            }
    ) {
        // Edge Indicator (always visible when hidden)
        AnimatedVisibility(
            visible = avatarState == AvatarState.HIDDEN,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            EdgeIndicator(
                onClick = { avatarState = AvatarState.IDLE }
            )
        }
        
        // Full Avatar Panel (slides in from right)
        AnimatedVisibility(
            visible = avatarState != AvatarState.HIDDEN,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Card(
                modifier = Modifier
                    .width(320.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = darkBg),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "YouFit AI",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { avatarState = AvatarState.HIDDEN },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hide",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 3D Avatar (Human-like representation)
                    Avatar3DModel(
                        state = avatarState,
                        pulseScale = if (avatarState == AvatarState.LISTENING) pulseScale else breathScale,
                        glowAlpha = glowAlpha
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Status Text
                    Text(
                        text = when (avatarState) {
                            AvatarState.IDLE -> "Double tap to activate"
                            AvatarState.LISTENING -> "Listening..."
                            AvatarState.THINKING -> "Processing..."
                            AvatarState.SPEAKING -> "Speaking..."
                            else -> ""
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Microphone Button
                        FloatingActionButton(
                            onClick = {
                                if (avatarState == AvatarState.LISTENING) {
                                    avatarState = AvatarState.IDLE
                                    onStopListening()
                                } else {
                                    avatarState = AvatarState.LISTENING
                                    onStartListening()
                                }
                            },
                            containerColor = if (avatarState == AvatarState.LISTENING) 
                                Color(0xFFEF4444) else primaryBlue,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (avatarState == AvatarState.LISTENING) 
                                    Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Quick Actions
                    QuickActions(
                        onAction = { action ->
                            avatarState = AvatarState.THINKING
                            scope.launch {
                                delay(500)
                                avatarState = AvatarState.SPEAKING
                                delay(2000)
                                avatarState = AvatarState.IDLE
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Edge Indicator - Small tab visible when avatar is hidden
 */
@Composable
private fun EdgeIndicator(
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "edge")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edgeGlow"
    )
    
    // GIF painter
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
            .width(40.dp)
            .height(120.dp)
            .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = glowAlpha),
                        Color(0xFF8B5CF6).copy(alpha = glowAlpha)
                    )
                ),
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // GIF Avatar
        Image(
            painter = painter,
            contentDescription = "AI Avatar",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * 3D Avatar Model - Human-like representation with GIF
 */
@Composable
private fun Avatar3DModel(
    state: AvatarState,
    pulseScale: Float,
    glowAlpha: Float
) {
    val context = LocalContext.current
    val primaryBlue = Color(0xFF3B82F6)
    val accentPurple = Color(0xFF8B5CF6)
    
    // GIF painter
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
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring (when listening)
        if (state == AvatarState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryBlue.copy(alpha = glowAlpha * 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        // Main avatar circle with GIF
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(if (state == AvatarState.LISTENING) pulseScale else 1f)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryBlue, accentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // GIF Avatar
            Image(
                painter = painter,
                contentDescription = "AI Avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            
            // Overlay icon when listening/thinking/speaking
            if (state != AvatarState.IDLE && state != AvatarState.HIDDEN) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            AvatarState.LISTENING -> Icons.Default.Mic
                            AvatarState.THINKING -> Icons.Default.Psychology
                            AvatarState.SPEAKING -> Icons.Default.RecordVoiceOver
                            else -> Icons.Default.SmartToy
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        // Animated particles when speaking
        if (state == AvatarState.SPEAKING) {
            SpeakingParticles()
        }
    }
}

/**
 * Animated particles when avatar is speaking
 */
@Composable
private fun SpeakingParticles() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    
    repeat(3) { index ->
        val offset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 40f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000 + index * 200, easing = EaseOut),
                repeatMode = RepeatMode.Restart
            ),
            label = "particle$index"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000 + index * 200),
                repeatMode = RepeatMode.Restart
            ),
            label = "particleAlpha$index"
        )
        
        Box(
            modifier = Modifier
                .offset(x = (offset * kotlin.math.cos(index * 120.0 * Math.PI / 180)).dp,
                       y = (offset * kotlin.math.sin(index * 120.0 * Math.PI / 180)).dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B82F6).copy(alpha = alpha))
        )
    }
}

/**
 * Quick action buttons
 */
@Composable
private fun QuickActions(
    onAction: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickActionButton(
            icon = Icons.Default.DirectionsWalk,
            label = "Steps",
            color = Color(0xFF10B981),
            onClick = { onAction("steps") }
        )
        QuickActionButton(
            icon = Icons.Default.LocalFireDepartment,
            label = "Calories",
            color = Color(0xFFEF4444),
            onClick = { onAction("calories") }
        )
        QuickActionButton(
            icon = Icons.Default.Favorite,
            label = "Health",
            color = Color(0xFFEC4899),
            onClick = { onAction("health") }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}
