package com.healthtracker.presentation.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.core.error.CrashBoundary
import com.healthtracker.core.startup.StartupManager
import com.healthtracker.core.startup.StartupState
import com.healthtracker.data.local.HealthTrackerDatabase
import com.healthtracker.data.security.EncryptionService
import com.healthtracker.domain.usecase.OnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the splash screen that orchestrates safe app startup.
 * 
 * CRITICAL: This ensures app launch does NOT depend on network or ML.
 * 
 * Startup sequence:
 * 1. Show splash animation
 * 2. Initialize database (required)
 * 3. Initialize encryption (required)
 * 4. Check user session
 * 5. Navigate to appropriate screen
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val startupManager: StartupManager,
    private val crashBoundary: CrashBoundary,
    private val database: HealthTrackerDatabase,
    private val encryptionService: EncryptionService,
    private val onboardingUseCase: OnboardingUseCase
) : ViewModel() {
    
    /**
     * UI state for the splash screen.
     */
    sealed class SplashUiState {
        object Loading : SplashUiState()
        object NavigateToOnboarding : SplashUiState()
        object NavigateToDashboard : SplashUiState()
        data class Error(val message: String) : SplashUiState()
    }
    
    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()
    
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()
    
    private val _loadingMessage = MutableStateFlow("Starting up...")
    val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
    
    init {
        startInitialization()
    }
    
    /**
     * Starts the safe initialization sequence.
     */
    private fun startInitialization() {
        viewModelScope.launch(crashBoundary.exceptionHandler) {
            try {
                // Step 1: Initialize core services
                _loadingMessage.value = "Initializing..."
                _loadingProgress.value = 0.2f
                
                startupManager.initializeCoreServices(
                    initDatabase = { initializeDatabase() },
                    initEncryption = { initializeEncryption() }
                )
                
                _loadingProgress.value = 0.5f
                
                // Check if initialization succeeded
                when (startupManager.state.value) {
                    StartupState.FAILED -> {
                        val errors = startupManager.startupErrors.value
                        _uiState.value = SplashUiState.Error(
                            errors.firstOrNull() ?: "Initialization failed"
                        )
                        return@launch
                    }
                    else -> { /* Continue */ }
                }
                
                // Step 2: Check user session
                _loadingMessage.value = "Checking session..."
                _loadingProgress.value = 0.7f
                
                val hasCompletedOnboarding = checkOnboardingStatus()
                
                // Step 3: Minimum splash display time for branding
                _loadingProgress.value = 0.9f
                delay(500) // Minimum splash time
                
                _loadingProgress.value = 1.0f
                
                // Step 4: Navigate
                _uiState.value = if (hasCompletedOnboarding) {
                    SplashUiState.NavigateToDashboard
                } else {
                    SplashUiState.NavigateToOnboarding
                }
                
                Timber.d("Startup complete, navigating to ${_uiState.value}")
                
            } catch (e: Exception) {
                Timber.e(e, "Startup failed")
                crashBoundary.handleException(e, "SplashViewModel.startInitialization")
                _uiState.value = SplashUiState.Error(
                    "Failed to start app: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Initializes the Room database.
     * This is a required service - app cannot function without it.
     */
    private suspend fun initializeDatabase(): Boolean {
        return try {
            // Trigger database creation by accessing a DAO
            database.healthMetricsDao()
            Timber.d("Database initialized successfully")
            true
        } catch (e: Exception) {
            Timber.e(e, "Database initialization failed")
            false
        }
    }
    
    /**
     * Initializes the encryption service.
     * This is a required service - app cannot function without it.
     */
    private suspend fun initializeEncryption(): Boolean {
        return try {
            val available = encryptionService.isAvailable()
            Timber.d("Encryption service available: $available")
            available
        } catch (e: Exception) {
            Timber.e(e, "Encryption initialization failed")
            false
        }
    }
    
    /**
     * Checks if user has completed onboarding.
     * Uses local database, does NOT require network.
     */
    private suspend fun checkOnboardingStatus(): Boolean {
        return crashBoundary.runSafelyWithDefault(false, "checkOnboardingStatus") {
            onboardingUseCase.isOnboardingComplete()
        }
    }
    
    /**
     * Retries initialization after an error.
     */
    fun retry() {
        _uiState.value = SplashUiState.Loading
        _loadingProgress.value = 0f
        startInitialization()
    }
}
