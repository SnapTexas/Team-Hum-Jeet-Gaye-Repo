package com.healthtracker.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.healthtracker.data.local.dao.SuggestionDao
import com.healthtracker.data.local.entity.SuggestionEntity
import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionAction
import com.healthtracker.domain.model.SuggestionType
import com.healthtracker.domain.repository.SuggestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Implementation of SuggestionRepository.
 * Handles storage and retrieval of AI-generated suggestions.
 */
class SuggestionRepositoryImpl @Inject constructor(
    private val suggestionDao: SuggestionDao,
    private val firebaseAuth: FirebaseAuth,
    private val gson: Gson
) : SuggestionRepository {
    
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "anonymous"
    
    override fun getSuggestions(date: LocalDate): Flow<List<Suggestion>> {
        val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return suggestionDao.getSuggestionsByDate(currentUserId, dateMillis)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override fun getTodaySuggestions(): Flow<List<Suggestion>> {
        val todayMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return suggestionDao.getActiveSuggestions(currentUserId, todayMillis)
            .map { entities -> entities.map { it.toDomain() } }
    }
    
    override suspend fun getSuggestionsSync(date: LocalDate): List<Suggestion> = withContext(Dispatchers.IO) {
        val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        suggestionDao.getSuggestionsByDateSync(currentUserId, dateMillis).map { it.toDomain() }
    }
    
    override suspend fun saveSuggestions(suggestions: List<Suggestion>) = withContext(Dispatchers.IO) {
        val entities = suggestions.map { it.toEntity() }
        suggestionDao.insertAllSuggestions(entities)
    }
    
    override suspend fun dismissSuggestion(suggestionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            suggestionDao.dismissSuggestion(suggestionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun completeSuggestion(suggestionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            suggestionDao.completeSuggestion(suggestionId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteOldSuggestions(daysToKeep: Int) = withContext(Dispatchers.IO) {
        val cutoffDate = LocalDate.now().minusDays(daysToKeep.toLong())
        val cutoffMillis = cutoffDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        suggestionDao.deleteOldSuggestions(cutoffMillis)
    }
    
    override suspend fun hasSuggestionsForDate(date: LocalDate): Boolean = withContext(Dispatchers.IO) {
        val dateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        suggestionDao.countSuggestionsForDate(currentUserId, dateMillis) > 0
    }
    
    // Extension functions for mapping
    private fun SuggestionEntity.toDomain(): Suggestion {
        val action = parseAction(actionType, actionDataJson)
        
        return Suggestion(
            id = id,
            userId = userId,
            type = SuggestionType.valueOf(type),
            title = title,
            description = description,
            priority = priority,
            actionable = actionable,
            action = action,
            generatedAt = Instant.ofEpochMilli(generatedAt),
            forDate = Instant.ofEpochMilli(forDate).atZone(ZoneId.systemDefault()).toLocalDate(),
            dismissed = dismissed,
            completed = completed
        )
    }
    
    private fun Suggestion.toEntity(): SuggestionEntity {
        val (actionType, actionData) = serializeAction(action)
        
        return SuggestionEntity(
            id = id,
            userId = userId,
            type = type.name,
            title = title,
            description = description,
            priority = priority,
            actionable = actionable,
            actionType = actionType,
            actionDataJson = actionData,
            generatedAt = generatedAt.toEpochMilli(),
            forDate = forDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            dismissed = dismissed,
            completed = completed
        )
    }
    
    private fun parseAction(actionType: String?, actionDataJson: String?): SuggestionAction? {
        if (actionType == null) return null
        
        return when (actionType) {
            "StartWorkout" -> {
                val data = gson.fromJson(actionDataJson, Map::class.java)
                SuggestionAction.StartWorkout(data["workoutId"] as String)
            }
            "LogWater" -> {
                val data = gson.fromJson(actionDataJson, Map::class.java)
                SuggestionAction.LogWater((data["targetMl"] as Double).toInt())
            }
            "StartMeditation" -> {
                val data = gson.fromJson(actionDataJson, Map::class.java)
                SuggestionAction.StartMeditation(data["sessionId"] as String)
            }
            "OpenDietTracker" -> SuggestionAction.OpenDietTracker
            "SetSleepReminder" -> {
                val data = gson.fromJson(actionDataJson, Map::class.java)
                SuggestionAction.SetSleepReminder(data["targetBedtime"] as String)
            }
            "OpenStepTracker" -> SuggestionAction.OpenStepTracker
            else -> null
        }
    }
    
    private fun serializeAction(action: SuggestionAction?): Pair<String?, String?> {
        if (action == null) return null to null
        
        return when (action) {
            is SuggestionAction.StartWorkout -> {
                "StartWorkout" to gson.toJson(mapOf("workoutId" to action.workoutId))
            }
            is SuggestionAction.LogWater -> {
                "LogWater" to gson.toJson(mapOf("targetMl" to action.targetMl))
            }
            is SuggestionAction.StartMeditation -> {
                "StartMeditation" to gson.toJson(mapOf("sessionId" to action.sessionId))
            }
            is SuggestionAction.OpenDietTracker -> {
                "OpenDietTracker" to null
            }
            is SuggestionAction.SetSleepReminder -> {
                "SetSleepReminder" to gson.toJson(mapOf("targetBedtime" to action.targetBedtime))
            }
            is SuggestionAction.OpenStepTracker -> {
                "OpenStepTracker" to null
            }
        }
    }
}
