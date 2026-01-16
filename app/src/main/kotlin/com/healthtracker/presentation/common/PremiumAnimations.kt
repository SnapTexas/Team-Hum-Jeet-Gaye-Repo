package com.healthtracker.presentation.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.healthtracker.presentation.theme.*
import kotlin.random.Random

// ============================================
// SCREEN TRANSITIONS
// ============================================

/**
 * Premium fade + scale enter transition.
 */
fun premiumEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(400, easing = EaseOutCubic)
    ) + scaleIn(
        initialScale = 0.92f,
        animationSpec = tween(400, easing = EaseOutCubic)
    )
}

/**
 * Premium fade + scale exit transition.
 */
fun premiumExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(300, easing = EaseInCubic)
    ) + scaleOut(
        targetScale = 0.92f,
        animationSpec = tween(300, easing = EaseInCubic)
    )
}

/**
 * Slide + fade enter from bottom.
 */
fun slideUpEnterTransition(): EnterTransition {
    return fadeIn(
        animationSpec = tween(400, easing = EaseOutCubic)
    ) + slideInVertically(
        initialOffsetY = { it / 4 },
        animationSpec = tween(400, easing = EaseOutCubic)
    )
}

/**
 * Slide + fade exit to bottom.
 */
fun slideDownExitTransition(): ExitTransition {
    return fadeOut(
        animationSpec = tween(300, easing = EaseInCubic)
    ) + slideOutVertically(
        targetOffsetY = { it / 4 },
        animationSpec = tween(300, easing = EaseInCubic)
    )
}

// ============================================
// CARD FLIP ANIMATION
// ============================================

/**
 * 3D card flip animation state.
 */
@Composable
fun rememberCardFlipState(): CardFlipState {
    return remember { CardFlipState() }
}

class CardFlipState {
    var isFlipped by mutableStateOf(false)
        private set
    
    fun flip() {
        isFlipped = !isFlipped
    }
}

/**
 * 3D card flip container.
 */
@Composable
fun FlippableCard(
    state: CardFlipState,
    modifier: Modifier = Modifier,
    frontContent: @Composable () -> Unit,
    backContent: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (state.isFlipped) 180f else 0f,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "cardFlip"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            frontContent()
        } else {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f }
            ) {
                backContent()
            }
        }
    }
}

// ============================================
// CONFETTI ANIMATION
// ============================================

/**
 * Confetti celebration animation.
 */
@Composable
fun ConfettiAnimation(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 50
) {
    if (!isPlaying) return
    
    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                color = listOf(
                    ElectricBlue,
                    NeonPurple,
                    CyberGreen,
                    Color(0xFFFFD700),
                    Color(0xFFFF6B6B)
                ).random(),
                startX = Random.nextFloat(),
                speed = 0.5f + Random.nextFloat() * 0.5f,
                amplitude = 20f + Random.nextFloat() * 30f,
                rotation = Random.nextFloat() * 360f
            )
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        particles.forEachIndexed { index, particle ->
            val infiniteTransition = rememberInfiniteTransition(label = "confetti$index")
            
            val yOffset by infiniteTransition.animateFloat(
                initialValue = -50f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = (2000 / particle.speed).toInt(),
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Restart
                ),
                label = "y$index"
            )
            
            val xWobble by infiniteTransition.animateFloat(
                initialValue = -particle.amplitude,
                targetValue = particle.amplitude,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "x$index"
            )
            
            val rotation by infiniteTransition.animateFloat(
                initialValue = particle.rotation,
                targetValue = particle.rotation + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rot$index"
            )
            
            Box(
                modifier = Modifier
                    .offset(
                        x = (particle.startX * 400 + xWobble).dp,
                        y = yOffset.dp
                    )
                    .rotate(rotation)
                    .size(8.dp, 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(particle.color)
            )
        }
    }
}

private data class ConfettiParticle(
    val color: Color,
    val startX: Float,
    val speed: Float,
    val amplitude: Float,
    val rotation: Float
)

// ============================================
// PULSE ANIMATION
// ============================================

/**
 * Pulsing circle animation for notifications/alerts.
 */
@Composable
fun PulseAnimation(
    modifier: Modifier = Modifier,
    color: Color = ElectricBlue,
    size: Dp = 100.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha)
                .background(color.copy(alpha = 0.3f), CircleShape)
        )
        
        // Inner circle
        Box(
            modifier = Modifier
                .size(size * 0.6f)
                .background(color, CircleShape)
        )
    }
}

// ============================================
// WAVE ANIMATION
// ============================================

/**
 * Animated wave background effect.
 */
@Composable
fun WaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = ElectricBlue.copy(alpha = 0.3f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 180f,
        targetValue = 540f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        // Wave 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .offset(y = (kotlin.math.sin(Math.toRadians(offset1.toDouble())) * 20).dp)
                .blur(40.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.BottomCenter)
        )
        
        // Wave 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .offset(y = (kotlin.math.sin(Math.toRadians(offset2.toDouble())) * 15).dp)
                .blur(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.BottomCenter)
        )
    }
}

// ============================================
// BREATHING ANIMATION
// ============================================

/**
 * Breathing animation for avatar or meditation.
 */
@Composable
fun BreathingAnimation(
    modifier: Modifier = Modifier,
    color: Color = ElectricBlue,
    size: Dp = 150.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathGlow"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glow aura
        Box(
            modifier = Modifier
                .size(size * 1.3f)
                .scale(scale)
                .blur(30.dp)
                .background(
                    color.copy(alpha = glowAlpha),
                    CircleShape
                )
        )
        
        // Main content
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale * 0.95f),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

// ============================================
// STAGGERED ANIMATION
// ============================================

/**
 * Staggered list item animation.
 */
@Composable
fun StaggeredAnimatedItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 50L)
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(300, easing = EaseOutCubic)
        ) + slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = tween(300, easing = EaseOutCubic)
        ),
        modifier = modifier
    ) {
        content()
    }
}

// ============================================
// UTILITY EASING
// ============================================

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)
private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
