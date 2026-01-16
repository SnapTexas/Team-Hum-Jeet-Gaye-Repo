package com.healthtracker.core.error

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized crash boundary for handling uncaught exceptions.
 * 
 * CRITICAL: This prevents app crashes from uncaught coroutine exceptions
 * and provides graceful error handling throughout the app.
 * 
 * Features:
 * - Global CoroutineExceptionHandler
 * - Circuit breaker pattern for crash loops
 * - Error logging with context
 * - Non-blocking error UI support
 */
@Singleton
class CrashBoundary @Inject constructor(
    private val errorReporter: ErrorReporter
) {
    
    companion object {
        private const val MAX_CRASHES_PER_MINUTE = 5
        private const val CRASH_WINDOW_MS = 60_000L
    }
    
    private val crashTimestamps = mutableListOf<Long>()
    private var isInCrashLoop = false
    
    /**
     * Global exception handler for coroutines.
     * Use this when creating CoroutineScopes.
     */
    val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        handleException(throwable, context.toString())
    }
    
    /**
     * Creates a supervised scope with crash boundary protection.
     * Child coroutine failures won't cancel siblings.
     */
    fun createSafeScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + exceptionHandler)
    }
    
    /**
     * Handles an exception with context information.
     * 
     * @param throwable The exception that occurred
     * @param context Additional context (screen name, action, etc.)
     */
    fun handleException(throwable: Throwable, context: String = "Unknown") {
        // Log the error
        Timber.e(throwable, "Crash caught in context: $context")
        
        // Check for crash loop
        val now = System.currentTimeMillis()
        crashTimestamps.add(now)
        crashTimestamps.removeAll { it < now - CRASH_WINDOW_MS }
        
        if (crashTimestamps.size >= MAX_CRASHES_PER_MINUTE) {
            isInCrashLoop = true
            Timber.e("CRASH LOOP DETECTED! Entering safe mode.")
            errorReporter.reportCrashLoop(crashTimestamps.size)
        }
        
        // Report to error tracking service
        errorReporter.reportError(
            throwable = throwable,
            context = context,
            isCrashLoop = isInCrashLoop
        )
        
        // Notify UI if possible (non-blocking)
        errorReporter.showNonBlockingError(
            message = getErrorMessage(throwable),
            isRecoverable = isRecoverable(throwable)
        )
    }
    
    /**
     * Wraps a suspending block with crash protection.
     * Returns null on failure instead of throwing.
     */
    suspend fun <T> runSafely(
        context: String = "Unknown",
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            handleException(e, context)
            null
        }
    }
    
    /**
     * Wraps a suspending block with crash protection and default value.
     */
    suspend fun <T> runSafelyWithDefault(
        default: T,
        context: String = "Unknown",
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            handleException(e, context)
            default
        }
    }
    
    /**
     * Checks if the app is currently in a crash loop.
     */
    fun isInCrashLoop(): Boolean = isInCrashLoop
    
    /**
     * Resets crash loop detection (call after successful recovery).
     */
    fun resetCrashLoop() {
        crashTimestamps.clear()
        isInCrashLoop = false
        Timber.d("Crash loop reset")
    }
    
    /**
     * Determines if an error is recoverable.
     */
    private fun isRecoverable(throwable: Throwable): Boolean {
        return when (throwable) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> true
            else -> false
        }
    }
    
    /**
     * Gets a user-friendly error message.
     */
    private fun getErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is java.net.UnknownHostException -> "No internet connection"
            is java.net.SocketTimeoutException -> "Connection timed out"
            is SecurityException -> "Permission denied"
            is IllegalStateException -> "Something went wrong"
            else -> "An unexpected error occurred"
        }
    }
}

/**
 * Interface for error reporting and UI notification.
 */
interface ErrorReporter {
    /**
     * Reports an error to the tracking service.
     */
    fun reportError(
        throwable: Throwable,
        context: String,
        isCrashLoop: Boolean
    )
    
    /**
     * Reports a crash loop condition.
     */
    fun reportCrashLoop(crashCount: Int)
    
    /**
     * Shows a non-blocking error message to the user.
     */
    fun showNonBlockingError(
        message: String,
        isRecoverable: Boolean
    )
}

/**
 * Default implementation of ErrorReporter.
 * In production, this would integrate with Firebase Crashlytics.
 */
@Singleton
class DefaultErrorReporter @Inject constructor() : ErrorReporter {
    
    // Callback for UI to show errors
    var onError: ((String, Boolean) -> Unit)? = null
    
    override fun reportError(
        throwable: Throwable,
        context: String,
        isCrashLoop: Boolean
    ) {
        // Log locally
        Timber.e(throwable, "Error reported: context=$context, crashLoop=$isCrashLoop")
        
        // TODO: Send to Firebase Crashlytics
        // FirebaseCrashlytics.getInstance().apply {
        //     setCustomKey("context", context)
        //     setCustomKey("crash_loop", isCrashLoop)
        //     recordException(throwable)
        // }
    }
    
    override fun reportCrashLoop(crashCount: Int) {
        Timber.e("Crash loop reported: $crashCount crashes in last minute")
        
        // TODO: Send critical alert
        // FirebaseCrashlytics.getInstance().apply {
        //     setCustomKey("crash_loop_count", crashCount)
        //     log("CRASH LOOP DETECTED")
        // }
    }
    
    override fun showNonBlockingError(message: String, isRecoverable: Boolean) {
        Timber.d("Showing error: $message (recoverable=$isRecoverable)")
        onError?.invoke(message, isRecoverable)
    }
}
