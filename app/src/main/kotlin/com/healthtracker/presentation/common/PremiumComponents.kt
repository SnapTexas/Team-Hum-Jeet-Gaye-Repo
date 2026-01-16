package com.healthtracker.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtracker.presentation.theme.*

// ============================================
// PREMIUM GLASSMORPHIC CARD
// ============================================

/**
 * Premium glassmorphic card with 3D depth and glow effects.
 */
@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = ElectricBlue,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    Card(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = glowColor.copy(alpha = 0.3f),
                spotColor = glowColor.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.5f),
                        glowColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        Column(content = content)
    }
}

// ============================================
// 3D HEALTH RING
// ============================================

/**
 * Premium 3D health ring with animated progress and glow.
 */
@Composable
fun Premium3DHealthRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
    color: Color = ElectricBlue,
    backgroundColor: Color = color.copy(alpha = 0.2f),
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (animated) tween(1500, easing = EaseOutCubic) else snap(),
        label = "ringProgress"
    )
    
    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "ringGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.toPx() - strokeWidthPx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            
            // Background ring
            drawCircle(
                color = backgroundColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            
            // Glow effect
            drawArc(
                color = color.copy(alpha = glowAlpha * 0.5f),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidthPx + 8.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Progress ring
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.8f),
                        color
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            
            // End cap glow
            if (animatedProgress > 0.01f) {
                val angle = Math.toRadians((-90 + 360 * animatedProgress).toDouble())
                val endX = center.x + radius * kotlin.math.cos(angle).toFloat()
                val endY = center.y + radius * kotlin.math.sin(angle).toFloat()
                
                drawCircle(
                    color = color.copy(alpha = glowAlpha),
                    radius = strokeWidthPx / 2 + 4.dp.toPx(),
                    center = Offset(endX, endY)
                )
            }
        }
        
        content()
    }
}

// ============================================
// PREMIUM BUTTON
// ============================================

/**
 * Premium button with glow and press animation.
 */
@Composable
fun PremiumButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    color: Color = ElectricBlue,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (enabled) 8.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (enabled) color.copy(alpha = 0.4f) else Color.Transparent,
                spotColor = if (enabled) color.copy(alpha = 0.5f) else Color.Transparent
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = if (enabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = 0.8f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Gray.copy(alpha = 0.5f),
                            Color.Gray.copy(alpha = 0.3f)
                        )
                    )
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// ============================================
// FLOATING ORB BACKGROUND
// ============================================

/**
 * Animated floating orbs for premium background effect.
 */
@Composable
fun FloatingOrbsBackground(
    modifier: Modifier = Modifier,
    orbColor1: Color = ElectricBlue,
    orbColor2: Color = NeonPurple,
    orbColor3: Color = CyberGreen
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        // Orb 1 - Top right
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(y = offset1.dp)
                .blur(80.dp)
                .background(orbColor1.copy(alpha = 0.15f), CircleShape)
                .align(Alignment.TopEnd)
        )
        
        // Orb 2 - Bottom left
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(y = offset2.dp)
                .blur(60.dp)
                .background(orbColor2.copy(alpha = 0.12f), CircleShape)
                .align(Alignment.BottomStart)
        )
        
        // Orb 3 - Center
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = offset1.dp, y = offset2.dp)
                .blur(50.dp)
                .background(orbColor3.copy(alpha = 0.1f), CircleShape)
                .align(Alignment.Center)
        )
    }
}

// ============================================
// ANIMATED METRIC VALUE
// ============================================

/**
 * Animated number counter for metric values.
 */
@Composable
fun AnimatedMetricValue(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: Int = 48,
    suffix: String = ""
) {
    var displayValue by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(value) {
        val startValue = displayValue
        val duration = 1000
        val startTime = System.currentTimeMillis()
        
        while (displayValue != value) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val easedProgress = EaseOutCubic.transform(progress)
            displayValue = (startValue + (value - startValue) * easedProgress).toInt()
            
            if (progress >= 1f) break
            kotlinx.coroutines.delay(16)
        }
        displayValue = value
    }
    
    Text(
        text = "$displayValue$suffix",
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold
    )
}

// ============================================
// PARTICLE EFFECT
// ============================================

/**
 * Celebration particle burst effect.
 */
@Composable
fun ParticleBurst(
    visible: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 20,
    colors: List<Color> = listOf(ElectricBlue, NeonPurple, CyberGreen, Color(0xFFFFD700))
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            repeat(particleCount) { index ->
                val infiniteTransition = rememberInfiniteTransition(label = "particle$index")
                val angle = (360f / particleCount) * index
                val distance by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 100f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseOutCubic),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "distance$index"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha$index"
                )
                
                val radians = Math.toRadians(angle.toDouble())
                val offsetX = (distance * kotlin.math.cos(radians)).toFloat()
                val offsetY = (distance * kotlin.math.sin(radians)).toFloat()
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = offsetX.dp, y = offsetY.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(
                            colors[index % colors.size],
                            CircleShape
                        )
                        .align(Alignment.Center)
                )
            }
        }
    }
}

// ============================================
// PREMIUM TEXT FIELD
// ============================================

/**
 * Premium glassmorphic text field.
 */
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    glowColor: Color = ElectricBlue,
    isError: Boolean = false
) {
    val focusedColor = if (isError) Error else glowColor
    
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = GlowWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = focusedColor.copy(alpha = 0.2f),
                    spotColor = focusedColor.copy(alpha = 0.3f)
                ),
            placeholder = {
                Text(
                    text = placeholder,
                    color = GlowWhite.copy(alpha = 0.5f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = GlassSurface,
                unfocusedContainerColor = GlassSurface,
                focusedBorderColor = focusedColor,
                unfocusedBorderColor = GlassBorderDark,
                cursorColor = focusedColor,
                errorBorderColor = Error
            ),
            shape = RoundedCornerShape(16.dp),
            isError = isError,
            singleLine = true
        )
    }
}

// ============================================
// SHIMMER SKELETON
// ============================================

/**
 * Shimmer loading skeleton.
 */
@Composable
fun ShimmerSkeleton(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(ShimmerBaseDark)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ShimmerBaseDark,
                            ShimmerHighlightDark,
                            ShimmerBaseDark
                        ),
                        start = Offset(size.width * shimmerOffset, 0f),
                        end = Offset(size.width * (shimmerOffset + 1f), size.height)
                    )
                )
            }
    )
}

// ============================================
// UTILITY EASING
// ============================================

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
