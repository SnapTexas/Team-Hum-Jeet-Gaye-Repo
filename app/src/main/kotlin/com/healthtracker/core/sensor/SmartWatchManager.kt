package com.healthtracker.core.sensor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SmartWatch Manager - Detects and syncs data from connected smartwatches
 * 
 * FEATURES:
 * - Auto-detect smartwatch connection
 * - Priority to smartwatch data over phone sensors
 * - Sync heart rate, steps, calories from watch
 * - Cache last synced data for disconnection scenarios
 * - Support for multiple watch brands (Xiaomi, Samsung, Fitbit, etc.)
 */
@Singleton
class SmartWatchManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "SmartWatchManager"
        private const val PREFS_NAME = "smartwatch_data"
        
        // Known smartwatch device names/prefixes
        private val WATCH_IDENTIFIERS = listOf(
            "Mi Band", "Mi Smart Band", "Xiaomi",
            "Galaxy Watch", "Galaxy Fit", "Samsung",
            "Fitbit", "Charge", "Versa", "Sense",
            "Apple Watch",
            "Amazfit", "Huami",
            "Garmin",
            "Fossil",
            "TicWatch",
            "Wear OS"
        )
        
        // Bluetooth Service UUIDs for health data
        private const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_MEASUREMENT_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
        private const val STEP_COUNT_SERVICE_UUID = "0000181c-0000-1000-8000-00805f9b34fb"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    // Connection state
    private val _isWatchConnected = MutableStateFlow(false)
    val isWatchConnected: StateFlow<Boolean> = _isWatchConnected.asStateFlow()
    
    private val _connectedWatchName = MutableStateFlow<String?>(null)
    val connectedWatchName: StateFlow<String?> = _connectedWatchName.asStateFlow()
    
    // Live data from watch
    private val _watchSteps = MutableStateFlow(0)
    val watchSteps: StateFlow<Int> = _watchSteps.asStateFlow()
    
    private val _watchHeartRate = MutableStateFlow(0)
    val watchHeartRate: StateFlow<Int> = _watchHeartRate.asStateFlow()
    
    private val _watchCalories = MutableStateFlow(0)
    val watchCalories: StateFlow<Int> = _watchCalories.asStateFlow()
    
    private val _watchDistance = MutableStateFlow(0.0)
    val watchDistance: StateFlow<Double> = _watchDistance.asStateFlow()
    
    // Last sync timestamp
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    
    /**
     * Smartwatch data model
     */
    data class WatchData(
        val steps: Int,
        val heartRate: Int,
        val calories: Int,
        val distance: Double,
        val timestamp: Long,
        val date: String
    )
    
    /**
     * Initialize and start watching for smartwatch connections
     */
    fun initialize() {
        Timber.d("$TAG: Initializing SmartWatch Manager")
        
        // Load cached data from today
        loadCachedDataForToday()
        
        // Check if any watch is already connected
        checkExistingConnections()
        
        // Register Bluetooth state receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothReceiver, filter)
        
        Timber.d("$TAG: SmartWatch Manager initialized")
    }
    
    /**
     * Check for existing Bluetooth connections
     */
    private fun checkExistingConnections() {
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                if (isSmartWatch(device)) {
                    Timber.d("$TAG: Found paired smartwatch: ${device.name}")
                    // Try to connect
                    connectToWatch(device)
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "$TAG: Bluetooth permission not granted")
        }
    }
    
    /**
     * Check if device is a smartwatch
     */
    private fun isSmartWatch(device: BluetoothDevice): Boolean {
        val deviceName = device.name ?: return false
        return WATCH_IDENTIFIERS.any { deviceName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Connect to smartwatch via Bluetooth LE
     */
    private fun connectToWatch(device: BluetoothDevice) {
        try {
            Timber.d("$TAG: Connecting to ${device.name}...")
            connectedDevice = device
            
            bluetoothGatt = device.connectGatt(context, true, gattCallback)
            
        } catch (e: SecurityException) {
            Timber.e(e, "$TAG: Failed to connect to watch")
        }
    }
    
    /**
     * Bluetooth GATT callback for smartwatch communication
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.d("$TAG: Watch connected!")
                    _isWatchConnected.value = true
                    _connectedWatchName.value = gatt?.device?.name
                    
                    // Discover services
                    try {
                        gatt?.discoverServices()
                    } catch (e: SecurityException) {
                        Timber.e(e, "$TAG: Permission error")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.d("$TAG: Watch disconnected")
                    _isWatchConnected.value = false
                    
                    // Save last known data for today
                    saveCachedDataForToday()
                    
                    // Try to reconnect after delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        connectedDevice?.let { connectToWatch(it) }
                    }, 5000)
                }
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("$TAG: Services discovered")
                
                // Subscribe to heart rate notifications
                subscribeToHeartRate(gatt)
                
                // Subscribe to step count notifications
                subscribeToStepCount(gatt)
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let {
                when (it.uuid.toString()) {
                    HEART_RATE_MEASUREMENT_UUID -> {
                        val heartRate = parseHeartRate(it)
                        _watchHeartRate.value = heartRate
                        Timber.d("$TAG: Heart rate: $heartRate BPM")
                    }
                    // Add more characteristic handlers
                }
                
                // Update last sync time
                _lastSyncTime.value = System.currentTimeMillis()
                
                // Save to cache
                saveCachedDataForToday()
            }
        }
    }
    
    /**
     * Subscribe to heart rate notifications
     */
    private fun subscribeToHeartRate(gatt: BluetoothGatt?) {
        try {
            val service = gatt?.getService(java.util.UUID.fromString(HEART_RATE_SERVICE_UUID))
            val characteristic = service?.getCharacteristic(
                java.util.UUID.fromString(HEART_RATE_MEASUREMENT_UUID)
            )
            
            characteristic?.let {
                gatt.setCharacteristicNotification(it, true)
                
                // Enable notifications on the device
                val descriptor = it.getDescriptor(
                    java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                
                Timber.d("$TAG: Subscribed to heart rate")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to subscribe to heart rate")
        }
    }
    
    /**
     * Subscribe to step count notifications
     */
    private fun subscribeToStepCount(gatt: BluetoothGatt?) {
        // Similar to heart rate subscription
        // Implementation depends on watch manufacturer
        Timber.d("$TAG: Step count subscription (manufacturer-specific)")
    }
    
    /**
     * Parse heart rate from characteristic
     */
    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int {
        val flag = characteristic.properties
        val format = if (flag and 0x01 != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        return characteristic.getIntValue(format, 1) ?: 0
    }
    
    /**
     * Bluetooth state receiver
     */
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (isSmartWatch(it)) {
                            Timber.d("$TAG: Smartwatch connected: ${it.name}")
                            connectToWatch(it)
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (isSmartWatch(it)) {
                            Timber.d("$TAG: Smartwatch disconnected: ${it.name}")
                            _isWatchConnected.value = false
                            saveCachedDataForToday()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get current watch data (prioritized over phone sensors)
     */
    fun getCurrentWatchData(): WatchData? {
        if (!_isWatchConnected.value) {
            // Return cached data if available for today
            return getCachedDataForToday()
        }
        
        return WatchData(
            steps = _watchSteps.value,
            heartRate = _watchHeartRate.value,
            calories = _watchCalories.value,
            distance = _watchDistance.value,
            timestamp = _lastSyncTime.value,
            date = LocalDate.now().toString()
        )
    }
    
    /**
     * Save cached data for today
     */
    private fun saveCachedDataForToday() {
        val today = LocalDate.now().toString()
        prefs.edit().apply {
            putInt("steps_$today", _watchSteps.value)
            putInt("heart_rate_$today", _watchHeartRate.value)
            putInt("calories_$today", _watchCalories.value)
            putFloat("distance_$today", _watchDistance.value.toFloat())
            putLong("timestamp_$today", _lastSyncTime.value)
            putString("watch_name_$today", _connectedWatchName.value)
            apply()
        }
        Timber.d("$TAG: Cached watch data for $today")
    }
    
    /**
     * Load cached data for today
     */
    private fun loadCachedDataForToday() {
        val today = LocalDate.now().toString()
        
        val steps = prefs.getInt("steps_$today", 0)
        val heartRate = prefs.getInt("heart_rate_$today", 0)
        val calories = prefs.getInt("calories_$today", 0)
        val distance = prefs.getFloat("distance_$today", 0f).toDouble()
        val timestamp = prefs.getLong("timestamp_$today", 0L)
        val watchName = prefs.getString("watch_name_$today", null)
        
        if (steps > 0 || heartRate > 0) {
            _watchSteps.value = steps
            _watchHeartRate.value = heartRate
            _watchCalories.value = calories
            _watchDistance.value = distance
            _lastSyncTime.value = timestamp
            _connectedWatchName.value = watchName
            
            Timber.d("$TAG: Loaded cached watch data for $today: $steps steps, $heartRate BPM")
        }
    }
    
    /**
     * Get cached data for today
     */
    private fun getCachedDataForToday(): WatchData? {
        val today = LocalDate.now().toString()
        
        val steps = prefs.getInt("steps_$today", 0)
        val heartRate = prefs.getInt("heart_rate_$today", 0)
        val calories = prefs.getInt("calories_$today", 0)
        val distance = prefs.getFloat("distance_$today", 0f).toDouble()
        val timestamp = prefs.getLong("timestamp_$today", 0L)
        
        return if (steps > 0 || heartRate > 0) {
            WatchData(steps, heartRate, calories, distance, timestamp, today)
        } else {
            null
        }
    }
    
    /**
     * Manually sync data from watch
     */
    fun syncNow() {
        if (_isWatchConnected.value) {
            Timber.d("$TAG: Manual sync requested")
            // Trigger data read from watch
            bluetoothGatt?.let { gatt ->
                // Read characteristics
                try {
                    val service = gatt.getService(java.util.UUID.fromString(HEART_RATE_SERVICE_UUID))
                    val characteristic = service?.getCharacteristic(
                        java.util.UUID.fromString(HEART_RATE_MEASUREMENT_UUID)
                    )
                    characteristic?.let { gatt.readCharacteristic(it) }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Sync failed")
                }
            }
        }
    }
    
    /**
     * Check if watch data should be prioritized
     */
    fun shouldUseWatchData(): Boolean {
        // Use watch data if:
        // 1. Watch is currently connected, OR
        // 2. We have cached data from today
        return _isWatchConnected.value || getCachedDataForToday() != null
    }
    
    /**
     * Cleanup
     */
    fun shutdown() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Shutdown error")
        }
    }
}
