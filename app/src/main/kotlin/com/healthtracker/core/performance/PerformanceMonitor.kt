package com.healthtracker.core.performance

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.healthtracker.BuildConfig
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Performance monitoring utility for tracking operation durations and detecting bottlenecks.
 * 
 * Features:
 * - Track operation durations
 * - Detect slow operations (>threshold)
 * - Log performance metrics
 * - Only active in debug builds
 */
object PerformanceMonitor {
    
    private val operationTimings = ConcurrentHashMap<String, Long>()
    private val operationCounts = ConcurrentHashMap<String, Int>()
    
    // Performance thresholds (milliseconds)
    private const val THRESHOLD_ML_INFERENCE = 200L
    private const val THRESHOLD_DATABASE_QUERY = 100L
    private const val THRESHOLD_NETWORK_REQUEST = 3000L
    private const val THRESHOLD_UI_RENDER = 16L // 60 FPS = 16ms per frame
    
    /**
     * Starts tracking an operation.
     * 
     * @param operationName Name of the operation
     * @return Start time in milliseconds
     */
    fun startOperation(operationName: String): Long {
        val startTime = SystemClock.elapsedRealtime()
        operationTimings[operationName] = startTime
        
        if (BuildConfig.DEBUG) {
            Timber.d("[Performance] Started: $operationName")
        }
        
        return startTime
    }
    
    /**
     * Ends tracking an operation and logs if it exceeded threshold.
     * 
     * @param operationName Name of the operation
     * @param threshold Optional custom threshold in milliseconds
     */
    fun endOperation(operationName: String, threshold: Long? = null) {
        val startTime = operationTimings.remove(operationName) ?: return
        val duration = SystemClock.elapsedRealtime() - startTime
        
        // Increment operation count
        operationCounts[operationName] = (operationCounts[operationName] ?: 0) + 1
        
        // Determine threshold
        val effectiveThreshold = threshold ?: getDefaultThreshold(operationName)
        
        if (BuildConfig.DEBUG) {
            if (duration > effectiveThreshold) {
                Timber.w("[Performance] SLOW: $operationName took ${duration}ms (threshold: ${effectiveThreshold}ms)")
            } else {
                Timber.d("[Performance] Completed: $operationName in ${duration}ms")
            }
        }
    }
    
    /**
     * Tracks an operation with automatic timing.
     * 
     * @param operationName Name of the operation
     * @param threshold Optional custom threshold
     * @param block The operation to track
     * @return Result of the operation
     */
    inline fun <T> track(
        operationName: String,
        threshold: Long? = null,
        block: () -> T
    ): T {
        startOperation(operationName)
        return try {
            block()
        } finally {
            endOperation(operationName, threshold)
        }
    }
    
    /**
     * Tracks a suspend operation with automatic timing.
     * 
     * @param operationName Name of the operation
     * @param threshold Optional custom threshold
     * @param block The suspend operation to track
     * @return Result of the operation
     */
    suspend inline fun <T> trackSuspend(
        operationName: String,
        threshold: Long? = null,
        crossinline block: suspend () -> T
    ): T {
        startOperation(operationName)
        return try {
            block()
        } finally {
            endOperation(operationName, threshold)
        }
    }
    
    /**
     * Gets performance statistics for an operation.
     * 
     * @param operationName Name of the operation
     * @return Number of times the operation was executed
     */
    fun getOperationCount(operationName: String): Int {
        return operationCounts[operationName] ?: 0
    }
    
    /**
     * Logs all performance statistics.
     */
    fun logStatistics() {
        if (BuildConfig.DEBUG && operationCounts.isNotEmpty()) {
            Timber.d("[Performance] === Statistics ===")
            operationCounts.entries
                .sortedByDescending { it.value }
                .forEach { (operation, count) ->
                    Timber.d("[Performance] $operation: $count executions")
                }
        }
    }
    
    /**
     * Clears all performance statistics.
     */
    fun clearStatistics() {
        operationTimings.clear()
        operationCounts.clear()
        if (BuildConfig.DEBUG) {
            Timber.d("[Performance] Statistics cleared")
        }
    }
    
    /**
     * Gets device performance tier.
     * 
     * @param context Application context
     * @return Performance tier (HIGH, MEDIUM, LOW)
     */
    fun getDevicePerformanceTier(context: Context): PerformanceTier {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val performanceClass = Build.VERSION.MEDIA_PERFORMANCE_CLASS
            return when {
                performanceClass >= Build.VERSION_CODES.TIRAMISU -> PerformanceTier.HIGH
                performanceClass >= Build.VERSION_CODES.S -> PerformanceTier.MEDIUM
                else -> PerformanceTier.LOW
            }
        }
        
        // Fallback: estimate based on available processors
        val processors = Runtime.getRuntime().availableProcessors()
        return when {
            processors >= 8 -> PerformanceTier.HIGH
            processors >= 4 -> PerformanceTier.MEDIUM
            else -> PerformanceTier.LOW
        }
    }
    
    /**
     * Checks if device should use reduced visual effects.
     * 
     * @param context Application context
     * @return true if device is low-end and should use reduced effects
     */
    fun shouldUseReducedEffects(context: Context): Boolean {
        return getDevicePerformanceTier(context) == PerformanceTier.LOW
    }
    
    private fun getDefaultThreshold(operationName: String): Long {
        return when {
            operationName.contains("ML", ignoreCase = true) || 
            operationName.contains("inference", ignoreCase = true) -> THRESHOLD_ML_INFERENCE
            
            operationName.contains("database", ignoreCase = true) || 
            operationName.contains("query", ignoreCase = true) -> THRESHOLD_DATABASE_QUERY
            
            operationName.contains("network", ignoreCase = true) || 
            operationName.contains("api", ignoreCase = true) -> THRESHOLD_NETWORK_REQUEST
            
            operationName.contains("render", ignoreCase = true) || 
            operationName.contains("draw", ignoreCase = true) -> THRESHOLD_UI_RENDER
            
            else -> 1000L // Default 1 second
        }
    }
}

/**
 * Device performance tier classification.
 */
enum class PerformanceTier {
    HIGH,    // Flagship devices, enable all effects
    MEDIUM,  // Mid-range devices, enable most effects
    LOW      // Low-end devices, use reduced effects
}
