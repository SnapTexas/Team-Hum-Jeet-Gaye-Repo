package com.healthtracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing AI-generated suggestions.
 * 
 * @property id Unique identifier
 * @property userId User who owns this suggestion
 * @property type Suggestion type (ACTIVITY, SLEEP, NUTRITION, etc.)
 * @property title Short title
 * @property description Detailed description
 * @property priority Priority level (1-5)
 * @property actionable Whether this has an action
 * @property actionType Type of action (nullable)
 * @property actionDataJson JSON data for the action (nullable)
 * @property generatedAt Timestamp when generated (epoch millis)
 * @property forDate Date this suggestion is for (epoch millis of start of day)
 * @property dismissed Whether user dismissed this
 * @property completed Whether user completed the action
 */
@Entity(
    tableName = "suggestions",
    indices = [
        Index(value = ["userId", "forDate"]),
        Index(value = ["dismissed"]),
        Index(value = ["completed"])
    ]
)
data class SuggestionEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val description: String,
    val priority: Int,
    val actionable: Boolean,
    val actionType: String?,
    val actionDataJson: String?,
    val generatedAt: Long,
    val forDate: Long,
    val dismissed: Boolean,
    val completed: Boolean
)
