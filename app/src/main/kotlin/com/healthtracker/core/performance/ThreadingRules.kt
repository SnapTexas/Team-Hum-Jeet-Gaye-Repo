package com.healthtracker.core.performance

import android.os.Looper
import com.healthtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Strict threading rules enforcement for performance and stability.
 * 
 * CRITICAL RULES:
 * 1. ALL Firebase operations MUST run on Dispatchers.IO
 * 2. ALL Room database operations MUST run on Dispatchers.IO
 * 3. ALL ML inference MUST run on Dispatchers.Default
 * 4. ALL analytics calculations MUST run on Dispatchers.Default
 * 5. NEVER call suspend functions on Main thread
 * 6. UI updates MUST run on Dispatchers.Main
 * 
 * StrictMode in debug builds will detect violations.
 */
object ThreadingRules {
    
    /**
     * Checks if currently on the main thread.
     * 
     * @return true if on main thread
     */
    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }
    
    /**
     * Asserts that code is running on the main thread.
     * Throws exception in debug builds if not on main thread.
     */
    fun assertMainThread() {
        if (BuildConfig.DEBUG && !isMainThread()) {
            val error = IllegalStateException("Expected main thread but was ${Thread.currentThread().name}")
            Timber.e(error, "Threading violation detected")
            throw error
        }
    }
    
    /**
     * Asserts that code is NOT running on the main thread.
     * Throws exception in debug builds if on main thread.
     */
    fun assertBackgroundThread() {
        if (BuildConfig.DEBUG && isMainThread()) {
            val error = IllegalStateException("Expected background thread but was on main thread")
            Timber.e(error, "Threading violation detected")
            throw error
        }
    }
    
    /**
     * Logs current thread information.
     */
    fun logCurrentThread(tag: String = "ThreadInfo") {
        Timber.d("[$tag] Thread: ${Thread.currentThread().name}, isMain: ${isMainThread()}")
    }
    
    /**
     * Executes a block on IO dispatcher (for database and network operations).
     * 
     * Use this for:
     * - Firebase operations
     * - Room database operations
     * - File I/O
     * - Network requests
     * 
     * @param block The code to execute
     * @return Result of the block
     */
    suspend fun <T> onIO(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) {
                logCurrentThread("IO")
            }
            block()
        }
    }
    
    /**
     * Executes a block on Default dispatcher (for CPU-intensive operations).
     * 
     * Use this for:
     * - ML inference
     * - Analytics calculations
     * - Data processing
     * - Encryption/decryption
     * 
     * @param block The code to execute
     * @return Result of the block
     */
    suspend fun <T> onDefault(block: suspend () -> T): T {
        return withContext(Dispatchers.Default) {
            if (BuildConfig.DEBUG) {
                logCurrentThread("Default")
            }
            block()
        }
    }
    
    /**
     * Executes a block on Main dispatcher (for UI updates).
     * 
     * Use this for:
     * - UI updates
     * - View state changes
     * - Navigation
     * 
     * @param block The code to execute
     * @return Result of the block
     */
    suspend fun <T> onMain(block: suspend () -> T): T {
        return withContext(Dispatchers.Main) {
            if (BuildConfig.DEBUG) {
                logCurrentThread("Main")
            }
            block()
        }
    }
}

/**
 * Extension function to ensure Firebase operations run on IO dispatcher.
 */
suspend fun <T> ensureIO(block: suspend () -> T): T {
    return ThreadingRules.onIO(block)
}

/**
 * Extension function to ensure CPU-intensive operations run on Default dispatcher.
 */
suspend fun <T> ensureDefault(block: suspend () -> T): T {
    return ThreadingRules.onDefault(block)
}

/**
 * Extension function to ensure UI operations run on Main dispatcher.
 */
suspend fun <T> ensureMain(block: suspend () -> T): T {
    return ThreadingRules.onMain(block)
}

/**
 * Annotation to mark functions that must run on IO dispatcher.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresIO

/**
 * Annotation to mark functions that must run on Default dispatcher.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresDefault

/**
 * Annotation to mark functions that must run on Main dispatcher.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class RequiresMain
