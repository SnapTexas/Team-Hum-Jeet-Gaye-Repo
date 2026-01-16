package com.healthtracker.ml

import android.graphics.Bitmap
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML service wrapper that checks feature flags before executing ML operations.
 * 
 * If ML features are disabled via Remote Config, this service returns
 * fallback results instead of executing ML inference.
 * 
 * This allows disabling ML features remotely without app updates if issues arise.
 */
@Singleton
class FeatureGatedMLService @Inject constructor(
    private val mlService: OptimizedMLService,
    private val featureFlagProvider: () -> Boolean // Injected feature flag check
) : MLService {
    
    override suspend fun detectAnomalies(input: AnomalyInput): MLResult<List<AnomalyResult>> {
        return if (featureFlagProvider()) {
            mlService.detectAnomalies(input)
        } else {
            Timber.d("ML features disabled via feature flag")
            MLResult.Fallback("ML features are temporarily disabled")
        }
    }
    
    override suspend fun generateSuggestions(input: SuggestionInput): MLResult<List<SuggestionResult>> {
        return if (featureFlagProvider()) {
            mlService.generateSuggestions(input)
        } else {
            Timber.d("ML features disabled via feature flag")
            MLResult.Fallback("ML features are temporarily disabled")
        }
    }
    
    override suspend fun classifyFood(image: Bitmap): MLResult<FoodClassificationResult> {
        return if (featureFlagProvider()) {
            mlService.classifyFood(image)
        } else {
            Timber.d("ML features disabled via feature flag")
            MLResult.Fallback("ML features are temporarily disabled")
        }
    }
    
    override fun isReady(): Boolean {
        return if (featureFlagProvider()) {
            mlService.isReady()
        } else {
            false
        }
    }
    
    override suspend fun warmUp() {
        if (featureFlagProvider()) {
            mlService.warmUp()
        } else {
            Timber.d("Skipping ML warm-up - features disabled")
        }
    }
}
