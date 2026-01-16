package com.healthtracker.core.startup

import android.content.Context
import com.healthtracker.core.config.FeatureFlagManager
import com.healthtracker.data.worker.WorkManagerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startup state machine for safe app initialization.
 * 
 * CRITICAL: App launch must NOT depend on network or ML availability.
 * 
 * State transitions:
 * INITIALIZING → READY (all services available)
 * INITIALIZING → DEGRADED (some services unavailable)
 * DEGRADED → READY (services recovered)
 */
enum class StartupState {
    /** App is initializing, show splash screen */
    INITIALIZING,
    
    /** All services ready, full functionality available */
    READY,
    
    /** Some services unavailable, limited functionality */
    DEGRADED,
    
    /** Critical failure, show error screen */
    FAILED
}

/**
 * Tracks which services are available.
 */
data class ServiceAvailability(
    val firebase: Boolean = false,
    val healthConnect: Boolean = false,
    val mlModels: Boolean = false,
    val encryption: Boolean = false,
    val database: Boolean = false
) {
    val isFullyReady: Boolean
        get() = firebase && healthConnect && mlModels && encryption && database
    
    val isMinimallyReady: Boolean
        get() = database && encryption // Minimum for app to function
    
    val unavailableServices: List<String>
        get() = buildList {
            if (!firebase) add("Firebase")
            if (!healthConnect) add("Health Connect")
            if (!mlModels) add("ML Models")
            if (!encryption) add("Encryption")
            if (!database) add("Database")
        }
}

/**
 * Manages app startup sequence with lazy initialization.
 * 
 * Key principles:
 * 1. Database and encryption init first (required)
 * 2. Firebase init lazy (on first use)
 * 3. Health Connect init lazy (on first use)
 * 4. ML models init lazy (on first inference)
 * 5. WorkManager scheduled after core services ready
 */
@Singleton
class StartupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val featureFlagManager: FeatureFlagManager
) {
    
    private val _state = MutableStateFlow(StartupState.INITIALIZING)
    val state: StateFlow<StartupState> = _state.asStateFlow()
    
    private val _availability = MutableStateFlow(ServiceAvailability())
    val availability: StateFlow<ServiceAvailability> = _availability.asStateFlow()
    
    private val _startupErrors = MutableStateFlow<List<String>>(emptyList())
    val startupErrors: StateFlow<List<String>> = _startupErrors.asStateFlow()
    
    /**
     * Initializes core services required for app startup.
     * Called from Application.onCreate() or SplashViewModel.
     */
    suspend fun initializeCoreServices(
        initDatabase: suspend () -> Boolean,
        initEncryption: suspend () -> Boolean
    ) {
        Timber.d("Starting core service initialization")
        
        val errors = mutableListOf<String>()
        
        // Initialize database (required)
        val dbReady = try {
            initDatabase()
        } catch (e: Exception) {
            Timber.e(e, "Database initialization failed")
            errors.add("Database: ${e.message}")
            false
        }
        
        // Initialize encryption (required)
        val encryptionReady = try {
            initEncryption()
        } catch (e: Exception) {
            Timber.e(e, "Encryption initialization failed")
            errors.add("Encryption: ${e.message}")
            false
        }
        
        _availability.value = _availability.value.copy(
            database = dbReady,
            encryption = encryptionReady
        )
        
        _startupErrors.value = errors
        
        // Fetch feature flags (non-blocking)
        try {
            featureFlagManager.fetchAndActivate()
            Timber.d("Feature flags fetched successfully")
        } catch (e: Exception) {
            Timber.e(e, "Feature flag fetch failed (non-critical)")
        }
        
        // Determine startup state
        _state.value = when {
            dbReady && encryptionReady -> {
                // Initialize WorkManager for background tasks
                initializeBackgroundWork()
                StartupState.DEGRADED // Ready for lazy init
            }
            else -> StartupState.FAILED
        }
        
        Timber.d("Core initialization complete: state=${_state.value}")
    }
    
    /**
     * Initializes WorkManager for lifecycle-safe background operations.
     * Called after core services are ready.
     */
    private fun initializeBackgroundWork() {
        try {
            WorkManagerConfig.initializeWorkers(context)
            Timber.d("WorkManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "WorkManager initialization failed (non-critical)")
            // Non-critical failure - app can still function
        }
    }
    
    /**
     * Marks Firebase as initialized (called lazily on first use).
     */
    fun markFirebaseReady(ready: Boolean) {
        _availability.value = _availability.value.copy(firebase = ready)
        updateState()
        Timber.d("Firebase marked as ready=$ready")
    }
    
    /**
     * Marks Health Connect as initialized (called lazily on first use).
     */
    fun markHealthConnectReady(ready: Boolean) {
        _availability.value = _availability.value.copy(healthConnect = ready)
        updateState()
        Timber.d("Health Connect marked as ready=$ready")
    }
    
    /**
     * Marks ML models as initialized (called lazily on first inference).
     */
    fun markMLModelsReady(ready: Boolean) {
        _availability.value = _availability.value.copy(mlModels = ready)
        updateState()
        Timber.d("ML models marked as ready=$ready")
    }
    
    /**
     * Updates the overall startup state based on service availability.
     */
    private fun updateState() {
        val avail = _availability.value
        _state.value = when {
            avail.isFullyReady -> StartupState.READY
            avail.isMinimallyReady -> StartupState.DEGRADED
            else -> StartupState.FAILED
        }
    }
    
    /**
     * Checks if a specific feature is available.
     */
    fun isFeatureAvailable(feature: Feature): Boolean {
        val avail = _availability.value
        return when (feature) {
            Feature.HEALTH_DATA -> avail.healthConnect
            Feature.ML_INSIGHTS -> avail.mlModels
            Feature.CLOUD_SYNC -> avail.firebase
            Feature.OFFLINE_MODE -> avail.database
            Feature.SECURE_STORAGE -> avail.encryption
        }
    }
    
    /**
     * Gets a user-friendly message for degraded mode.
     */
    fun getDegradedModeMessage(): String? {
        val unavailable = _availability.value.unavailableServices
        return if (unavailable.isNotEmpty()) {
            "Some features are unavailable: ${unavailable.joinToString(", ")}"
        } else {
            null
        }
    }
}

/**
 * Features that can be checked for availability.
 */
enum class Feature {
    HEALTH_DATA,
    ML_INSIGHTS,
    CLOUD_SYNC,
    OFFLINE_MODE,
    SECURE_STORAGE
}
