package com.healthtracker.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Accessibility helper utilities for WCAG AA compliance.
 * 
 * REQUIREMENTS:
 * - TalkBack support with contentDescription
 * - WCAG AA color contrast (4.5:1 for normal text, 3:1 for large text)
 * - Gesture-free alternatives for all interactions
 * - Minimum 48dp touch targets
 */
object AccessibilityHelper {
    
    /**
     * Minimum touch target size per Material Design guidelines.
     */
    val MIN_TOUCH_TARGET_SIZE = 48.dp
    
    /**
     * Recommended touch target size for better accessibility.
     */
    val RECOMMENDED_TOUCH_TARGET_SIZE = 56.dp
    
    /**
     * Checks if a color contrast ratio meets WCAG AA standards.
     * 
     * @param foreground Foreground color (text)
     * @param background Background color
     * @param isLargeText Whether the text is large (18pt+ or 14pt+ bold)
     * @return true if contrast ratio meets WCAG AA
     */
    fun meetsContrastRequirement(
        foreground: androidx.compose.ui.graphics.Color,
        background: androidx.compose.ui.graphics.Color,
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        val requiredRatio = if (isLargeText) 3.0 else 4.5
        return ratio >= requiredRatio
    }
    
    /**
     * Calculates the contrast ratio between two colors.
     * 
     * @param color1 First color
     * @param color2 Second color
     * @return Contrast ratio (1.0 to 21.0)
     */
    fun calculateContrastRatio(
        color1: androidx.compose.ui.graphics.Color,
        color2: androidx.compose.ui.graphics.Color
    ): Double {
        val l1 = calculateRelativeLuminance(color1)
        val l2 = calculateRelativeLuminance(color2)
        
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        
        return (lighter + 0.05) / (darker + 0.05)
    }
    
    /**
     * Calculates relative luminance of a color.
     */
    private fun calculateRelativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)
        
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    
    /**
     * Linearizes a color component.
     */
    private fun linearize(component: Float): Double {
        return if (component <= 0.03928) {
            component / 12.92
        } else {
            Math.pow((component + 0.055) / 1.055, 2.4)
        }
    }
    
    /**
     * Generates a descriptive content description for health metrics.
     * 
     * @param metricName Name of the metric (e.g., "Steps")
     * @param value Current value
     * @param target Target value (optional)
     * @param unit Unit of measurement (e.g., "steps", "minutes")
     * @return Content description for TalkBack
     */
    fun healthMetricDescription(
        metricName: String,
        value: Number,
        target: Number? = null,
        unit: String
    ): String {
        return if (target != null) {
            "$metricName: $value out of $target $unit"
        } else {
            "$metricName: $value $unit"
        }
    }
    
    /**
     * Generates a descriptive content description for progress indicators.
     * 
     * @param progress Progress value (0.0 to 1.0)
     * @param label Label for the progress (e.g., "Daily steps goal")
     * @return Content description for TalkBack
     */
    fun progressDescription(progress: Float, label: String): String {
        val percentage = (progress * 100).toInt()
        return "$label: $percentage percent complete"
    }
    
    /**
     * Generates a descriptive content description for buttons.
     * 
     * @param action Action the button performs (e.g., "Start workout")
     * @param state Current state (optional, e.g., "disabled")
     * @return Content description for TalkBack
     */
    fun buttonDescription(action: String, state: String? = null): String {
        return if (state != null) {
            "$action button, $state"
        } else {
            "$action button"
        }
    }
}

/**
 * Modifier extension for accessible clickable elements.
 * 
 * Ensures:
 * - Minimum touch target size
 * - Proper semantic role
 * - Content description
 * 
 * @param contentDescription Description for TalkBack
 * @param role Semantic role (Button, Checkbox, etc.)
 * @param enabled Whether the element is enabled
 * @param onClick Click handler
 * @return Modifier with accessibility support
 */
@Composable
fun Modifier.accessibleClickable(
    contentDescription: String,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    
    return this
        .semantics {
            this.contentDescription = contentDescription
            this.role = role
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material.ripple.rememberRipple(),
            enabled = enabled,
            role = role,
            onClick = onClick
        )
        .minimumTouchTarget()
}

/**
 * Modifier extension to ensure minimum touch target size.
 * 
 * @param minSize Minimum size (default: 48dp)
 * @return Modifier with minimum size
 */
fun Modifier.minimumTouchTarget(minSize: Dp = AccessibilityHelper.MIN_TOUCH_TARGET_SIZE): Modifier {
    return this.size(minSize)
}

/**
 * Modifier extension for semantic content description.
 * 
 * @param description Content description for TalkBack
 * @return Modifier with content description
 */
fun Modifier.contentDescription(description: String): Modifier {
    return this.semantics {
        contentDescription = description
    }
}

/**
 * Composable for announcing changes to screen readers.
 * 
 * @param message Message to announce
 */
@Composable
fun AnnounceForAccessibility(message: String) {
    androidx.compose.runtime.LaunchedEffect(message) {
        // In production, use AccessibilityManager to announce
        timber.log.Timber.d("Accessibility announcement: $message")
    }
}

/**
 * Color contrast checker composable for debugging.
 * 
 * @param foreground Foreground color
 * @param background Background color
 * @param isLargeText Whether text is large
 */
@Composable
fun ColorContrastChecker(
    foreground: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    isLargeText: Boolean = false
) {
    val ratio = AccessibilityHelper.calculateContrastRatio(foreground, background)
    val meets = AccessibilityHelper.meetsContrastRequirement(foreground, background, isLargeText)
    
    if (!meets) {
        timber.log.Timber.w(
            "Color contrast warning: ratio=${"%.2f".format(ratio)}, " +
            "required=${if (isLargeText) "3.0" else "4.5"}"
        )
    }
}
