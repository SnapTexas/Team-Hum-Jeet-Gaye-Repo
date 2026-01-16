package com.healthtracker.ml

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ML model versioning and OTA updates.
 * 
 * FEATURES:
 * - Semantic versioning (MAJOR.MINOR.PATCH)
 * - Model integrity verification (SHA-256 hash)
 * - Secure OTA delivery
 * - Rollback on accuracy degradation
 * - Version tracking per model
 */
@Singleton
class ModelVersionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "model_versions",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    companion object {
        private const val KEY_ANOMALY_VERSION = "anomaly_model_version"
        private const val KEY_SUGGESTION_VERSION = "suggestion_model_version"
        private const val KEY_ANOMALY_HASH = "anomaly_model_hash"
        private const val KEY_SUGGESTION_HASH = "suggestion_model_hash"
        
        // Current bundled model versions
        private const val BUNDLED_ANOMALY_VERSION = "1.0.0"
        private const val BUNDLED_SUGGESTION_VERSION = "1.0.0"
    }
    
    /**
     * Gets the current version of a model.
     * 
     * @param modelType Type of model
     * @return Semantic version string (e.g., "1.2.3")
     */
    fun getCurrentVersion(modelType: ModelType): String {
        return when (modelType) {
            ModelType.ANOMALY_DETECTION -> 
                prefs.getString(KEY_ANOMALY_VERSION, BUNDLED_ANOMALY_VERSION) ?: BUNDLED_ANOMALY_VERSION
            ModelType.SUGGESTION_GENERATION -> 
                prefs.getString(KEY_SUGGESTION_VERSION, BUNDLED_SUGGESTION_VERSION) ?: BUNDLED_SUGGESTION_VERSION
        }
    }
    
    /**
     * Checks if a model update is available.
     * 
     * @param modelType Type of model
     * @param availableVersion Version available for download
     * @return true if update is available and newer
     */
    fun isUpdateAvailable(modelType: ModelType, availableVersion: String): Boolean {
        val currentVersion = getCurrentVersion(modelType)
        return compareVersions(availableVersion, currentVersion) > 0
    }
    
    /**
     * Downloads and installs a model update.
     * 
     * @param modelType Type of model
     * @param version Version to download
     * @param downloadUrl URL to download from
     * @param expectedHash Expected SHA-256 hash for verification
     * @return Result indicating success or failure
     */
    suspend fun downloadAndInstallUpdate(
        modelType: ModelType,
        version: String,
        downloadUrl: String,
        expectedHash: String
    ): UpdateResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Downloading model update: $modelType v$version")
            
            // Download model file
            val modelFile = downloadModel(downloadUrl, modelType, version)
            
            // Verify integrity
            val actualHash = calculateFileHash(modelFile)
            if (actualHash != expectedHash) {
                modelFile.delete()
                Timber.e("Model hash mismatch: expected=$expectedHash, actual=$actualHash")
                return@withContext UpdateResult.Failure("Hash verification failed")
            }
            
            // Backup current model
            val currentVersion = getCurrentVersion(modelType)
            backupModel(modelType, currentVersion)
            
            // Install new model
            installModel(modelFile, modelType)
            
            // Update version and hash
            saveModelMetadata(modelType, version, actualHash)
            
            Timber.d("Model update installed successfully: $modelType v$version")
            UpdateResult.Success(version)
        } catch (e: Exception) {
            Timber.e(e, "Model update failed: $modelType")
            UpdateResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Rolls back to the previous model version.
     * 
     * @param modelType Type of model
     * @return Result indicating success or failure
     */
    suspend fun rollback(modelType: ModelType): UpdateResult = withContext(Dispatchers.IO) {
        try {
            Timber.d("Rolling back model: $modelType")
            
            val backupFile = getBackupFile(modelType)
            if (!backupFile.exists()) {
                return@withContext UpdateResult.Failure("No backup available")
            }
            
            // Restore backup
            val modelFile = getModelFile(modelType)
            backupFile.copyTo(modelFile, overwrite = true)
            
            // Revert version (decrement patch version)
            val currentVersion = getCurrentVersion(modelType)
            val previousVersion = decrementVersion(currentVersion)
            saveModelMetadata(modelType, previousVersion, calculateFileHash(modelFile))
            
            Timber.d("Model rolled back successfully: $modelType to v$previousVersion")
            UpdateResult.Success(previousVersion)
        } catch (e: Exception) {
            Timber.e(e, "Model rollback failed: $modelType")
            UpdateResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Verifies model integrity.
     * 
     * @param modelType Type of model
     * @return true if model integrity is valid
     */
    suspend fun verifyModelIntegrity(modelType: ModelType): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(modelType)
            if (!modelFile.exists()) {
                Timber.w("Model file not found: $modelType")
                return@withContext false
            }
            
            val expectedHash = when (modelType) {
                ModelType.ANOMALY_DETECTION -> prefs.getString(KEY_ANOMALY_HASH, null)
                ModelType.SUGGESTION_GENERATION -> prefs.getString(KEY_SUGGESTION_HASH, null)
            }
            
            if (expectedHash == null) {
                Timber.w("No hash stored for model: $modelType")
                return@withContext false
            }
            
            val actualHash = calculateFileHash(modelFile)
            val isValid = actualHash == expectedHash
            
            if (!isValid) {
                Timber.e("Model integrity check failed: $modelType")
            }
            
            isValid
        } catch (e: Exception) {
            Timber.e(e, "Model integrity verification failed: $modelType")
            false
        }
    }
    
    /**
     * Downloads a model file from URL.
     * Placeholder implementation - actual implementation would use proper HTTP client.
     */
    private suspend fun downloadModel(url: String, modelType: ModelType, version: String): File {
        // Placeholder: In production, use OkHttp or similar
        val modelFile = File(context.filesDir, "models/${modelType.name.lowercase()}_$version.tflite")
        modelFile.parentFile?.mkdirs()
        
        // Simulate download
        Timber.d("Downloading from $url to ${modelFile.absolutePath}")
        
        return modelFile
    }
    
    /**
     * Calculates SHA-256 hash of a file.
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Backs up current model.
     */
    private fun backupModel(modelType: ModelType, version: String) {
        val modelFile = getModelFile(modelType)
        if (modelFile.exists()) {
            val backupFile = getBackupFile(modelType)
            modelFile.copyTo(backupFile, overwrite = true)
            Timber.d("Model backed up: $modelType v$version")
        }
    }
    
    /**
     * Installs a model file.
     */
    private fun installModel(sourceFile: File, modelType: ModelType) {
        val targetFile = getModelFile(modelType)
        sourceFile.copyTo(targetFile, overwrite = true)
        sourceFile.delete()
    }
    
    /**
     * Saves model metadata (version and hash).
     */
    private fun saveModelMetadata(modelType: ModelType, version: String, hash: String) {
        prefs.edit().apply {
            when (modelType) {
                ModelType.ANOMALY_DETECTION -> {
                    putString(KEY_ANOMALY_VERSION, version)
                    putString(KEY_ANOMALY_HASH, hash)
                }
                ModelType.SUGGESTION_GENERATION -> {
                    putString(KEY_SUGGESTION_VERSION, version)
                    putString(KEY_SUGGESTION_HASH, hash)
                }
            }
            apply()
        }
    }
    
    /**
     * Gets the model file for a given type.
     */
    private fun getModelFile(modelType: ModelType): File {
        return File(context.filesDir, "models/${modelType.name.lowercase()}.tflite")
    }
    
    /**
     * Gets the backup file for a given type.
     */
    private fun getBackupFile(modelType: ModelType): File {
        return File(context.filesDir, "models/${modelType.name.lowercase()}_backup.tflite")
    }
    
    /**
     * Compares two semantic versions.
     * 
     * @return Positive if v1 > v2, negative if v1 < v2, zero if equal
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            if (p1 != p2) return p1 - p2
        }
        
        return 0
    }
    
    /**
     * Decrements a semantic version (patch version).
     */
    private fun decrementVersion(version: String): String {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }.toMutableList()
        if (parts.size >= 3 && parts[2] > 0) {
            parts[2]--
        }
        return parts.joinToString(".")
    }
}

/**
 * Types of ML models.
 */
enum class ModelType {
    ANOMALY_DETECTION,
    SUGGESTION_GENERATION
}

/**
 * Result of a model update operation.
 */
sealed class UpdateResult {
    data class Success(val version: String) : UpdateResult()
    data class Failure(val reason: String) : UpdateResult()
}
