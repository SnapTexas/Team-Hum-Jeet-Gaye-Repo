package com.healthtracker.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permission state machine for safe permission handling.
 * 
 * CRITICAL: This prevents crashes from missing permissions.
 * Rule: No permission → no repository call → no crash.
 * 
 * States:
 * - GRANTED: Permission fully granted
 * - DENIED: Permission denied (can request again)
 * - PARTIALLY_GRANTED: Some permissions granted (e.g., coarse but not fine location)
 * - PERMANENTLY_DENIED: User selected "Don't ask again"
 * - NOT_REQUESTED: Permission not yet requested
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    PARTIALLY_GRANTED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED
}

/**
 * Types of permissions used in the app.
 */
enum class PermissionType {
    HEALTH_CONNECT,
    LOCATION,
    CAMERA,
    NOTIFICATIONS,
    ACTIVITY_RECOGNITION,
    BODY_SENSORS
}

/**
 * Manages permission states and provides safe access checks.
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val _permissionStates = MutableStateFlow<Map<PermissionType, PermissionState>>(
        PermissionType.entries.associateWith { PermissionState.NOT_REQUESTED }
    )
    val permissionStates: StateFlow<Map<PermissionType, PermissionState>> = 
        _permissionStates.asStateFlow()
    
    /**
     * Checks and updates the state of a specific permission type.
     * 
     * @param type The permission type to check
     * @return Current permission state
     */
    fun checkPermission(type: PermissionType): PermissionState {
        val state = when (type) {
            PermissionType.HEALTH_CONNECT -> checkHealthConnectPermission()
            PermissionType.LOCATION -> checkLocationPermission()
            PermissionType.CAMERA -> checkCameraPermission()
            PermissionType.NOTIFICATIONS -> checkNotificationPermission()
            PermissionType.ACTIVITY_RECOGNITION -> checkActivityRecognitionPermission()
            PermissionType.BODY_SENSORS -> checkBodySensorsPermission()
        }
        
        updateState(type, state)
        return state
    }
    
    /**
     * Gets the current state without re-checking.
     */
    fun getState(type: PermissionType): PermissionState {
        return _permissionStates.value[type] ?: PermissionState.NOT_REQUESTED
    }
    
    /**
     * Checks if a permission is granted (safe to make repository calls).
     * 
     * CRITICAL: Always call this before any data access that requires permissions.
     */
    fun isGranted(type: PermissionType): Boolean {
        val state = checkPermission(type)
        return state == PermissionState.GRANTED
    }
    
    /**
     * Checks if permission can be requested (not permanently denied).
     */
    fun canRequest(type: PermissionType): Boolean {
        val state = getState(type)
        return state != PermissionState.PERMANENTLY_DENIED
    }
    
    /**
     * Updates permission state after a request result.
     * 
     * @param type Permission type
     * @param granted Whether permission was granted
     * @param shouldShowRationale Whether rationale should be shown (from Activity)
     */
    fun updateAfterRequest(
        type: PermissionType,
        granted: Boolean,
        shouldShowRationale: Boolean
    ) {
        val newState = when {
            granted -> PermissionState.GRANTED
            shouldShowRationale -> PermissionState.DENIED
            else -> PermissionState.PERMANENTLY_DENIED
        }
        
        updateState(type, newState)
        Timber.d("Permission $type updated to $newState")
    }
    
    /**
     * Gets all permissions that need to be requested.
     */
    fun getPermissionsToRequest(): List<PermissionType> {
        return PermissionType.entries.filter { type ->
            val state = checkPermission(type)
            state == PermissionState.NOT_REQUESTED || state == PermissionState.DENIED
        }
    }
    
    /**
     * Gets Android permission strings for a permission type.
     */
    fun getAndroidPermissions(type: PermissionType): List<String> {
        return when (type) {
            PermissionType.HEALTH_CONNECT -> emptyList() // Health Connect uses its own permission system
            PermissionType.LOCATION -> listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            PermissionType.CAMERA -> listOf(Manifest.permission.CAMERA)
            PermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
            PermissionType.ACTIVITY_RECOGNITION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOf(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                emptyList()
            }
            PermissionType.BODY_SENSORS -> listOf(Manifest.permission.BODY_SENSORS)
        }
    }
    
    // ============================================
    // Private permission check methods
    // ============================================
    
    private fun checkHealthConnectPermission(): PermissionState {
        // Health Connect permissions are checked via Health Connect SDK
        // This is a placeholder - actual check happens in HealthConnectService
        return PermissionState.NOT_REQUESTED
    }
    
    private fun checkLocationPermission(): PermissionState {
        val fineGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        return when {
            fineGranted -> PermissionState.GRANTED
            coarseGranted -> PermissionState.PARTIALLY_GRANTED
            else -> PermissionState.NOT_REQUESTED
        }
    }
    
    private fun checkCameraPermission(): PermissionState {
        return if (isPermissionGranted(Manifest.permission.CAMERA)) {
            PermissionState.GRANTED
        } else {
            PermissionState.NOT_REQUESTED
        }
    }
    
    private fun checkNotificationPermission(): PermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                PermissionState.GRANTED
            } else {
                PermissionState.NOT_REQUESTED
            }
        } else {
            // Notifications don't require runtime permission on older versions
            PermissionState.GRANTED
        }
    }
    
    private fun checkActivityRecognitionPermission(): PermissionState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (isPermissionGranted(Manifest.permission.ACTIVITY_RECOGNITION)) {
                PermissionState.GRANTED
            } else {
                PermissionState.NOT_REQUESTED
            }
        } else {
            PermissionState.GRANTED
        }
    }
    
    private fun checkBodySensorsPermission(): PermissionState {
        return if (isPermissionGranted(Manifest.permission.BODY_SENSORS)) {
            PermissionState.GRANTED
        } else {
            PermissionState.NOT_REQUESTED
        }
    }
    
    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
            PackageManager.PERMISSION_GRANTED
    }
    
    private fun updateState(type: PermissionType, state: PermissionState) {
        _permissionStates.value = _permissionStates.value.toMutableMap().apply {
            put(type, state)
        }
    }
}

/**
 * Extension function to safely execute code only if permission is granted.
 * 
 * Usage:
 * ```
 * permissionManager.withPermission(PermissionType.CAMERA) {
 *     // Camera code here - only runs if permission granted
 * }
 * ```
 */
inline fun <T> PermissionManager.withPermission(
    type: PermissionType,
    onDenied: () -> T? = { null },
    onGranted: () -> T
): T? {
    return if (isGranted(type)) {
        onGranted()
    } else {
        Timber.w("Permission $type not granted, skipping operation")
        onDenied()
    }
}

/**
 * Extension function for suspend functions with permission check.
 */
suspend fun <T> PermissionManager.withPermissionSuspend(
    type: PermissionType,
    onDenied: suspend () -> T? = { null },
    onGranted: suspend () -> T
): T? {
    return if (isGranted(type)) {
        onGranted()
    } else {
        Timber.w("Permission $type not granted, skipping operation")
        onDenied()
    }
}
