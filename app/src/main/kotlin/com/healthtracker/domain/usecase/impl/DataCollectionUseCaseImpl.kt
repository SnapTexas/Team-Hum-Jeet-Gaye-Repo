package com.healthtracker.domain.usecase.impl

import android.content.Context
import com.healthtracker.data.health.HealthConnectAvailability
import com.healthtracker.data.health.HealthConnectPermissionState
import com.healthtracker.data.health.HealthConnectService
import com.healthtracker.data.repository.HealthDataRepositoryImpl
import com.healthtracker.data.worker.HealthDataSyncWorker
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.usecase.DataCollectionPermissionState
import com.healthtracker.domain.usecase.DataCollectionResult
import com.healthtracker.domain.usecase.DataCollectionUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Implementation of DataCollectionUseCase.
 * 
 * CRITICAL: All data access is gated behind permission checks.
 * Rule: No permission → no repository call → no crash.
 * 
 * Features:
 * - Permission state machine
 * - Graceful degradation
 * - Background collection via WorkManager
 */
class DataCollectionUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectService: HealthConnectService,
    private val healthDataRepository: HealthDataRepository,
    private val healthDataRepositoryImpl: HealthDataRepositoryImpl
) : DataCollectionUseCase {
    
    override suspend fun getPermissionState(): DataCollectionPermissionState {
        // First check if Health Connect is available
        val availability = healthConnectService.checkAvailability()
        if (availability != HealthConnectAvailability.INSTALLED) {
            return DataCollectionPermissionState.NOT_AVAILABLE
        }
        
        // Check permission state
        return when (healthConnectService.checkPermissions()) {
            HealthConnectPermissionState.GRANTED -> DataCollectionPermissionState.GRANTED
            HealthConnectPermissionState.DENIED -> DataCollectionPermissionState.DENIED
            HealthConnectPermissionState.PARTIALLY_GRANTED -> DataCollectionPermissionState.PARTIALLY_GRANTED
            HealthConnectPermissionState.NOT_AVAILABLE -> DataCollectionPermissionState.NOT_AVAILABLE
            HealthConnectPermissionState.NOT_REQUESTED -> DataCollectionPermissionState.NOT_REQUESTED
        }
    }
    
    override suspend fun collectHealthData(date: LocalDate): DataCollectionResult {
        Timber.d("Collecting health data for $date")
        
        // CRITICAL: Check permissions first
        val permissionState = getPermissionState()
        
        return when (permissionState) {
            DataCollectionPermissionState.NOT_AVAILABLE -> {
                Timber.w("Health Connect not available")
                DataCollectionResult.Error("Health Connect is not available on this device")
            }
            
            DataCollectionPermissionState.NOT_REQUESTED,
            DataCollectionPermissionState.DENIED -> {
                Timber.w("Permissions not granted, requesting")
                DataCollectionResult.PermissionRequired(getMissingPermissions())
            }
            
            DataCollectionPermissionState.PERMANENTLY_DENIED -> {
                Timber.w("Permissions permanently denied")
                DataCollectionResult.Error("Health permissions are permanently denied. Please enable them in Settings.")
            }
            
            DataCollectionPermissionState.PARTIALLY_GRANTED -> {
                // Collect what we can with partial permissions
                Timber.d("Collecting with partial permissions")
                collectWithPartialPermissions(date)
            }
            
            DataCollectionPermissionState.GRANTED -> {
                // Full data collection
                Timber.d("Collecting with full permissions")
                collectWithFullPermissions(date)
            }
        }
    }
    
    override fun getHealthMetrics(date: LocalDate): Flow<HealthMetrics?> {
        return healthDataRepository.getHealthMetrics(date)
    }
    
    override fun getHealthMetricsRange(start: LocalDate, end: LocalDate): Flow<List<HealthMetrics>> {
        return healthDataRepository.getHealthMetricsRange(start, end)
    }
    
    override suspend fun syncHealthData(): Result<Unit> {
        return healthDataRepository.syncHealthData()
    }
    
    override suspend fun getMissingPermissions(): List<String> {
        // Return list of permission descriptions for UI
        val permissionState = healthConnectService.checkPermissions()
        
        return when (permissionState) {
            HealthConnectPermissionState.NOT_REQUESTED,
            HealthConnectPermissionState.DENIED -> {
                listOf(
                    "Steps",
                    "Distance",
                    "Calories",
                    "Heart Rate",
                    "Sleep",
                    "Heart Rate Variability"
                )
            }
            HealthConnectPermissionState.PARTIALLY_GRANTED -> {
                // TODO: Check individual permissions
                listOf("Some health permissions")
            }
            else -> emptyList()
        }
    }
    
    override fun isHealthConnectAvailable(): Boolean {
        return healthConnectService.checkAvailability() == HealthConnectAvailability.INSTALLED
    }
    
    override fun scheduleBackgroundCollection() {
        Timber.d("Scheduling background health data collection")
        HealthDataSyncWorker.schedule(context)
    }
    
    override fun cancelBackgroundCollection() {
        Timber.d("Cancelling background health data collection")
        HealthDataSyncWorker.cancel(context)
    }
    
    // ============================================
    // Private Methods
    // ============================================
    
    private suspend fun collectWithFullPermissions(date: LocalDate): DataCollectionResult {
        return try {
            val healthData = healthConnectService.readHealthData(date)
            
            // Save to local database
            val saveResult = healthDataRepositoryImpl.saveHealthConnectData(date, healthData)
            
            if (saveResult.isSuccess) {
                // Create metrics object
                val metrics = HealthMetrics(
                    id = UUID.randomUUID().toString(),
                    userId = "current", // Will be replaced by repository
                    date = date,
                    steps = healthData.steps,
                    distanceMeters = healthData.distanceMeters.takeIf { it > 0 }
                        ?: healthDataRepositoryImpl.calculateDistanceFromSteps(healthData.steps),
                    caloriesBurned = healthData.caloriesBurned,
                    screenTimeMinutes = 0,
                    sleepDurationMinutes = healthData.sleepDurationMinutes,
                    sleepQuality = null,
                    heartRateSamples = healthData.heartRateSamples.map {
                        HeartRateSample(it.timestamp, it.bpm)
                    },
                    hrvSamples = healthData.hrvSamples.map {
                        HrvSample(it.timestamp, it.rmssd)
                    },
                    mood = null,
                    syncedAt = Instant.now()
                )
                
                DataCollectionResult.Success(metrics)
            } else {
                DataCollectionResult.Error("Failed to save health data")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error collecting health data")
            DataCollectionResult.Error("Failed to collect health data: ${e.message}")
        }
    }
    
    private suspend fun collectWithPartialPermissions(date: LocalDate): DataCollectionResult {
        return try {
            val healthData = healthConnectService.readHealthData(date)
            
            // Save whatever we got
            val saveResult = healthDataRepositoryImpl.saveHealthConnectData(date, healthData)
            
            if (saveResult.isSuccess) {
                val metrics = HealthMetrics(
                    id = UUID.randomUUID().toString(),
                    userId = "current",
                    date = date,
                    steps = healthData.steps,
                    distanceMeters = healthData.distanceMeters.takeIf { it > 0 }
                        ?: healthDataRepositoryImpl.calculateDistanceFromSteps(healthData.steps),
                    caloriesBurned = healthData.caloriesBurned,
                    screenTimeMinutes = 0,
                    sleepDurationMinutes = healthData.sleepDurationMinutes,
                    sleepQuality = null,
                    heartRateSamples = healthData.heartRateSamples.map {
                        HeartRateSample(it.timestamp, it.bpm)
                    },
                    hrvSamples = healthData.hrvSamples.map {
                        HrvSample(it.timestamp, it.rmssd)
                    },
                    mood = null,
                    syncedAt = Instant.now()
                )
                
                val missingPermissions = getMissingPermissions()
                DataCollectionResult.PartialSuccess(metrics, missingPermissions)
            } else {
                DataCollectionResult.Error("Failed to save health data")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error collecting health data with partial permissions")
            DataCollectionResult.Error("Failed to collect health data: ${e.message}")
        }
    }
}
