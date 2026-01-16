package com.healthtracker

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Main Application class for Health Tracker.
 * 
 * Initializes:
 * - Hilt dependency injection
 * - WorkManager with Hilt support
 * - Timber logging (debug builds only)
 * - StrictMode for detecting main thread violations (debug builds only)
 */
@HiltAndroidApp
class HealthTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            setupStrictMode()
        }
        
        Timber.d("HealthTrackerApplication initialized")
    }

    /**
     * Provides WorkManager configuration with Hilt worker factory.
     * This enables dependency injection in WorkManager workers.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG 
                else android.util.Log.ERROR
            )
            .build()

    /**
     * Sets up StrictMode for debug builds to detect:
     * - Disk reads/writes on main thread
     * - Network operations on main thread
     * - Resource mismatches
     * 
     * This helps identify performance issues during development.
     */
    private fun setupStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
        
        Timber.d("StrictMode enabled for debug build")
    }
}
