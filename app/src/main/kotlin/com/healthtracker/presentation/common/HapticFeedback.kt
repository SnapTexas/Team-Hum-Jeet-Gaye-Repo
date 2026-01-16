package com.healthtracker.presentation.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback helper for premium micro-interactions.
 */
object HapticFeedbackHelper {
    
    /**
     * Light tap feedback for button presses.
     */
    fun lightTap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    
    /**
     * Medium feedback for confirmations.
     */
    fun mediumTap(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
    
    /**
     * Heavy feedback for important actions.
     */
    fun heavyTap(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    
    /**
     * Success feedback for achievements.
     */
    fun success(context: Context) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }
    
    /**
     * Achievement unlock celebration pattern.
     */
    fun achievementUnlock(context: Context) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 100, 50, 100, 50, 200)
            val amplitudes = intArrayOf(0, 128, 0, 128, 0, 255)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, amplitudes, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 100, 50, 100, 50, 200), -1)
        }
    }
    
    /**
     * Goal completion celebration.
     */
    fun goalComplete(context: Context) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    }
    
    /**
     * Streak milestone celebration.
     */
    fun streakMilestone(context: Context) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 50, 30, 50, 30, 50, 30, 100)
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 50, 30, 50, 30, 50, 30, 100), -1)
        }
    }
    
    /**
     * Warning/alert feedback.
     */
    fun warning(context: Context) {
        val vibrator = getVibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(30)
        }
    }
    
    /**
     * Scroll boundary feedback.
     */
    fun scrollBoundary(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    
    /**
     * Selection change feedback.
     */
    fun selectionChange(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}

/**
 * Composable helper to access haptic feedback.
 */
@Composable
fun rememberHapticFeedback(): HapticFeedbackState {
    val view = LocalView.current
    val context = LocalContext.current
    
    return remember(view, context) {
        HapticFeedbackState(view, context)
    }
}

class HapticFeedbackState(
    private val view: View,
    private val context: Context
) {
    fun lightTap() = HapticFeedbackHelper.lightTap(view)
    fun mediumTap() = HapticFeedbackHelper.mediumTap(view)
    fun heavyTap() = HapticFeedbackHelper.heavyTap(view)
    fun success() = HapticFeedbackHelper.success(context)
    fun achievementUnlock() = HapticFeedbackHelper.achievementUnlock(context)
    fun goalComplete() = HapticFeedbackHelper.goalComplete(context)
    fun streakMilestone() = HapticFeedbackHelper.streakMilestone(context)
    fun warning() = HapticFeedbackHelper.warning(context)
    fun scrollBoundary() = HapticFeedbackHelper.scrollBoundary(view)
    fun selectionChange() = HapticFeedbackHelper.selectionChange(view)
}
