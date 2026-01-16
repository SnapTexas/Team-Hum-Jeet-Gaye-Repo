package com.healthtracker.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Glassmorphism effect with frosted glass appearance
fun Modifier.glassmorphic(
    blurRadius: Dp = 12.dp,
    backgroundColor: Color = Color.White.copy(alpha = 0.1f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    cornerRadius: Dp = 16.dp
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(backgroundColor)
    .border(
        width = 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(cornerRadius)
    )

// Premium gradient background
fun Modifier.premiumGradient(
    colors: List<Color> = listOf(
        Color(0xFF6B46C1), // Deep Purple
        Color(0xFF00D4FF), // Electric Blue
        Color(0xFF00BCD4)  // Cyan
    )
) = this.background(
    brush = Brush.linearGradient(colors)
)


// Neon glow effect
fun Modifier.neonGlow(
    glowColor: Color = ElectricBlue,
    blurRadius: Dp = 8.dp
) = this.border(
    width = 2.dp,
    brush = Brush.linearGradient(
        colors = listOf(
            glowColor.copy(alpha = 0.8f),
            glowColor.copy(alpha = 0.4f)
        )
    ),
    shape = RoundedCornerShape(16.dp)
)

// Neumorphism soft UI effect
fun Modifier.neumorphic(
    cornerRadius: Dp = 16.dp,
    lightColor: Color = Color.White.copy(alpha = 0.1f),
    darkColor: Color = Color.Black.copy(alpha = 0.2f)
) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color(0xFF1E1E2E))
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(lightColor, darkColor)
        ),
        shape = RoundedCornerShape(cornerRadius)
    )

// Holographic border effect
fun Modifier.holographicBorder(
    cornerRadius: Dp = 16.dp
) = this.border(
    width = 2.dp,
    brush = Brush.sweepGradient(
        colors = listOf(
            ElectricBlue,
            NeonPurple,
            CyberGreen,
            ElectricBlue
        )
    ),
    shape = RoundedCornerShape(cornerRadius)
)
