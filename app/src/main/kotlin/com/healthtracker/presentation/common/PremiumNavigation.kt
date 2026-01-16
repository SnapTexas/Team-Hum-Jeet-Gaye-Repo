package com.healthtracker.presentation.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthtracker.presentation.theme.*

// ============================================
// PREMIUM BOTTOM NAVIGATION
// ============================================

/**
 * Premium floating bottom navigation bar with glassmorphism.
 */
@Composable
fun PremiumBottomNavigation(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<BottomNavItem> = defaultNavItems
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Glassmorphic background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(35.dp),
                    ambientColor = ElectricBlue.copy(alpha = 0.2f),
                    spotColor = ElectricBlue.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(35.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassDark,
                            GlassDarkStrong
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassBorder,
                            GlassBorderDark
                        )
                    ),
                    shape = RoundedCornerShape(35.dp)
                )
        )
        
        // Navigation items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                PremiumNavItem(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun PremiumNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navScale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) item.activeColor else GlowWhite.copy(alpha = 0.6f),
        animationSpec = tween(300),
        label = "navColor"
    )
    
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect for selected item
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .blur(12.dp)
                        .background(item.activeColor.copy(alpha = 0.4f), CircleShape)
                )
            }
            
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
        }
        
        // Label
        Text(
            text = item.label,
            color = iconColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        
        // Active indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .background(item.activeColor, CircleShape)
            )
        }
    }
}

data class BottomNavItem(
    val icon: ImageVector,
    val label: String,
    val activeColor: Color = ElectricBlue
)

private val defaultNavItems = listOf(
    BottomNavItem(Icons.Default.Home, "Home", ElectricBlue),
    BottomNavItem(Icons.Default.Analytics, "Analytics", NeonPurple),
    BottomNavItem(Icons.Default.Restaurant, "Diet", CyberGreen),
    BottomNavItem(Icons.Default.FitnessCenter, "Workout", Color(0xFFFF6B6B)),
    BottomNavItem(Icons.Default.Person, "Profile", Color(0xFFFFB347))
)

// ============================================
// PREMIUM TAB ROW
// ============================================

/**
 * Premium glassmorphic tab row.
 */
@Composable
fun PremiumTabRow(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>,
    modifier: Modifier = Modifier,
    activeColor: Color = ElectricBlue
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassDark)
            .border(
                width = 1.dp,
                color = GlassBorderDark,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                PremiumTab(
                    text = tab,
                    isSelected = index == selectedIndex,
                    activeColor = activeColor,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PremiumTab(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(300),
        label = "tabBg"
    )
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else GlowWhite.copy(alpha = 0.7f),
        animationSpec = tween(300),
        label = "tabText"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

// ============================================
// PREMIUM FAB
// ============================================

/**
 * Premium floating action button with glow.
 */
@Composable
fun PremiumFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    color: Color = ElectricBlue
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val infiniteTransition = rememberInfiniteTransition(label = "fabGlow")
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
        modifier = modifier
            .size(64.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = color.copy(alpha = glowAlpha),
                spotColor = color.copy(alpha = glowAlpha + 0.1f)
            )
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.8f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Action",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

// ============================================
// PREMIUM APP BAR
// ============================================

/**
 * Premium glassmorphic app bar.
 */
@Composable
fun PremiumAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        BackgroundDark.copy(alpha = 0.95f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationIcon?.invoke()
                
                Column(
                    modifier = Modifier.padding(start = if (navigationIcon != null) 12.dp else 0.dp)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            color = GlowWhite.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
        }
    }
}

// ============================================
// PREMIUM ICON BUTTON
// ============================================

/**
 * Premium icon button with glow effect.
 */
@Composable
fun PremiumIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = GlowWhite,
    glowOnPress: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassDark)
            .border(
                width = 1.dp,
                color = GlassBorderDark,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ============================================
// UTILITY EASING
// ============================================

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
