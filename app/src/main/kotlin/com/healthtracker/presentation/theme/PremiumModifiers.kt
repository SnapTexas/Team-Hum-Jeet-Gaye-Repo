package com.healthtracker.presentation.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ============================================
// 3D DEPTH & ELEVATION EFFECTS
// ============================================

/**
 * Premium 3D card effect with multi-layer shadows and depth.
 * Creates a floating card appearance with realistic shadows.
 */
fun Modifier.premium3DCard(
    elevation: Dp = 12.dp,
    cornerRadius: Dp = 20.dp,
    glowColor: Color = ElectricBlue,
    backgroundColor: Color = CardBackgroundDark
) = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = glowColor.copy(alpha = 0.25f),
        spotColor = glowColor.copy(alpha = 0.35f)
    )
    .shadow(
        elevation = elevation / 2,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = Color.Black.copy(alpha = 0.3f),
        spotColor = Color.Black.copy(alpha = 0.4f)
    )
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.4f),
                glowColor.copy(alpha = 0.1f),
                Color.Transparent
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Neumorphism soft UI effect for interactive elements.
 * Creates a soft, pressed-in or raised appearance.
 */
fun Modifier.neumorphicSoft(
    cornerRadius: Dp = 16.dp,
    isPressed: Boolean = false,
    lightColor: Color = Color.White.copy(alpha = 0.08f),
    darkColor: Color = Color.Black.copy(alpha = 0.25f)
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(
        if (isPressed) NeuroDark.copy(alpha = 0.8f) else NeuroDark
    )
    .then(
        if (isPressed) {
            Modifier.border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(darkColor, lightColor)
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
        } else {
            Modifier.border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(lightColor, darkColor)
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
        }
    )

/**
 * Floating effect with subtle hover animation.
 */
fun Modifier.floatingEffect(
    elevation: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(16.dp)
) = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.15f),
        spotColor = Color.Black.copy(alpha = 0.2f)
    )
    .shadow(
        elevation = elevation * 2,
        shape = shape,
        ambientColor = ElectricBlue.copy(alpha = 0.1f),
        spotColor = ElectricBlue.copy(alpha = 0.15f)
    )

// ============================================
// GLASSMORPHISM EFFECTS
// ============================================

/**
 * Premium glassmorphism with frosted glass effect.
 */
fun Modifier.premiumGlass(
    cornerRadius: Dp = 20.dp,
    backgroundColor: Color = GlassWhite,
    borderColor: Color = GlassBorder,
    blurAmount: Dp = 16.dp
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = 0.3f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

/**
 * Dark glassmorphism for dark theme.
 */
fun Modifier.darkGlass(
    cornerRadius: Dp = 20.dp,
    glowColor: Color = ElectricBlue
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassDark)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.3f),
                GlassBorderDark
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

// ============================================
// GLOW & NEON EFFECTS
// ============================================

/**
 * Animated neon glow border effect.
 */
fun Modifier.animatedNeonGlow(
    glowColor: Color = ElectricBlue,
    cornerRadius: Dp = 16.dp
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "neonGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    this
        .shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(cornerRadius),
            ambientColor = glowColor.copy(alpha = glowAlpha * 0.5f),
            spotColor = glowColor.copy(alpha = glowAlpha)
        )
        .border(
            width = 2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    glowColor.copy(alpha = glowAlpha),
                    glowColor.copy(alpha = glowAlpha * 0.5f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}

/**
 * Pulsing glow effect for important elements.
 */
fun Modifier.pulsingGlow(
    glowColor: Color = ElectricBlue,
    shape: Shape = CircleShape
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingGlow")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    this
        .scale(scale)
        .shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = glowColor.copy(alpha = alpha * 0.4f),
            spotColor = glowColor.copy(alpha = alpha * 0.6f)
        )
}

/**
 * Static neon border glow.
 */
fun Modifier.neonBorder(
    color: Color = ElectricBlue,
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 2.dp
) = this
    .shadow(
        elevation = 6.dp,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = color.copy(alpha = 0.4f),
        spotColor = color.copy(alpha = 0.5f)
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = 0.9f),
                color.copy(alpha = 0.5f)
            )
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

// ============================================
// HOLOGRAPHIC EFFECTS
// ============================================

/**
 * Animated holographic border with rainbow sweep.
 */
fun Modifier.holographicAnimated(
    cornerRadius: Dp = 16.dp
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "holo")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    this
        .graphicsLayer { rotationZ = rotation }
        .border(
            width = 2.dp,
            brush = Brush.sweepGradient(
                colors = listOf(
                    HoloStart,
                    HoloMid,
                    HoloEnd,
                    HoloStart
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
        .graphicsLayer { rotationZ = -rotation }
}

/**
 * Static holographic shimmer effect.
 */
fun Modifier.holographicShimmer(
    cornerRadius: Dp = 16.dp
) = this.border(
    width = 2.dp,
    brush = Brush.sweepGradient(
        colors = listOf(
            ElectricBlue,
            NeonPurple,
            CyberGreen,
            Color(0xFFEC4899),
            ElectricBlue
        )
    ),
    shape = RoundedCornerShape(cornerRadius)
)

// ============================================
// GRADIENT BACKGROUNDS
// ============================================

/**
 * Premium aurora gradient background.
 */
fun Modifier.auroraBackground() = this.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            AuroraStart,
            AuroraMid1,
            AuroraMid2,
            AuroraEnd
        )
    )
)

/**
 * Dark premium gradient background.
 */
fun Modifier.darkPremiumBackground() = this.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            BackgroundDark,
            BackgroundDarkSecondary,
            BackgroundDarkTertiary
        )
    )
)

/**
 * Radial glow background effect.
 */
fun Modifier.radialGlowBackground(
    glowColor: Color = ElectricBlue,
    centerOffset: Offset = Offset(0.5f, 0.3f)
) = this.drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.3f),
                glowColor.copy(alpha = 0.1f),
                Color.Transparent
            ),
            center = Offset(size.width * centerOffset.x, size.height * centerOffset.y),
            radius = size.maxDimension * 0.8f
        )
    )
}

// ============================================
// 3D TRANSFORM EFFECTS
// ============================================

/**
 * 3D perspective tilt effect.
 */
fun Modifier.perspective3D(
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    cameraDistance: Float = 12f
) = this.graphicsLayer {
    this.rotationX = rotationX
    this.rotationY = rotationY
    this.cameraDistance = cameraDistance * density
}

/**
 * Animated floating effect with subtle movement.
 */
fun Modifier.animatedFloat() = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    
    this.graphicsLayer {
        translationY = offsetY
    }
}

/**
 * Scale bounce animation on appear.
 */
fun Modifier.scaleBounce(
    initialScale: Float = 0.8f,
    targetScale: Float = 1f
) = composed {
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scaleBounce"
    )
    
    this.scale(scale)
}

// ============================================
// SHIMMER & LOADING EFFECTS
// ============================================

/**
 * Animated shimmer loading effect.
 */
fun Modifier.shimmerEffect(
    baseColor: Color = ShimmerBaseDark,
    highlightColor: Color = ShimmerHighlightDark
) = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    
    this.drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    baseColor.copy(alpha = 0f),
                    highlightColor.copy(alpha = 0.5f),
                    baseColor.copy(alpha = 0f)
                ),
                start = Offset(size.width * (shimmerOffset - 0.3f), 0f),
                end = Offset(size.width * (shimmerOffset + 0.3f), size.height)
            )
        )
    }
}

/**
 * Skeleton loading placeholder.
 */
fun Modifier.skeletonLoading(
    cornerRadius: Dp = 8.dp
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(ShimmerBaseDark)
    .shimmerEffect()

// ============================================
// BADGE RARITY EFFECTS
// ============================================

/**
 * Badge glow based on rarity.
 */
fun Modifier.badgeRarityGlow(
    rarity: BadgeRarity
): Modifier {
    val glowColor = when (rarity) {
        BadgeRarity.COMMON -> BadgeCommon
        BadgeRarity.UNCOMMON -> BadgeUncommon
        BadgeRarity.RARE -> BadgeRare
        BadgeRarity.EPIC -> BadgeEpic
        BadgeRarity.LEGENDARY -> BadgeLegendary
    }
    
    return this
        .shadow(
            elevation = when (rarity) {
                BadgeRarity.COMMON -> 4.dp
                BadgeRarity.UNCOMMON -> 6.dp
                BadgeRarity.RARE -> 8.dp
                BadgeRarity.EPIC -> 10.dp
                BadgeRarity.LEGENDARY -> 12.dp
            },
            shape = CircleShape,
            ambientColor = glowColor.copy(alpha = 0.4f),
            spotColor = glowColor.copy(alpha = 0.6f)
        )
        .border(
            width = 2.dp,
            color = glowColor,
            shape = CircleShape
        )
}

enum class BadgeRarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
}

// ============================================
// UTILITY EASING FUNCTIONS
// ============================================

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
