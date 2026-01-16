package com.healthtracker.di

import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.ml.FeatureGatedMLService
import com.healthtracker.ml.MLService
import com.healthtracker.ml.OptimizedMLService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for ML-related dependencies with feature flag integration.
 */
@Module
@InstallIn(SingletonComponent::class)
object MLModule {
    
    /**
     * Provides the ML service with feature flag gating.
     * 
     * If ML features are disabled via Remote Config, the service
     * returns fallback results instead of executing ML inference.
     */
    @Provides
    @Singleton
    fun provideMLService(
        optimizedMLService: OptimizedMLService,
        featureFlagManager: FeatureFlagManager
    ): MLService {
        return FeatureGatedMLService(
            mlService = optimizedMLService,
            featureFlagProvider = { featureFlagManager.isMlEnabled() }
        )
    }
}
