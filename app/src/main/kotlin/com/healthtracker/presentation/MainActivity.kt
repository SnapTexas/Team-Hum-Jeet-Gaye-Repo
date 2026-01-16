package com.healthtracker.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.healthtracker.presentation.navigation.HealthTrackerNavHost
import com.healthtracker.presentation.theme.HealthTrackerTheme
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
}
