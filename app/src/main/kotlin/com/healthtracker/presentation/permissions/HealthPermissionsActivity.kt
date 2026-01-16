package com.healthtracker.presentation.permissions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.healthtracker.R
import com.healthtracker.presentation.theme.HealthTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity for displaying Health Connect permission rationale.
 * 
 * This activity is launched when:
 * - User clicks "Learn more" in Health Connect permission dialog
 * - System needs to show permission usage explanation
 * 
 * Required by Health Connect API for privacy compliance.
 */
@AndroidEntryPoint
class HealthPermissionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            HealthTrackerTheme {
                HealthPermissionsScreen(
                    onDismiss = { finish() }
                )
            }
        }
    }
}

/**
 * Screen explaining why Health Tracker needs health data permissions.
 * 
 * @param onDismiss Callback when user dismisses the screen
 */
@Composable
fun HealthPermissionsScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.permission_health_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.permission_health_rationale),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Health Tracker uses your health data to:\n\n" +
                    "• Track daily steps and activity\n" +
                    "• Monitor sleep patterns\n" +
                    "• Analyze heart rate trends\n" +
                    "• Calculate calories burned\n" +
                    "• Provide personalized wellness insights\n\n" +
                    "Your data is encrypted and stored securely. " +
                    "You can revoke permissions at any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.done))
            }
        }
    }
}
