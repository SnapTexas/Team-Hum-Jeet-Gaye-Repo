package com.healthtracker.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthtracker.domain.usecase.AISuggestionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for daily suggestion generation.
 * 
 * Scheduled to run at 12:00 AM daily to analyze the previous 24 hours
 * of health data and generate personalized suggestions for the next day.
 * 
 * Uses WorkManager for reliable scheduling that survives app restarts
 * and device reboots.
 */
@HiltWorker
class SuggestionGenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val aiSuggestionUseCase: AISuggestionUseCase
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "SuggestionWorker"
        const val WORK_NAME = "daily_suggestion_generation"
        
        /**
         * Schedules the daily suggestion generation worker.
         * 
         * Calculates the initial delay to start at 12:00 AM,
         * then repeats every 24 hours.
         * 
         * @param context Application context
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            // Calculate delay until next midnight
            val initialDelay = calculateDelayUntilMidnight()
            
            val workRequest = PeriodicWorkRequestBuilder<SuggestionGenerationWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d(TAG, "Scheduled daily suggestion generation, initial delay: ${initialDelay / 1000 / 60} minutes")
        }
        
        /**
         * Cancels the scheduled suggestion generation.
         * 
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled daily suggestion generation")
        }
        
        /**
         * Calculates milliseconds until next midnight (12:00 AM).
         */
        fun calculateDelayUntilMidnight(): Long {
            val now = Calendar.getInstance()
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            return midnight.timeInMillis - now.timeInMillis
        }
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily suggestion generation")
        
        return try {
            // Generate suggestions for tomorrow
            val result = aiSuggestionUseCase.generateDailySuggestions()
            
            Log.d(TAG, "Generated ${result.suggestions.size} suggestions, " +
                    "usedML: ${result.usedMLModel}, " +
                    "fallback: ${result.fallbackReason ?: "none"}")
            
            // Cleanup old suggestions (keep last 7 days)
            aiSuggestionUseCase.cleanupOldSuggestions(7)
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate suggestions", e)
            
            // Retry on failure (WorkManager will handle backoff)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
