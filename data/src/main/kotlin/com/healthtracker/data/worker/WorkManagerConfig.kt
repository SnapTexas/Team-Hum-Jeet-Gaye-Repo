package com.healthtracker.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Centralized WorkManager configuration for lifecycle-safe background operations.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * - NO long-running background services (Android 8+ kills them)
 * - Use WorkManager for all periodic tasks
 * - Use ForegroundService ONLY when user-visible work is required
 * - Batch operations to minimize wake locks
 * - Target <5% daily battery usage
 * 
 * WorkManager automatically handles:
 * - Job scheduling across app restarts
 * - Device reboots
 * - Doze mode and App Standby
 * - Battery optimization
 */
object WorkManagerConfig {
    
    /**
     * Initializes all periodic workers with optimal constraints.
     * 
     * Call this from Application.onCreate() or after user login.
     * 
     * @param context Application context
     */
    fun initializeWorkers(context: Context) {
        Timber.d("Initializing WorkManager periodic tasks")
        
        // Schedule health data sync (every 15 minutes)
        scheduleHealthDataSync(context)
        
        // Schedule daily suggestion generation (at midnight)
        scheduleDailySuggestions(context)
        
        Timber.d("WorkManager initialization complete")
    }
    
    /**
     * Schedules periodic health data synchronization.
     * 
     * Features:
     * - Runs every 15 minutes (minimum WorkManager interval)
     * - Only runs when battery is not low
     * - Requires network for Firebase sync
     * - Survives app restarts and device reboots
     * 
     * @param context Application context
     */
    private fun scheduleHealthDataSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("health_sync")
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HealthDataSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        Timber.d("Health data sync scheduled (15 min interval)")
    }
    
    /**
     * Schedules daily suggestion generation at midnight.
     * 
     * Features:
     * - Runs once per day at 12:00 AM
     * - Only runs when battery is not low
     * - Calculates initial delay to next midnight
     * 
     * @param context Application context
     */
    private fun scheduleDailySuggestions(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        
        val suggestionRequest = PeriodicWorkRequestBuilder<SuggestionGenerationWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(
                SuggestionGenerationWorker.calculateDelayUntilMidnight(),
                TimeUnit.MILLISECONDS
            )
            .addTag("daily_suggestions")
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SuggestionGenerationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            suggestionRequest
        )
        
        Timber.d("Daily suggestion generation scheduled (midnight)")
    }
    
    /**
     * Cancels all scheduled workers.
     * 
     * Call this when user logs out or disables background sync.
     * 
     * @param context Application context
     */
    fun cancelAllWorkers(context: Context) {
        Timber.d("Cancelling all WorkManager tasks")
        
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(HealthDataSyncWorker.WORK_NAME)
            cancelUniqueWork(SuggestionGenerationWorker.WORK_NAME)
        }
        
        Timber.d("All WorkManager tasks cancelled")
    }
    
    /**
     * Pauses background sync (e.g., when user disables data collection).
     * 
     * @param context Application context
     */
    fun pauseBackgroundSync(context: Context) {
        Timber.d("Pausing background sync")
        WorkManager.getInstance(context).cancelUniqueWork(HealthDataSyncWorker.WORK_NAME)
    }
    
    /**
     * Resumes background sync.
     * 
     * @param context Application context
     */
    fun resumeBackgroundSync(context: Context) {
        Timber.d("Resuming background sync")
        scheduleHealthDataSync(context)
    }
}
