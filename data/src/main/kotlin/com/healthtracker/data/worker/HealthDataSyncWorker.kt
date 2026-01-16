package com.healthtracker.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthtracker.data.health.HealthConnectPermissionState
import com.healthtracker.data.health.HealthConnectService
import com.healthtracker.data.repository.HealthDataRepositoryImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic health data synchronization.
 * 
 * CRITICAL: Uses WorkManager instead of long-running background services.
 * Android 8+ kills long-running services, so we use WorkManager with constraints.
 * 
 * Features:
 * - Runs every 15 minutes (minimum WorkManager interval)
 * - Only runs when battery is not low
 * - Syncs to Firebase when network is available
 * - Lifecycle-safe: survives app restarts
 */
@HiltWorker
class HealthDataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectService: HealthConnectService,
    private val healthDataRepository: HealthDataRepositoryImpl
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "health_data_sync"
        private const val SYNC_INTERVAL_MINUTES = 15L
        
        /**
         * Schedules periodic health data sync.
         * 
         * @param context Application context
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            
            Timber.d("Health data sync scheduled every $SYNC_INTERVAL_MINUTES minutes")
        }
        
        /**
         * Schedules sync with network constraint for Firebase upload.
         */
        fun scheduleWithNetwork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
                SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            
            Timber.d("Health data sync scheduled with network constraint")
        }
        
        /**
         * Cancels scheduled sync.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Health data sync cancelled")
        }
    }
    
    override suspend fun doWork(): Result {
        Timber.d("Starting health data sync")
        
        return try {
            // Check Health Connect permissions first
            val permissionState = healthConnectService.checkPermissions()
            if (permissionState != HealthConnectPermissionState.GRANTED &&
                permissionState != HealthConnectPermissionState.PARTIALLY_GRANTED) {
                Timber.w("Health Connect permissions not granted, skipping sync")
                return Result.success()
            }
            
            // Collect today's health data
            val today = LocalDate.now()
            val healthData = healthConnectService.readHealthData(today)
            
            Timber.d("Collected health data: steps=${healthData.steps}, distance=${healthData.distanceMeters}")
            
            // Save to local database
            val saveResult = healthDataRepository.saveHealthConnectData(today, healthData)
            
            if (saveResult.isSuccess) {
                Timber.d("Health data saved successfully")
                
                // Try to sync to Firebase (non-blocking)
                try {
                    healthDataRepository.syncHealthData()
                    Timber.d("Health data synced to Firebase")
                } catch (e: Exception) {
                    // Firebase sync failure is not critical
                    Timber.w(e, "Firebase sync failed, will retry later")
                }
                
                Result.success()
            } else {
                Timber.e("Failed to save health data")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "Health data sync failed")
            Result.retry()
        }
    }
}
