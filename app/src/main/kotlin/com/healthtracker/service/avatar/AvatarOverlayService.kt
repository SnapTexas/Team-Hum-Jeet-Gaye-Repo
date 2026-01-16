package com.healthtracker.service.avatar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.healthtracker.R
import com.healthtracker.domain.model.AvatarState
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that manages the floating AI Avatar overlay.
 * 
 * The avatar appears as a floating button that can be:
 * - Dragged around the screen
 * - Double-tapped to expand
 * - Single-tapped to minimize when expanded
 */
@AndroidEntryPoint
class AvatarOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isExpanded by mutableStateOf(false)
    private var avatarState by mutableStateOf(AvatarState.MINIMIZED)
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Lifecycle management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    
    // Touch handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var tapCount = 0
    
    companion object {
        private const val CHANNEL_ID = "avatar_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        private const val DOUBLE_TAP_TIMEOUT = 300L
        
        const val ACTION_SHOW = "com.healthtracker.avatar.SHOW"
        const val ACTION_HIDE = "com.healthtracker.avatar.HIDE"
        const val ACTION_TOGGLE = "com.healthtracker.avatar.TOGGLE"
        
        /**
         * Checks if the app has overlay permission.
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
        
        /**
         * Opens the overlay permission settings.
         */
        fun requestOverlayPermission(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_TOGGLE -> toggleOverlay()
            else -> showOverlay()
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        hideOverlay()
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
    
    private fun showOverlay() {
        if (!hasOverlayPermission(this)) {
            stopSelf()
            return
        }
        
        if (overlayView != null) return
        
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        
        val params = createLayoutParams()
        
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AvatarOverlayService)
            setViewTreeSavedStateRegistryOwner(this@AvatarOverlayService)
            
            setContent {
                AvatarOverlayContent(
                    isExpanded = isExpanded,
                    avatarState = avatarState,
                    onExpandToggle = { toggleExpanded() },
                    onQuerySubmit = { query -> handleQuery(query) },
                    onDismiss = { hideOverlay() }
                )
            }
            
            setOnTouchListener(createTouchListener(params))
        }
        
        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            overlayView = null
        }
    }
    
    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
            overlayView = null
        }
        isExpanded = false
        avatarState = AvatarState.MINIMIZED
    }
    
    private fun toggleOverlay() {
        if (overlayView != null) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }
    
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        avatarState = if (isExpanded) AvatarState.EXPANDED else AvatarState.MINIMIZED
        
        // Update layout params for expanded/minimized state
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (isExpanded) {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.x = 0
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.TOP or Gravity.START
            }
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                // Ignore layout update errors
            }
        }
    }
    
    private fun handleQuery(query: String) {
        avatarState = AvatarState.PROCESSING
        
        serviceScope.launch {
            // Simulate processing delay
            delay(500)
            avatarState = AvatarState.RESPONDING
            delay(2000)
            avatarState = AvatarState.EXPANDED
        }
    }
    
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }
    }
    
    private fun createTouchListener(params: WindowManager.LayoutParams): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            if (isExpanded) {
                // Don't handle drag when expanded
                return@OnTouchListener false
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = kotlin.math.abs(event.rawX - initialTouchX)
                    val deltaY = kotlin.math.abs(event.rawY - initialTouchY)
                    
                    // Check if it was a tap (not a drag)
                    if (deltaX < 10 && deltaY < 10) {
                        handleTap()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun handleTap() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
            tapCount++
            if (tapCount >= 2) {
                // Double tap detected
                toggleExpanded()
                tapCount = 0
            }
        } else {
            tapCount = 1
        }
        
        lastTapTime = currentTime
        
        // Reset tap count after timeout
        serviceScope.launch {
            delay(DOUBLE_TAP_TIMEOUT)
            if (System.currentTimeMillis() - lastTapTime >= DOUBLE_TAP_TIMEOUT) {
                tapCount = 0
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Avatar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the AI Avatar is active"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val hideIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AvatarOverlayService::class.java).apply {
                action = ACTION_HIDE
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Health Assistant Active")
            .setContentText("Double-tap the avatar to interact")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "Hide", hideIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Composable content for the avatar overlay.
 * This is a placeholder - the actual UI will be in a separate file.
 */
@Composable
private fun AvatarOverlayContent(
    isExpanded: Boolean,
    avatarState: AvatarState,
    onExpandToggle: () -> Unit,
    onQuerySubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Placeholder - actual implementation in AvatarOverlayUI.kt
}
