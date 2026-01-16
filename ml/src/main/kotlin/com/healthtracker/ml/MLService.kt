package com.healthtracker.ml

import android.graphics.Bitmap

/**
 * Service interface for ML operations.
 * 
 * All methods return MLResult to ensure graceful fallback
 * when ML inference fails.
 */
interface MLService {
    
    /**
     * Detects anomalies in health metrics.
     * 
     * @param input Input data for anomaly detection
     * @return MLResult containing detected anomalies or fallback
     */
    suspend fun detectAnomalies(input: AnomalyInput): MLResult<List<AnomalyResult>>
    
    /**
     * Generates health suggestions based on metrics.
     * 
     * @param input Input data for suggestion generation
     * @return MLResult containing suggestions or fallback
     */
    suspend fun generateSuggestions(input: SuggestionInput): MLResult<List<SuggestionResult>>
    
    /**
     * Classifies food in an image.
     * 
     * @param image Bitmap image of food
     * @return MLResult containing classification or fallback
     */
    suspend fun classifyFood(image: Bitmap): MLResult<FoodClassificationResult>
    
    /**
     * Checks if ML models are loaded and ready.
     * 
     * @return true if models are ready for inference
     */
    fun isReady(): Boolean
    
    /**
     * Warms up ML models for faster first inference.
     * Should be called lazily, not at app startup.
     */
    suspend fun warmUp()
}

/**
 * Input data for anomaly detection.
 */
data class AnomalyInput(
    val steps: Int,
    val sleepMinutes: Int,
    val screenTimeMinutes: Int,
    val heartRate: Int?,
    val hrv: Double?,
    val baselineSteps: Double,
    val baselineSleep: Double,
    val baselineScreenTime: Double,
    val baselineHeartRate: Double?,
    val baselineHrv: Double?,
    val stdDevSteps: Double,
    val stdDevSleep: Double,
    val stdDevScreenTime: Double,
    val stdDevHeartRate: Double?,
    val stdDevHrv: Double?
)

/**
 * Result of anomaly detection.
 */
data class AnomalyResult(
    val type: String,
    val severity: String,
    val metricType: String,
    val actualValue: Double,
    val expectedMin: Double,
    val expectedMax: Double,
    val confidence: Float,
    val message: String
)

/**
 * Input data for suggestion generation.
 */
data class SuggestionInput(
    val steps: Int,
    val sleepMinutes: Int,
    val sleepQuality: String?,
    val caloriesBurned: Double,
    val screenTimeMinutes: Int,
    val heartRate: Int?,
    val hrv: Double?,
    val mood: String?,
    val waterIntakeMl: Int,
    val goal: String
)

/**
 * Result of suggestion generation.
 */
data class SuggestionResult(
    val type: String,
    val title: String,
    val description: String,
    val priority: Int,
    val confidence: Float
)

/**
 * Result of food classification.
 */
data class FoodClassificationResult(
    val foodName: String,
    val confidence: Float,
    val alternativeFoods: List<String>,
    val servingSize: String
)
