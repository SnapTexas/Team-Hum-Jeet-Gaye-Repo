package com.healthtracker.presentation.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptive UI Quality Manager for performance optimization.
 * 
 * Detects device performance tier and adjusts visual effects accordingly:
 * - HIGH: All effects enabled (blur, particles, complex animations)
 * - MEDIUM: Reduced blur, simplified particles
 * - LOW: Minimal effects, basic animations only
 * - LITE: Battery saver mode, static UI
 */
object AdaptiveUIQuality {
    
    private val _qualityTier = MutableStateFlow(QualityTier.HIGH)
    val qualityTier: StateFlow<QualityTier> = _qualityTier.asStateFlow()
    
    private val _isLiteMode = MutableStateFlow(false)
    val isLiteMode: StateFlow<Boolean> = _isLiteMode.asStateFlow()
    
    /**
     * Initialize quality settings based on device capabilities.
     */
    fun initialize(context: Context) {
        val tier = detectDeviceTier(context)
        _qualityTier.value = tier
    }
    
    /**
     * Enable/disable lite mode for battery saving.
     */
    fun setLiteMode(enabled: Boolean) {
        _isLiteMode.value = enabled
        if (enabled) {
            _qualityTier.value = QualityTier.LITE
        } else {
            // Re-detect tier when exiting lite mode
            // This would need context, so we default to MEDIUM
            _qualityTier.value = QualityTier.MEDIUM
        }
    }
    
    /**
     * Manually set quality tier.
     */
    fun setQualityTier(tier: QualityTier) {
        if (!_isLiteMode.value) {
            _qualityTier.value = tier
        }
    }
    
    private fun detectDeviceTier(context: Context): QualityTier {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024.0)
        val isLowRamDevice = activityManager.isLowRamDevice
        
        // Check for high-end indicators
        val isHighEnd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                        totalRamGB >= 6 && 
                        !isLowRamDevice
        
        // Check for mid-range
        val isMidRange = totalRamGB >= 4 && !isLowRamDevice
        
        return when {
            isLowRamDevice -> QualityTier.LOW
            isHighEnd -> QualityTier.HIGH
            isMidRange -> QualityTier.MEDIUM
            else -> QualityTier.LOW
        }
    }
}

enum class QualityTier {
    HIGH,   // All effects enabled
    MEDIUM, // Reduced effects
    LOW,    // Minimal effects
    LITE    // Battery saver mode
}

/**
 * Quality-aware settings for UI components.
 */
data class QualitySettings(
    val enableBlur: Boolean,
    val enableParticles: Boolean,
    val enableComplexAnimations: Boolean,
    val enableGlowEffects: Boolean,
    val enableShadows: Boolean,
    val animationDurationMultiplier: Float,
    val blurRadius: Int,
    val particleCount: Int
) {
    companion object {
        fun forTier(tier: QualityTier): QualitySettings {
            return when (tier) {
                QualityTier.HIGH -> QualitySettings(
                    enableBlur = true,
                    enableParticles = true,
                    enableComplexAnimations = true,
                    enableGlowEffects = true,
                    enableShadows = true,
                    animationDurationMultiplier = 1f,
                    blurRadius = 16,
                    particleCount = 50
                )
                QualityTier.MEDIUM -> QualitySettings(
                    enableBlur = true,
                    enableParticles = true,
                    enableComplexAnimations = true,
                    enableGlowEffects = true,
                    enableShadows = true,
                    animationDurationMultiplier = 0.8f,
                    blurRadius = 8,
                    particleCount = 25
                )
                QualityTier.LOW -> QualitySettings(
                    enableBlur = false,
                    enableParticles = false,
                    enableComplexAnimations = false,
                    enableGlowEffects = true,
                    enableShadows = true,
                    animationDurationMultiplier = 0.5f,
                    blurRadius = 0,
                    particleCount = 0
                )
                QualityTier.LITE -> QualitySettings(
                    enableBlur = false,
                    enableParticles = false,
                    enableComplexAnimations = false,
                    enableGlowEffects = false,
                    enableShadows = false,
                    animationDurationMultiplier = 0f,
                    blurRadius = 0,
                    particleCount = 0
                )
            }
        }
    }
}

/**
 * Composable to access current quality settings.
 */
@Composable
fun rememberQualitySettings(): QualitySettings {
    val tier by AdaptiveUIQuality.qualityTier.collectAsState()
    return remember(tier) { QualitySettings.forTier(tier) }
}

/**
 * Composable to check if specific effects should be enabled.
 */
@Composable
fun shouldEnableBlur(): Boolean {
    val settings = rememberQualitySettings()
    return settings.enableBlur
}

@Composable
fun shouldEnableParticles(): Boolean {
    val settings = rememberQualitySettings()
    return settings.enableParticles
}

@Composable
fun shouldEnableComplexAnimations(): Boolean {
    val settings = rememberQualitySettings()
    return settings.enableComplexAnimations
}

@Composable
fun shouldEnableGlowEffects(): Boolean {
    val settings = rememberQualitySettings()
    return settings.enableGlowEffects
}

/**
 * Initialize adaptive UI quality on app start.
 */
@Composable
fun InitializeAdaptiveUI() {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        AdaptiveUIQuality.initialize(context)
    }
}

/**
 * Quality-aware blur modifier.
 * Returns no-op modifier on low-end devices.
 */
@Composable
fun qualityAwareBlur(radiusDp: Int): androidx.compose.ui.Modifier {
    val settings = rememberQualitySettings()
    
    return if (settings.enableBlur && settings.blurRadius > 0) {
        val actualRadius = (radiusDp * settings.blurRadius / 16).coerceAtLeast(1)
        androidx.compose.ui.Modifier.blur(actualRadius.dp)
    } else {
        androidx.compose.ui.Modifier
    }
}

/**
 * Quality-aware animation duration.
 */
@Composable
fun qualityAwareAnimationDuration(baseDuration: Int): Int {
    val settings = rememberQualitySettings()
    return (baseDuration * settings.animationDurationMultiplier).toInt().coerceAtLeast(0)
}

private val Int.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this.toFloat())
