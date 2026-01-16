package com.healthtracker.data.security

import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Request signing for replay attack protection.
 * 
 * Implements HMAC-SHA256 signing with timestamp and nonce validation.
 * 
 * Security Features:
 * - Timestamp validation (±5 min window)
 * - Nonce-based replay prevention
 * - HMAC-SHA256 signature verification
 * 
 * Usage:
 * 1. Client signs request with timestamp + nonce
 * 2. Server validates signature and checks timestamp freshness
 * 3. Server stores nonce to prevent replay
 */

/**
 * Represents a sync request to be signed.
 */
data class SyncRequest(
    val userId: String,
    val data: String,
    val endpoint: String
)

/**
 * Represents a signed request with security metadata.
 */
data class SignedRequest(
    val request: SyncRequest,
    val timestamp: Long,
    val nonce: String,
    val signature: String
)

/**
 * Interface for request signing operations.
 */
interface RequestSigner {
    /**
     * Signs a request with timestamp and nonce.
     * @param request The request to sign
     * @param timestamp Unix timestamp in seconds
     * @param nonce Unique random string
     * @return Signed request with signature
     */
    fun signRequest(request: SyncRequest, timestamp: Long, nonce: String): SignedRequest
    
    /**
     * Verifies a signed request signature.
     * @param signedRequest The signed request to verify
     * @param secretKey The shared secret key
     * @return true if signature is valid
     */
    fun verifySignature(signedRequest: SignedRequest, secretKey: String): Boolean
    
    /**
     * Checks if a timestamp is within acceptable window.
     * @param timestamp Unix timestamp in seconds
     * @param windowSeconds Acceptable time window (default: 300 = 5 min)
     * @return true if timestamp is fresh
     */
    fun isTimestampValid(timestamp: Long, windowSeconds: Long = 300): Boolean
    
    /**
     * Generates a cryptographically secure nonce.
     * @return Random nonce string
     */
    fun generateNonce(): String
}

/**
 * Implementation of request signing using HMAC-SHA256.
 */
@Singleton
class RequestSignerImpl @Inject constructor() : RequestSigner {
    
    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val NONCE_LENGTH = 32
    }
    
    private val secureRandom = SecureRandom()
    
    override fun signRequest(request: SyncRequest, timestamp: Long, nonce: String): SignedRequest {
        // Create message to sign: userId|data|endpoint|timestamp|nonce
        val message = buildMessage(request, timestamp, nonce)
        
        // For now, use a placeholder secret key
        // In production, this should be retrieved from secure storage or server
        val secretKey = "PLACEHOLDER_SECRET_KEY_REPLACE_IN_PRODUCTION"
        
        val signature = computeHmac(message, secretKey)
        
        return SignedRequest(
            request = request,
            timestamp = timestamp,
            nonce = nonce,
            signature = signature
        )
    }
    
    override fun verifySignature(signedRequest: SignedRequest, secretKey: String): Boolean {
        return try {
            val message = buildMessage(signedRequest.request, signedRequest.timestamp, signedRequest.nonce)
            val expectedSignature = computeHmac(message, secretKey)
            
            // Use constant-time comparison to prevent timing attacks
            MessageDigest.isEqual(
                expectedSignature.toByteArray(),
                signedRequest.signature.toByteArray()
            )
        } catch (e: Exception) {
            Timber.e(e, "Signature verification failed")
            false
        }
    }
    
    override fun isTimestampValid(timestamp: Long, windowSeconds: Long): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        val timeDiff = Math.abs(currentTime - timestamp)
        return timeDiff <= windowSeconds
    }
    
    override fun generateNonce(): String {
        val bytes = ByteArray(NONCE_LENGTH)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Builds the message string to be signed.
     */
    private fun buildMessage(request: SyncRequest, timestamp: Long, nonce: String): String {
        return "${request.userId}|${request.data}|${request.endpoint}|$timestamp|$nonce"
    }
    
    /**
     * Computes HMAC-SHA256 signature.
     */
    private fun computeHmac(message: String, secretKey: String): String {
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            val secretKeySpec = SecretKeySpec(secretKey.toByteArray(), HMAC_ALGORITHM)
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(message.toByteArray())
            hmacBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "HMAC computation failed")
            throw SecurityException("Failed to compute HMAC signature", e)
        }
    }
}
