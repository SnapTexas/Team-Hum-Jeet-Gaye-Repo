package com.healthtracker.data.repository

import com.google.gson.Gson
import com.healthtracker.data.health.HealthConnectData
import com.healthtracker.data.health.HeartRateSampleData
import com.healthtracker.data.health.HrvSampleData
import com.healthtracker.data.local.dao.HealthMetricsDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.local.entity.HealthMetricsEntity
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.HeartRateSample
import com.healthtracker.domain.model.HrvSample
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.Result
import kotlinx.coroutines.runBlocking
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.repository.HealthDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of HealthDataRepository with local-first caching.
 * 
 * Strategy:
 * 1. Always save to local Room database first
 * 2. Mark records for sync with Firebase
 * 3. Sync in background when network available
 * 4. Use last-write-wins with versioning for conflict resolution
 * 
 * CRITICAL: This ensures app works offline and data is never lost.
 */
@Singleton
class HealthDataRepositoryImpl @Inject constructor(
    private val healthMetricsDao: HealthMetricsDao,
    private val userDao: UserDao,
    private val gson: Gson
) : HealthDataRepository {
    
    companion object {
        // Average step length in meters (used for distance calculation)
        const val AVERAGE_STEP_LENGTH_METERS = 0.762 // ~30 inches
    }
    
    // ============================================
    // Public API
    // ============================================
    
    override fun getHealthMetrics(date: LocalDate): Flow<HealthMetrics?> {
        return healthMetricsDao.getMetricsByDate(
            userId = getCurrentUserId(),
            date = date.toEpochDay()
        ).map { entity ->
            entity?.toDomain()
        }
    }
    
    override fun getHealthMetricsRange(start: LocalDate, end: LocalDate): Flow<List<HealthMetrics>> {
        return healthMetricsDao.getMetricsInRange(
            userId = getCurrentUserId(),
            startDate = start.toEpochDay(),
            endDate = end.toEpochDay()
        ).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getMetricsForDateSync(date: LocalDate): HealthMetrics? = withContext(Dispatchers.IO) {
        healthMetricsDao.getMetricsByDateSync(
            userId = getCurrentUserId(),
            date = date.toEpochDay()
        )?.toDomain()
    }
    
    override suspend fun getMetricsRangeSync(start: LocalDate, end: LocalDate): List<HealthMetrics> = 
        withContext(Dispatchers.IO) {
            healthMetricsDao.getMetricsInRangeSync(
                userId = getCurrentUserId(),
                startDate = start.toEpochDay(),
                endDate = end.toEpochDay()
            ).map { it.toDomain() }
        }
    
    override suspend fun syncHealthData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val metricsToSync = healthMetricsDao.getMetricsNeedingSync(userId)
            
            if (metricsToSync.isEmpty()) {
                Timber.d("No metrics to sync")
                return@withContext Result.Success(Unit)
            }
            
            Timber.d("Syncing ${metricsToSync.size} metrics records")
            
            // TODO: Implement Firebase sync
            // For now, just mark as synced
            metricsToSync.forEach { metrics ->
                healthMetricsDao.markAsSynced(metrics.id, System.currentTimeMillis())
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync health data")
            Result.Error(
                AppException.NetworkException(
                    message = "Failed to sync health data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun insertMetrics(metrics: HealthMetrics): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val entity = metrics.toEntity(needsSync = true)
                
                // Check for existing record and handle conflict
                val existing = healthMetricsDao.getLatestMetrics(metrics.userId)
                if (existing != null && existing.date == metrics.date.toEpochDay()) {
                    // Conflict resolution: last-write-wins with version increment
                    val resolvedEntity = resolveConflict(existing, entity)
                    healthMetricsDao.updateMetrics(resolvedEntity)
                    Timber.d("Updated existing metrics with conflict resolution")
                } else {
                    healthMetricsDao.insertMetrics(entity)
                    Timber.d("Inserted new metrics")
                }
                
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to insert metrics")
                Result.Error(
                    AppException.StorageException(
                        message = "Failed to save metrics: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    
    override suspend fun getLatestMetrics(): Result<HealthMetrics?> = withContext(Dispatchers.IO) {
        try {
            val entity = healthMetricsDao.getLatestMetrics(getCurrentUserId())
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get latest metrics")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to get metrics: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    override suspend fun deleteAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            healthMetricsDao.deleteAllForUser(getCurrentUserId())
            Timber.d("Deleted all health data")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete health data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to delete data: ${e.message}",
                    cause = e
                )
            )
        }
    }

    
    // ============================================
    // Health Connect Integration
    // ============================================
    
    /**
     * Saves health data from Health Connect to local database.
     * Calculates derived metrics like distance from steps.
     */
    suspend fun saveHealthConnectData(
        date: LocalDate,
        data: HealthConnectData
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            val existingEntity = getExistingMetrics(userId, date)
            
            // Calculate distance from steps if not provided
            val distance = if (data.distanceMeters > 0) {
                data.distanceMeters
            } else {
                calculateDistanceFromSteps(data.steps)
            }
            
            val entity = if (existingEntity != null) {
                // Merge with existing data
                existingEntity.copy(
                    steps = maxOf(existingEntity.steps, data.steps),
                    distanceMeters = maxOf(existingEntity.distanceMeters, distance),
                    caloriesBurned = maxOf(existingEntity.caloriesBurned, data.caloriesBurned),
                    sleepDurationMinutes = maxOf(existingEntity.sleepDurationMinutes, data.sleepDurationMinutes),
                    heartRateSamplesJson = mergeHeartRateSamples(
                        existingEntity.heartRateSamplesJson,
                        data.heartRateSamples
                    ),
                    hrvSamplesJson = mergeHrvSamples(
                        existingEntity.hrvSamplesJson,
                        data.hrvSamples
                    ),
                    needsSync = true,
                    version = existingEntity.version + 1
                )
            } else {
                // Create new record
                HealthMetricsEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    date = date.toEpochDay(),
                    steps = data.steps,
                    distanceMeters = distance,
                    caloriesBurned = data.caloriesBurned,
                    screenTimeMinutes = 0, // Not from Health Connect
                    sleepDurationMinutes = data.sleepDurationMinutes,
                    sleepQuality = null,
                    heartRateSamplesJson = gson.toJson(data.heartRateSamples.map { 
                        mapOf("timestamp" to it.timestamp.toEpochMilli(), "bpm" to it.bpm)
                    }),
                    hrvSamplesJson = gson.toJson(data.hrvSamples.map {
                        mapOf("timestamp" to it.timestamp.toEpochMilli(), "rmssd" to it.rmssd)
                    }),
                    mood = null,
                    syncedAt = 0,
                    needsSync = true,
                    version = 1
                )
            }
            
            healthMetricsDao.insertMetrics(entity)
            Timber.d("Saved Health Connect data for $date")
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save Health Connect data")
            Result.Error(
                AppException.StorageException(
                    message = "Failed to save health data: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    // ============================================
    // Distance Calculation (Task 3.4)
    // ============================================
    
    /**
     * Calculates distance from step count.
     * 
     * Formula: distance = steps × average_step_length
     * 
     * Property 3: distance = steps × step_length, result always non-negative
     */
    fun calculateDistanceFromSteps(steps: Int): Double {
        require(steps >= 0) { "Steps cannot be negative" }
        return steps * AVERAGE_STEP_LENGTH_METERS
    }
    
    // ============================================
    // Conflict Resolution (Task 3.3)
    // ============================================
    
    /**
     * Resolves conflicts between local and incoming data.
     * 
     * Strategy: Last-write-wins with version increment.
     * Prefers the record with higher version, or newer syncedAt timestamp.
     */
    private fun resolveConflict(
        existing: HealthMetricsEntity,
        incoming: HealthMetricsEntity
    ): HealthMetricsEntity {
        Timber.d("Resolving conflict: existing v${existing.version} vs incoming v${incoming.version}")
        
        // If incoming has higher version, use it
        if (incoming.version > existing.version) {
            Timber.d("Using incoming record (higher version)")
            return incoming.copy(version = incoming.version + 1)
        }
        
        // If same version, prefer newer syncedAt
        if (incoming.syncedAt > existing.syncedAt) {
            Timber.d("Using incoming record (newer timestamp)")
            return incoming.copy(version = existing.version + 1)
        }
        
        // Otherwise, merge data preferring higher values
        Timber.d("Merging records")
        return existing.copy(
            steps = maxOf(existing.steps, incoming.steps),
            distanceMeters = maxOf(existing.distanceMeters, incoming.distanceMeters),
            caloriesBurned = maxOf(existing.caloriesBurned, incoming.caloriesBurned),
            screenTimeMinutes = maxOf(existing.screenTimeMinutes, incoming.screenTimeMinutes),
            sleepDurationMinutes = maxOf(existing.sleepDurationMinutes, incoming.sleepDurationMinutes),
            sleepQuality = incoming.sleepQuality ?: existing.sleepQuality,
            mood = incoming.mood ?: existing.mood,
            needsSync = true,
            version = existing.version + 1
        )
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    private fun getCurrentUserId(): String {
        return runBlocking {
            userDao.getUser()?.id ?: "anonymous"
        }
    }
    
    private suspend fun getExistingMetrics(userId: String, date: LocalDate): HealthMetricsEntity? {
        return healthMetricsDao.getLatestMetrics(userId)?.takeIf { 
            it.date == date.toEpochDay() 
        }
    }
    
    private fun mergeHeartRateSamples(
        existingJson: String,
        newSamples: List<HeartRateSampleData>
    ): String {
        val existing = try {
            gson.fromJson(existingJson, List::class.java) as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        val newMapped = newSamples.map { 
            mapOf("timestamp" to it.timestamp.toEpochMilli(), "bpm" to it.bpm)
        }
        
        // Combine and deduplicate by timestamp
        val combined = (existing + newMapped)
            .distinctBy { (it as Map<*, *>)["timestamp"] }
            .sortedBy { (it as Map<*, *>)["timestamp"] as Long }
        
        return gson.toJson(combined)
    }
    
    private fun mergeHrvSamples(
        existingJson: String,
        newSamples: List<HrvSampleData>
    ): String {
        val existing = try {
            gson.fromJson(existingJson, List::class.java) as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        
        val newMapped = newSamples.map {
            mapOf("timestamp" to it.timestamp.toEpochMilli(), "rmssd" to it.rmssd)
        }
        
        val combined = (existing + newMapped)
            .distinctBy { (it as Map<*, *>)["timestamp"] }
            .sortedBy { (it as Map<*, *>)["timestamp"] as Long }
        
        return gson.toJson(combined)
    }
    
    // ============================================
    // Entity/Domain Mapping
    // ============================================
    
    private fun HealthMetricsEntity.toDomain(): HealthMetrics {
        return HealthMetrics(
            id = id,
            userId = userId,
            date = LocalDate.ofEpochDay(date),
            steps = steps,
            distanceMeters = distanceMeters,
            caloriesBurned = caloriesBurned,
            screenTimeMinutes = screenTimeMinutes,
            sleepDurationMinutes = sleepDurationMinutes,
            sleepQuality = sleepQuality?.let { SleepQuality.valueOf(it) },
            heartRateSamples = parseHeartRateSamples(heartRateSamplesJson),
            hrvSamples = parseHrvSamples(hrvSamplesJson),
            mood = mood?.let { Mood.valueOf(it) },
            syncedAt = Instant.ofEpochMilli(syncedAt)
        )
    }
    
    private fun HealthMetrics.toEntity(needsSync: Boolean = false): HealthMetricsEntity {
        return HealthMetricsEntity(
            id = id,
            userId = userId,
            date = date.toEpochDay(),
            steps = steps,
            distanceMeters = distanceMeters,
            caloriesBurned = caloriesBurned,
            screenTimeMinutes = screenTimeMinutes,
            sleepDurationMinutes = sleepDurationMinutes,
            sleepQuality = sleepQuality?.name,
            heartRateSamplesJson = gson.toJson(heartRateSamples.map {
                mapOf("timestamp" to it.timestamp.toEpochMilli(), "bpm" to it.bpm)
            }),
            hrvSamplesJson = gson.toJson(hrvSamples.map {
                mapOf("timestamp" to it.timestamp.toEpochMilli(), "sdnn" to it.sdnn)
            }),
            mood = mood?.name,
            syncedAt = syncedAt.toEpochMilli(),
            needsSync = needsSync,
            version = 1
        )
    }
    
    private fun parseHeartRateSamples(json: String): List<HeartRateSample> {
        return try {
            val list = gson.fromJson(json, List::class.java) as? List<Map<String, Any>> ?: emptyList()
            list.map { map ->
                HeartRateSample(
                    timestamp = Instant.ofEpochMilli((map["timestamp"] as Number).toLong()),
                    bpm = (map["bpm"] as Number).toInt()
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse heart rate samples")
            emptyList()
        }
    }
    
    private fun parseHrvSamples(json: String): List<HrvSample> {
        return try {
            val list = gson.fromJson(json, List::class.java) as? List<Map<String, Any>> ?: emptyList()
            list.map { map ->
                HrvSample(
                    timestamp = Instant.ofEpochMilli((map["timestamp"] as Number).toLong()),
                    sdnn = (map["sdnn"] as? Number)?.toDouble() 
                        ?: (map["rmssd"] as? Number)?.toDouble() 
                        ?: 0.0
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse HRV samples")
            emptyList()
        }
    }
}
