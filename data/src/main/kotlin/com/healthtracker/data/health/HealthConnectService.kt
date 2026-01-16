package com.healthtracker.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect permission state.
 */
enum class HealthConnectPermissionState {
    GRANTED,
    DENIED,
    PARTIALLY_GRANTED,
    NOT_AVAILABLE,
    NOT_REQUESTED
}

/**
 * Health Connect availability status.
 */
enum class HealthConnectAvailability {
    INSTALLED,
    NOT_INSTALLED,
    NOT_SUPPORTED
}

/**
 * Data class for collected health data from Health Connect.
 */
data class HealthConnectData(
    val steps: Int = 0,
    val distanceMeters: Double = 0.0,
    val caloriesBurned: Double = 0.0,
    val sleepDurationMinutes: Int = 0,
    val heartRateSamples: List<HeartRateSampleData> = emptyList(),
    val hrvSamples: List<HrvSampleData> = emptyList()
)

data class HeartRateSampleData(
    val timestamp: Instant,
    val bpm: Int
)

data class HrvSampleData(
    val timestamp: Instant,
    val rmssd: Double
)

/**
 * Service for interacting with Google Health Connect API.
 * 
 * CRITICAL: All operations are gated behind permission checks.
 * Rule: No permission → no data read → no crash.
 */
@Singleton
class HealthConnectService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        
        /**
         * Required permissions for health data collection.
         */
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
        )
    }
    
    private var healthConnectClient: HealthConnectClient? = null
    
    /**
     * Checks if Health Connect is available on this device.
     */
    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                Timber.d("Health Connect is available")
                HealthConnectAvailability.INSTALLED
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Timber.w("Health Connect needs update")
                HealthConnectAvailability.NOT_INSTALLED
            }
            else -> {
                Timber.w("Health Connect not supported on this device")
                HealthConnectAvailability.NOT_SUPPORTED
            }
        }
    }
    
    /**
     * Gets the Health Connect client, initializing if necessary.
     * Returns null if Health Connect is not available.
     */
    private fun getClient(): HealthConnectClient? {
        if (healthConnectClient == null) {
            if (checkAvailability() == HealthConnectAvailability.INSTALLED) {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
            }
        }
        return healthConnectClient
    }
    
    /**
     * Checks current permission state for Health Connect.
     */
    suspend fun checkPermissions(): HealthConnectPermissionState = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext HealthConnectPermissionState.NOT_AVAILABLE
        
        try {
            val granted = client.permissionController.getGrantedPermissions()
            
            when {
                granted.containsAll(REQUIRED_PERMISSIONS) -> {
                    Timber.d("All Health Connect permissions granted")
                    HealthConnectPermissionState.GRANTED
                }
                granted.isNotEmpty() -> {
                    Timber.d("Some Health Connect permissions granted: ${granted.size}/${REQUIRED_PERMISSIONS.size}")
                    HealthConnectPermissionState.PARTIALLY_GRANTED
                }
                else -> {
                    Timber.d("No Health Connect permissions granted")
                    HealthConnectPermissionState.NOT_REQUESTED
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking Health Connect permissions")
            HealthConnectPermissionState.NOT_AVAILABLE
        }
    }
    
    /**
     * Creates a permission request contract for Health Connect.
     * TODO: Implement when Health Connect SDK is properly configured
     */
    fun createPermissionRequestContract(): Any? {
        // val client = getClient() ?: throw IllegalStateException("Health Connect not available")
        // return client.permissionController.createRequestPermissionResultContract()
        return null
    }
    
    /**
     * Gets the intent to open Health Connect app for installation.
     */
    fun getHealthConnectInstallIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    /**
     * Reads health data for a specific date.
     * 
     * CRITICAL: Returns empty data if permissions not granted.
     */
    suspend fun readHealthData(date: LocalDate): HealthConnectData = withContext(Dispatchers.IO) {
        val client = getClient()
        if (client == null) {
            Timber.w("Health Connect not available, returning empty data")
            return@withContext HealthConnectData()
        }
        
        val permissionState = checkPermissions()
        if (permissionState != HealthConnectPermissionState.GRANTED && 
            permissionState != HealthConnectPermissionState.PARTIALLY_GRANTED) {
            Timber.w("Health Connect permissions not granted, returning empty data")
            return@withContext HealthConnectData()
        }
        
        val zoneId = ZoneId.systemDefault()
        val startTime = date.atStartOfDay(zoneId).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val timeRange = TimeRangeFilter.between(startTime, endTime)
        
        try {
            val steps = readSteps(client, timeRange)
            val distance = readDistance(client, timeRange)
            val calories = readCalories(client, timeRange)
            val sleep = readSleep(client, timeRange)
            val heartRate = readHeartRate(client, timeRange)
            val hrv = readHrv(client, timeRange)
            
            HealthConnectData(
                steps = steps,
                distanceMeters = distance,
                caloriesBurned = calories,
                sleepDurationMinutes = sleep,
                heartRateSamples = heartRate,
                hrvSamples = hrv
            )
        } catch (e: Exception) {
            Timber.e(e, "Error reading health data from Health Connect")
            HealthConnectData()
        }
    }

    
    /**
     * Reads step count for the time range.
     */
    private suspend fun readSteps(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): Int {
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.sumOf { it.count.toInt() }
        } catch (e: SecurityException) {
            Timber.w("Steps permission not granted")
            0
        } catch (e: Exception) {
            Timber.e(e, "Error reading steps")
            0
        }
    }
    
    /**
     * Reads distance for the time range.
     */
    private suspend fun readDistance(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): Double {
        return try {
            val request = ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.sumOf { it.distance.inMeters }
        } catch (e: SecurityException) {
            Timber.w("Distance permission not granted")
            0.0
        } catch (e: Exception) {
            Timber.e(e, "Error reading distance")
            0.0
        }
    }
    
    /**
     * Reads calories burned for the time range.
     */
    private suspend fun readCalories(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): Double {
        return try {
            val request = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.sumOf { it.energy.inKilocalories }
        } catch (e: SecurityException) {
            Timber.w("Calories permission not granted")
            0.0
        } catch (e: Exception) {
            Timber.e(e, "Error reading calories")
            0.0
        }
    }
    
    /**
     * Reads sleep duration for the time range.
     */
    private suspend fun readSleep(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): Int {
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.sumOf { record ->
                java.time.Duration.between(record.startTime, record.endTime).toMinutes().toInt()
            }
        } catch (e: SecurityException) {
            Timber.w("Sleep permission not granted")
            0
        } catch (e: Exception) {
            Timber.e(e, "Error reading sleep")
            0
        }
    }
    
    /**
     * Reads heart rate samples for the time range.
     */
    private suspend fun readHeartRate(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HeartRateSampleData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateSampleData(
                        timestamp = sample.time,
                        bpm = sample.beatsPerMinute.toInt()
                    )
                }
            }
        } catch (e: SecurityException) {
            Timber.w("Heart rate permission not granted")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error reading heart rate")
            emptyList()
        }
    }
    
    /**
     * Reads HRV samples for the time range.
     */
    private suspend fun readHrv(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<HrvSampleData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = timeRange
            )
            val response = client.readRecords(request)
            response.records.map { record ->
                HrvSampleData(
                    timestamp = record.time,
                    rmssd = record.heartRateVariabilityMillis
                )
            }
        } catch (e: SecurityException) {
            Timber.w("HRV permission not granted")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error reading HRV")
            emptyList()
        }
    }
    
    /**
     * Checks if a specific permission is granted.
     */
    suspend fun hasPermission(permission: String): Boolean = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(permission)
        } catch (e: Exception) {
            Timber.e(e, "Error checking permission: $permission")
            false
        }
    }
}
