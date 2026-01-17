package com.healthtracker.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.healthtracker.presentation.navigation.HealthTrackerNavHost
import com.healthtracker.presentation.theme.HealthTrackerTheme
import com.healthtracker.service.avatar.AvatarOverlayService
import com.healthtracker.service.step.StepCounterService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point Activity for the Health Tracker app.
 * 
 * Uses Jetpack Compose for the entire UI with:
 * - Edge-to-edge display
 * - Material 3 theming
 * - Navigation component for screen management
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Request permissions and start step counter service
        requestPermissionsAndStartService()
        
        setContent {
            HealthTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthTrackerNavHost()
                }
            }
        }
    }
    
    private fun requestPermissionsAndStartService() {
        val permissionsNeeded = mutableListOf<String>()
        
        // Activity Recognition permission (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Microphone permission for voice commands
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 100)
        } else {
            // Permissions already granted, start services
            startServices()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            // Start services regardless of permission result
            // Services will handle missing permissions gracefully
            startServices()
        }
    }
    
    private fun startServices() {
        // Start step counter service
        StepCounterService.startService(this)
        
        // Start avatar overlay service if permission granted
        // Check if user has enabled avatar in settings
        val prefs = getSharedPreferences("avatar_settings", MODE_PRIVATE)
        val avatarEnabled = prefs.getBoolean("avatar_enabled", true) // Default enabled
        
        if (avatarEnabled && AvatarOverlayService.hasOverlayPermission(this)) {
            AvatarOverlayService.start(this)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't stop avatar service on activity destroy - it should keep running
    }
}
