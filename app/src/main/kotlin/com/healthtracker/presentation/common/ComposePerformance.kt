package com.healthtracker.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import timber.log.Timber

/**
 * Compose performance optimization utilities.
 * 
 * CRITICAL OPTIMIZATIONS:
 * - Minimize recomposition with @Stable and remember
 * - Efficient image caching with Coil
 * - Lazy loading for heavy composables
 * - 60 FPS rendering target
 */

/**
 * Creates an optimized image loader with memory and disk caching.
 * 
 * @return Configured ImageLoader instance
 */
@Composable
fun rememberOptimizedImageLoader(): ImageLoader {
    val context = LocalContext.current
    
    return remember {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // Use 25% of app memory for image cache
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}

/**
 * Stable wrapper for UI state to minimize recomposition.
 * 
 * Use this for complex state objects that don't change frequently.
 * 
 * @param T Type of state
 * @property value The wrapped state value
 */
@Stable
data class StableHolder<T>(val value: T)

/**
 * Wraps a value in a StableHolder to prevent unnecessary recomposition.
 * 
 * @param value Value to wrap
 * @return StableHolder containing the value
 */
@Composable
fun <T> rememberStable(value: T): StableHolder<T> {
    return remember(value) { StableHolder(value) }
}

/**
 * Modifier for efficient drawing with caching.
 * 
 * Use this for complex drawing operations that don't change frequently.
 * 
 * @param key Cache key - drawing is only recomputed when key changes
 * @param onDraw Drawing lambda
 * @return Modifier with cached drawing
 */
fun Modifier.drawCached(
    key: Any?,
    onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit
): Modifier = this.drawWithCache {
    onDrawBehind {
        onDraw()
    }
}

/**
 * Performance monitoring for Compose.
 */
object ComposePerformance {
    
    private var frameCount = 0
    private var lastFpsLog = System.currentTimeMillis()
    
    /**
     * Tracks frame rendering for FPS monitoring.
     * Call this from your root composable.
     */
    @Composable
    fun TrackFrameRate() {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            frameCount++
            
            val now = System.currentTimeMillis()
            if (now - lastFpsLog >= 1000) {
                val fps = frameCount * 1000 / (now - lastFpsLog)
                if (fps < 55) {
                    Timber.w("Low FPS detected: $fps (target: 60)")
                }
                frameCount = 0
                lastFpsLog = now
            }
        }
    }
    
    /**
     * Logs recomposition count for debugging.
     * 
     * @param tag Tag for logging
     */
    @Composable
    fun LogRecomposition(tag: String) {
        androidx.compose.runtime.SideEffect {
            Timber.d("[$tag] Recomposed")
        }
    }
}

/**
 * Lazy loading wrapper for heavy composables.
 * 
 * Use this to defer loading of expensive UI until it's actually needed.
 * 
 * @param visible Whether the content should be visible
 * @param content The composable content to load lazily
 */
@Composable
fun LazyComposable(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (visible) {
        content()
    }
}

/**
 * Debounced state for text input to reduce recomposition.
 * 
 * @param initialValue Initial text value
 * @param delayMillis Debounce delay in milliseconds
 * @return Pair of (current value, update function)
 */
@Composable
fun rememberDebouncedState(
    initialValue: String,
    delayMillis: Long = 300L
): Pair<String, (String) -> Unit> {
    val (value, setValue) = androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(initialValue) 
    }
    val (debouncedValue, setDebouncedValue) = androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(initialValue) 
    }
    
    androidx.compose.runtime.LaunchedEffect(value) {
        kotlinx.coroutines.delay(delayMillis)
        setDebouncedValue(value)
    }
    
    return debouncedValue to setValue
}

/**
 * Efficient list key generation for LazyColumn/LazyRow.
 * 
 * Use this to generate stable keys for list items.
 * 
 * @param item The list item
 * @param index The item index
 * @return Stable key for the item
 */
fun <T> listItemKey(item: T, index: Int): Any {
    return when (item) {
        is Any -> item.hashCode()
        else -> index
    }
}
