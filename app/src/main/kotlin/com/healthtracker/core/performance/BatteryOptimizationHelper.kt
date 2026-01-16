package com.healthtracker.core.performance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * Helper class for managing battery optimization settings.
 * 
 * CRITICAL: The app should work correctly even WITH battery optimization enabled.
 * We use WorkManager which is designed to work with Doze mode and App Standby.
 * 
 * Only request battery optimization exemption if absolutely necessary
 * (e.g., for critical health monitoring features).
 */
object BatteryOptimizationHelper {
    
    /**
     * Checks if the app is currently ignoring battery optimizations.
     * 
     * @param context Application context
     * @return true if battery optimization is disabled for this app
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = context.packageName
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true // Not applicable on older versions
    }
    
    /**
     * Opens the battery optimization settings for the app.
     * 
     * IMPORTANT: Only call this if the user explicitly wants to disable
     * battery optimization. The app should work correctly with optimization enabled.
     * 
     * @param context Application context
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Timber.d("Opened battery optimization settings")
            } catch (e: Exception) {
                Timber.e(e, "Failed to open battery optimization settings")
                // Fallback: open general battery optimization settings
                openBatteryOptimizationSettings(context)
            }
        }
    }
    
    /**
     * Opens the general battery optimization settings page.
     * 
     * @param context Application context
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Timber.d("Opened general battery optimization settings")
            } catch (e: Exception) {
                Timber.e(e, "Failed to open battery optimization settings")
            }
        }
    }
    
    /**
     * Checks if the device is in Doze mode.
     * 
     * @param context Application context
     * @return true if device is in Doze mode
     */
    fun isDeviceInDozeMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isDeviceIdleMode
        }
        return false
    }
    
    /**
     * Checks if the app is in App Standby mode.
     * 
     * @param context Application context
     * @return true if app is in standby
     */
    fun isAppInStandby(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isDeviceIdleMode || powerManager.isPowerSaveMode
        }
        return false
    }
    
    /**
     * Gets battery optimization status as a human-readable string.
     * 
     * @param context Application context
     * @return Status description
     */
    fun getBatteryOptimizationStatus(context: Context): String {
        return when {
            isIgnoringBatteryOptimizations(context) -> 
                "Battery optimization disabled (unrestricted background access)"
            isDeviceInDozeMode(context) -> 
                "Device in Doze mode (limited background access)"
            isAppInStandby(context) -> 
                "App in standby (restricted background access)"
            else -> 
                "Battery optimization enabled (normal background access)"
        }
    }
    
    /**
     * Logs current battery optimization status.
     * 
     * @param context Application context
     */
    fun logBatteryOptimizationStatus(context: Context) {
        Timber.d("Battery Optimization Status: ${getBatteryOptimizationStatus(context)}")
        Timber.d("Ignoring battery optimizations: ${isIgnoringBatteryOptimizations(context)}")
        Timber.d("Device in Doze mode: ${isDeviceInDozeMode(context)}")
        Timber.d("App in standby: ${isAppInStandby(context)}")
    }
}
