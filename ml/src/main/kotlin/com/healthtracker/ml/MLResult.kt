package com.healthtracker.ml

/**
 * Sealed class representing the result of an ML inference operation.
 * 
 * All ML operations should return this type to ensure graceful
 * fallback when ML fails. This is a CRITICAL safety wrapper.
 * 
 * Rule: ML failure = graceful fallback, never block UI or background job.
 * 
 * @param T The type of successful result data
 */
sealed class MLResult<out T> {
    
    /**
     * Successful ML inference result.
     * 
     * @property data The inference result data
     */
    data class Success<T>(val data: T) : MLResult<T>()
    
    /**
     * ML inference failed, use fallback behavior.
     * 
     * @property reason Human-readable reason for fallback
     * @property exception Optional underlying exception
     */
    data class Fallback(
        val reason: String,
        val exception: Throwable? = null
    ) : MLResult<Nothing>()
    
    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is a Fallback result.
     */
    val isFallback: Boolean get() = this is Fallback
    
    /**
     * Returns the data if Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Fallback -> null
    }
    
    /**
     * Returns the data if Success, default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Fallback -> default
    }
    
    /**
     * Maps the success value to a new type.
     */
    inline fun <R> map(transform: (T) -> R): MLResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Fallback -> this
    }
    
    /**
     * Executes the given block if this is a Success.
     */
    inline fun onSuccess(action: (T) -> Unit): MLResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes the given block if this is a Fallback.
     */
    inline fun onFallback(action: (String, Throwable?) -> Unit): MLResult<T> {
        if (this is Fallback) action(reason, exception)
        return this
    }
    
    companion object {
        /**
         * Creates a Success result.
         */
        fun <T> success(data: T): MLResult<T> = Success(data)
        
        /**
         * Creates a Fallback result.
         */
        fun fallback(reason: String, exception: Throwable? = null): MLResult<Nothing> = 
            Fallback(reason, exception)
        
        /**
         * Wraps a suspending block in ML safety wrapper with timeout.
         * 
         * @param timeoutMs Maximum time to wait for inference (default 200ms)
         * @param fallbackReason Reason to use if operation fails
         * @param block The ML operation to execute
         * @return MLResult with success data or fallback
         */
        suspend inline fun <T> runWithTimeout(
            timeoutMs: Long = 200L,
            fallbackReason: String = "ML inference timed out",
            crossinline block: suspend () -> T
        ): MLResult<T> {
            return try {
                kotlinx.coroutines.withTimeout(timeoutMs) {
                    Success(block())
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Fallback(fallbackReason, e)
            } catch (e: Exception) {
                Fallback("ML inference failed: ${e.message}", e)
            }
        }
        
        /**
         * Wraps a block in ML safety wrapper.
         * 
         * @param fallbackReason Reason to use if operation fails
         * @param block The ML operation to execute
         * @return MLResult with success data or fallback
         */
        inline fun <T> runCatching(
            fallbackReason: String = "ML inference failed",
            block: () -> T
        ): MLResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Fallback("$fallbackReason: ${e.message}", e)
            }
        }
    }
}
