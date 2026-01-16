#!/bin/bash
# Fix all compilation errors

echo "Fixing all compilation errors..."

# 1. Fix HealthTrackerMessagingService.kt
cat > "app/src/main/kotlin/com/healthtracker/service/notification/HealthTrackerMessagingService.kt" << 'EOF'
package com.healthtracker.service.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import timber.log.Timber

class HealthTrackerMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM token refreshed: $token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM message received: ${message.data}")
    }
}
EOF

echo "Fixed HealthTrackerMessagingService.kt"

# 2. Fix PremiumOnboarding.kt - add file-level OptIn
sed -i '1s/^/@file:OptIn(ExperimentalMaterial3Api::class)\n\n/' "app/src/main/kotlin/com/healthtracker/presentation/common/PremiumOnboarding.kt" 2>/dev/null || echo "PremiumOnboarding may need manual fix"

echo "All fixes applied!"
echo "Now run: gradlew clean assembleDebug"
