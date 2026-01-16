# Feature Flags Implementation

## Overview

The Smart Health Tracker app implements Firebase Remote Config for feature flags, allowing features to be enabled or disabled remotely without requiring an app update.

## Purpose

Feature flags provide:
- **Emergency Kill Switch**: Disable problematic features instantly
- **Gradual Rollout**: Enable features for a subset of users
- **A/B Testing**: Test different feature configurations
- **Graceful Degradation**: Handle feature failures without crashes

## Available Feature Flags

### 1. `enable_ml` (Boolean)
- **Default**: `true`
- **Controls**: All ML-related features
- **Impact**: 
  - Anomaly detection
  - AI suggestions
  - Food classification
- **Fallback**: Rule-based algorithms

### 2. `enable_avatar` (Boolean)
- **Default**: `true`
- **Controls**: AI Avatar feature
- **Impact**: Avatar screen and overlay service
- **Fallback**: Feature unavailable message

### 3. `enable_cv_food` (Boolean)
- **Default**: `true`
- **Controls**: Computer vision food tracking
- **Impact**: Camera-based food classification
- **Fallback**: Manual food entry

### 4. `enable_anomaly_detection` (Boolean)
- **Default**: `true`
- **Controls**: Health anomaly detection
- **Impact**: Anomaly alerts and notifications
- **Fallback**: No anomaly detection

## Implementation

### FeatureFlagManager

Located at: `app/src/main/kotlin/com/healthtracker/core/config/FeatureFlagManager.kt`

```kotlin
class FeatureFlagManager @Inject constructor() {
    fun isMlEnabled(): Boolean
    fun isAvatarEnabled(): Boolean
    fun isCvFoodEnabled(): Boolean
    fun isAnomalyDetectionEnabled(): Boolean
}
```

### Integration Points

1. **StartupManager**: Fetches flags on app startup
2. **MLModule**: Gates ML service with feature flags
3. **ViewModels**: Check flags before executing features
4. **UI Components**: Show unavailable messages when disabled

## Usage in Code

### Checking Feature Flags

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val featureFlagManager: FeatureFlagManager
) : ViewModel() {
    
    fun performMLOperation() {
        if (!featureFlagManager.isMlEnabled()) {
            // Show fallback UI
            return
        }
        // Proceed with ML operation
    }
}
```

### Gating ML Services

```kotlin
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
```

## Firebase Console Configuration

### Setting Up Remote Config

1. Go to Firebase Console → Remote Config
2. Add parameters:
   - `enable_ml`: Boolean, default `true`
   - `enable_avatar`: Boolean, default `true`
   - `enable_cv_food`: Boolean, default `true`
   - `enable_anomaly_detection`: Boolean, default `true`

### Creating Conditions

Example: Enable ML only for beta users
```
Condition: User in audience "beta_testers"
Parameter: enable_ml = true
Default: enable_ml = false
```

### Emergency Disable

To disable a feature immediately:
1. Go to Firebase Console → Remote Config
2. Set parameter to `false`
3. Click "Publish changes"
4. Changes propagate within 1 hour (or force fetch in app)

## Testing

### Local Testing

Default values are set in code, so features work without Firebase:

```kotlin
private fun getDefaultFlags(): Map<String, Any> {
    return mapOf(
        KEY_ENABLE_ML to true,
        KEY_ENABLE_AVATAR to true,
        KEY_ENABLE_CV_FOOD to true,
        KEY_ENABLE_ANOMALY_DETECTION to true
    )
}
```

### Testing Disabled Features

1. Modify default values in `FeatureFlagManager`
2. Or use Firebase Remote Config debug mode
3. Verify fallback UI appears
4. Verify no crashes occur

## Monitoring

### Logging

Feature flag states are logged:
```
D/StartupManager: Feature flags fetched successfully
D/FeatureGatedMLService: ML features disabled via feature flag
```

### Analytics

Track feature flag usage:
- Log when features are disabled
- Track fallback usage
- Monitor user impact

## Best Practices

1. **Always Provide Fallbacks**: Never crash when a feature is disabled
2. **Clear User Communication**: Show why a feature is unavailable
3. **Test Disabled States**: Verify app works with all flags off
4. **Monitor Impact**: Track metrics when disabling features
5. **Document Changes**: Log why flags were changed

## Troubleshooting

### Feature Not Disabling

- Check Firebase Console for published changes
- Verify fetch interval (default: 1 hour)
- Force fetch with `fetchAndActivate()`
- Check logs for fetch errors

### App Crashes When Disabled

- Review fallback logic
- Ensure null checks are in place
- Test with all flags disabled
- Check for missing feature checks

## Future Enhancements

1. **Per-User Flags**: Enable features for specific users
2. **Percentage Rollouts**: Enable for X% of users
3. **A/B Testing**: Test different implementations
4. **Feature Analytics**: Track feature usage and performance
5. **Automatic Rollback**: Disable features on high error rates
