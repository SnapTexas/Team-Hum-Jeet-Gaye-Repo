package com.healthtracker.domain.model

/**
 * A generic class that holds a value or an error.
 * Used throughout the domain layer for operation results.
 * 
 * @param T The type of the success value
 */
sealed class Result<out T> {
    /**
     * Represents a successful operation with data.
     * 
     * @property data The successful result data
     */
    data class Success<out T>(val data: T) : Result<T>()
    
    /**
     * Represents a failed operation with an exception.
     * 
     * @property exception The exception that caused the failure
     */
    data class Error(val exception: AppException) : Result<Nothing>()
    
    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is an Error result.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns the data if Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    /**
     * Returns the data if Success, throws exception otherwise.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
    
    /**
     * Returns the data if Success, default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }
    
    /**
     * Maps the success value to a new type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    /**
     * Flat maps the success value to a new Result.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
    
    /**
     * Executes the given block if this is a Success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes the given block if this is an Error.
     */
    inline fun onError(action: (AppException) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
}

/**
 * Base exception class for all app-specific exceptions.
 * 
 * @property message Human-readable error message
 * @property cause Underlying cause of the exception
 */
sealed class AppException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Network-related errors (connectivity, timeout, etc.)
     */
    class NetworkException(
        message: String,
        cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Permission-related errors.
     * 
     * @property permission The permission that was denied
     */
    class PermissionException(
        val permission: String
    ) : AppException("Permission denied: $permission")
    
    /**
     * Input validation errors.
     * 
     * @property field The field that failed validation
     * @property reason The reason for validation failure
     */
    class ValidationException(
        val field: String,
        val reason: String
    ) : AppException("$field: $reason")
    
    /**
     * Machine learning inference errors.
     */
    class MLException(
        message: String,
        cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Storage-related errors (disk full, encryption failure, etc.)
     */
    class StorageException(
        message: String,
        cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Authentication errors (token expired, invalid credentials, etc.)
     */
    class AuthException(
        message: String
    ) : AppException(message)
    
    /**
     * Encryption/decryption errors.
     */
    class EncryptionException(
        message: String,
        cause: Throwable? = null
    ) : AppException(message, cause)
    
    /**
     * Generic unknown errors.
     */
    class UnknownException(
        message: String,
        cause: Throwable? = null
    ) : AppException(message, cause)
}
