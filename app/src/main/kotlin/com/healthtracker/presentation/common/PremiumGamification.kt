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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.rotate
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
// 3D ROTATING BADGE
// ============================================

/**
 * Premium 3D rotating badge with rarity glow.
 */
@Composable
fun Premium3DBadge(
    name: String,
    emoji: String,
    rarity: BadgeRarity,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    onClick: (() -> Unit)? = null
) {
    val rarityColor = when (rarity) {
        BadgeRarity.COMMON -> BadgeCommon
        BadgeRarity.UNCOMMON -> BadgeUncommon
        BadgeRarity.RARE -> BadgeRare
        BadgeRarity.EPIC -> BadgeEpic
        BadgeRarity.LEGENDARY -> BadgeLegendary
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "badge")
    
    // Rotation for legendary badges
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (rarity == BadgeRarity.LEGENDARY && isUnlocked) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Glow aura for unlocked badges
            if (isUnlocked) {
                Box(
                    modifier = Modifier
                        .size(size + 16.dp)
                        .blur(16.dp)
                        .background(
                            rarityColor.copy(alpha = glowAlpha * 0.5f),
                            CircleShape
                        )
                )
            }
            
            // Holographic ring for epic/legendary
            if (isUnlocked && (rarity == BadgeRarity.EPIC || rarity == BadgeRarity.LEGENDARY)) {
                Box(
                    modifier = Modifier
                        .size(size + 8.dp)
                        .rotate(rotation)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    rarityColor,
                                    rarityColor.copy(alpha = 0.5f),
                                    Color.White,
                                    rarityColor.copy(alpha = 0.5f),
                                    rarityColor
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }
            
            // Badge body
            Box(
                modifier = Modifier
                    .size(size)
                    .shadow(
                        elevation = if (isUnlocked) 8.dp else 2.dp,
                        shape = CircleShape,
                        ambientColor = if (isUnlocked) rarityColor.copy(alpha = 0.3f) else Color.Transparent,
                        spotColor = if (isUnlocked) rarityColor.copy(alpha = 0.4f) else Color.Transparent
                    )
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(
                                    rarityColor.copy(alpha = 0.3f),
                                    GlassDark
                                )
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(
                                    GlassDark,
                                    GlassDarkStrong
                                )
                            )
                        }
                    )
                    .border(
                        width = 2.dp,
                        color = if (isUnlocked) rarityColor else GlassBorderDark,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Text(
                        text = emoji,
                        fontSize = (size.value * 0.45f).sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = GlowWhite.copy(alpha = 0.3f),
                        modifier = Modifier.size(size * 0.4f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Badge name
        Text(
            text = name,
            color = if (isUnlocked) Color.White else GlowWhite.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        
        // Rarity label
        Text(
            text = rarity.name.lowercase().replaceFirstChar { it.uppercase() },
            color = if (isUnlocked) rarityColor else GlowWhite.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class BadgeRarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
}

// ============================================
// BADGE CAROUSEL
// ============================================

/**
 * Premium 3D badge carousel.
 */
@Composable
fun PremiumBadgeCarousel(
    badges: List<BadgeData>,
    modifier: Modifier = Modifier,
    onBadgeClick: ((BadgeData) -> Unit)? = null
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(badges) { index, badge ->
            StaggeredAnimatedItem(index = index) {
                Premium3DBadge(
                    name = badge.name,
                    emoji = badge.emoji,
                    rarity = badge.rarity,
                    isUnlocked = badge.isUnlocked,
                    onClick = { onBadgeClick?.invoke(badge) }
                )
            }
        }
    }
}

data class BadgeData(
    val id: String,
    val name: String,
    val emoji: String,
    val rarity: BadgeRarity,
    val isUnlocked: Boolean,
    val description: String = ""
)

// ============================================
// STREAK COUNTER
// ============================================

/**
 * Premium animated streak counter with fire effect.
 */
@Composable
fun PremiumStreakCounter(
    streakCount: Int,
    modifier: Modifier = Modifier,
    isAtRisk: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streak")
    
    // Fire flicker animation
    val fireScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fireScale"
    )
    
    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val streakColor = if (isAtRisk) Warning else Color(0xFFFF6B6B)
    
    Row(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = streakColor.copy(alpha = glowAlpha * 0.3f),
                spotColor = streakColor.copy(alpha = glowAlpha * 0.4f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        streakColor.copy(alpha = 0.2f),
                        GlassDark
                    )
                )
            )
            .border(
                width = 1.dp,
                color = streakColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fire emoji with animation
        Text(
            text = "🔥",
            fontSize = 28.sp,
            modifier = Modifier.scale(fireScale)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = "$streakCount Day Streak",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (isAtRisk) {
                Text(
                    text = "⚠️ At risk! Complete today's goal",
                    color = Warning,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Keep it going!",
                    color = GlowWhite.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ============================================
// LEADERBOARD ENTRY
// ============================================

/**
 * Premium leaderboard entry with rank effects.
 */
@Composable
fun PremiumLeaderboardEntry(
    rank: Int,
    name: String,
    score: Int,
    avatarEmoji: String,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> GlowWhite
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "leaderboard")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (rank <= 3 || isCurrentUser) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = (if (isCurrentUser) ElectricBlue else rankColor).copy(alpha = glowAlpha * 0.3f),
                        spotColor = (if (isCurrentUser) ElectricBlue else rankColor).copy(alpha = glowAlpha * 0.4f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrentUser) ElectricBlue.copy(alpha = 0.15f) else GlassDark
            )
            .border(
                width = if (rank <= 3 || isCurrentUser) 1.dp else 0.dp,
                color = if (isCurrentUser) ElectricBlue.copy(alpha = 0.5f) 
                       else if (rank <= 3) rankColor.copy(alpha = 0.5f) 
                       else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (rank <= 3) rankColor.copy(alpha = 0.2f) else GlassSurface
                ),
            contentAlignment = Alignment.Center
        ) {
            if (rank <= 3) {
                Text(
                    text = when (rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "#$rank"
                    },
                    fontSize = if (rank <= 3) 20.sp else 14.sp
                )
            } else {
                Text(
                    text = "#$rank",
                    color = GlowWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(
                    width = 2.dp,
                    color = if (isCurrentUser) ElectricBlue else GlassBorderDark,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = avatarEmoji, fontSize = 24.sp)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (isCurrentUser) ElectricBlue else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (isCurrentUser) {
                Text(
                    text = "You",
                    color = ElectricBlue.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        
        // Score
        Text(
            text = "$score pts",
            color = rankColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================
// PODIUM VISUALIZATION
// ============================================

/**
 * Premium podium for top 3 users.
 */
@Composable
fun PremiumPodium(
    first: LeaderboardUser,
    second: LeaderboardUser,
    third: LeaderboardUser,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        // Second place
        PodiumPosition(
            user = second,
            rank = 2,
            height = 120.dp,
            color = Color(0xFFC0C0C0)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // First place
        PodiumPosition(
            user = first,
            rank = 1,
            height = 160.dp,
            color = Color(0xFFFFD700)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Third place
        PodiumPosition(
            user = third,
            rank = 3,
            height = 100.dp,
            color = Color(0xFFCD7F32)
        )
    }
}

@Composable
private fun PodiumPosition(
    user: LeaderboardUser,
    rank: Int,
    height: Dp,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "podium$rank")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500 + rank * 200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = color.copy(alpha = glowAlpha),
                    spotColor = color.copy(alpha = glowAlpha + 0.1f)
                )
                .clip(CircleShape)
                .background(GlassDark)
                .border(
                    width = 3.dp,
                    color = color,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = user.avatarEmoji, fontSize = 28.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Name
        Text(
            text = user.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Podium block
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(height)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    ambientColor = color.copy(alpha = 0.3f),
                    spotColor = color.copy(alpha = 0.4f)
                )
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.4f),
                            color.copy(alpha = 0.2f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (rank) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "#$rank"
                    },
                    fontSize = 32.sp
                )
                
                Text(
                    text = "${user.score}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class LeaderboardUser(
    val id: String,
    val name: String,
    val avatarEmoji: String,
    val score: Int
)

// ============================================
// ACHIEVEMENT UNLOCK ANIMATION
// ============================================

/**
 * Achievement unlock celebration overlay.
 */
@Composable
fun AchievementUnlockOverlay(
    isVisible: Boolean,
    badge: BadgeData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && badge != null,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        badge?.let {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Confetti
                    ConfettiAnimation(isPlaying = true)
                    
                    Text(
                        text = "🎉 Achievement Unlocked! 🎉",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Premium3DBadge(
                        name = badge.name,
                        emoji = badge.emoji,
                        rarity = badge.rarity,
                        isUnlocked = true,
                        size = 120.dp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = badge.description,
                        color = GlowWhite.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    PremiumButton(
                        onClick = onDismiss,
                        text = "Awesome!",
                        color = ElectricBlue
                    )
                }
            }
        }
    }
}

// ============================================
// UTILITY EASING
// ============================================

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
