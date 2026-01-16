package com.healthtracker.core.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureFlagManager @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values
        remoteConfig.setDefaultsAsync(getDefaultFlags())
    }

    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote config")
            false
        }
    }

    fun isMlEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_ENABLE_ML)
    }

    fun isAvatarEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_ENABLE_AVATAR)
    }

    fun isCvFoodEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_ENABLE_CV_FOOD)
    }

    fun isAnomalyDetectionEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_ENABLE_ANOMALY_DETECTION)
    }

    private fun getDefaultFlags(): Map<String, Any> {
        return mapOf(
            KEY_ENABLE_ML to true,
            KEY_ENABLE_AVATAR to true,
            KEY_ENABLE_CV_FOOD to true,
            KEY_ENABLE_ANOMALY_DETECTION to true
        )
    }

    companion object {
        const val KEY_ENABLE_ML = "enable_ml"
        const val KEY_ENABLE_AVATAR = "enable_avatar"
        const val KEY_ENABLE_CV_FOOD = "enable_cv_food"
        const val KEY_ENABLE_ANOMALY_DETECTION = "enable_anomaly_detection"
    }
}
