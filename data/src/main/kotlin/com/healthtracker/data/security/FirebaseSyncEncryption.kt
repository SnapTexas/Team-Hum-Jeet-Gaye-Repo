package com.healthtracker.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for encrypting data before Firebase sync.
 * 
 * Implements end-to-end encryption for all data transmitted to Firebase.
 * Uses user-specific keys stored in Android Keystore.
 * 
 * CRITICAL: All Firebase payloads MUST be encrypted before transmission.
 */
interface FirebaseSyncEncryption {
    /**
     * Encrypts a payload before sending to Firebase.
     * 
     * @param payload The data to encrypt
     * @param userId The user ID (used for key derivation)
     * @return Encrypted payload with IV prepended
     */
    fun encryptPayload(payload: ByteArray, userId: String): ByteArray
    
    /**
     * Decrypts a payload received from Firebase.
     * 
     * @param encryptedPayload The encrypted data with IV prepended
     * @param userId The user ID (used for key derivation)
     * @return Decrypted payload
     */
    fun decryptPayload(encryptedPayload: ByteArray, userId: String): ByteArray
    
    /**
     * Encrypts a string payload for Firebase sync.
     * 
     * @param payload The string to encrypt
     * @param userId The user ID
     * @return Base64-encoded encrypted string
     */
    fun encryptStringPayload(payload: String, userId: String): String
    
    /**
     * Decrypts a string payload from Firebase.
     * 
     * @param encryptedPayload Base64-encoded encrypted string
     * @param userId The user ID
     * @return Decrypted string
     */
    fun decryptStringPayload(encryptedPayload: String, userId: String): String
    
    /**
     * Checks if encryption is available for a user.
     * 
     * @param userId The user ID
     * @return true if encryption is available
     */
    fun isEncryptionAvailable(userId: String): Boolean
    
    /**
     * Generates or retrieves the encryption key for a user.
     * 
     * @param userId The user ID
     */
    fun ensureKeyExists(userId: String)
    
    /**
     * Deletes the encryption key for a user (for account deletion).
     * 
     * @param userId The user ID
     */
    fun deleteUserKey(userId: String)
}

/**
 * Implementation of FirebaseSyncEncryption using Android Keystore.
 */
@Singleton
class FirebaseSyncEncryptionImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FirebaseSyncEncryption {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "firebase_sync_key_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }
    
    /**
     * Gets the key alias for a specific user.
     */
    private fun getKeyAlias(userId: String): String {
        return "$KEY_ALIAS_PREFIX${userId.hashCode()}"
    }
    
    /**
     * Gets or creates the encryption key for a user.
     */
    private fun getOrCreateKey(userId: String): SecretKey {
        val keyAlias = getKeyAlias(userId)
        
        return try {
            val entry = keyStore.getEntry(keyAlias, null)
            if (entry is KeyStore.SecretKeyEntry) {
                entry.secretKey
            } else {
                createKey(keyAlias)
            }
        } catch (e: Exception) {
            Timber.w(e, "Key not found for user, creating new one")
            createKey(keyAlias)
        }
    }
    
    /**
     * Creates a new AES-256 key for a user.
     */
    private fun createKey(keyAlias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Allow background sync
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
    
    override fun encryptPayload(payload: ByteArray, userId: String): ByteArray {
        return try {
            val secretKey = getOrCreateKey(userId)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(payload)
            
            // Prepend IV to encrypted data
            ByteArray(iv.size + encryptedData.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encryptedData, 0, this, iv.size, encryptedData.size)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt payload for Firebase sync")
            throw SecurityException("Failed to encrypt payload", e)
        }
    }
    
    override fun decryptPayload(encryptedPayload: ByteArray, userId: String): ByteArray {
        return try {
            require(encryptedPayload.size > GCM_IV_LENGTH) {
                "Encrypted payload too short"
            }
            
            val secretKey = getOrCreateKey(userId)
            
            // Extract IV from beginning
            val iv = encryptedPayload.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = encryptedPayload.copyOfRange(GCM_IV_LENGTH, encryptedPayload.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt payload from Firebase")
            throw SecurityException("Failed to decrypt payload", e)
        }
    }
    
    override fun encryptStringPayload(payload: String, userId: String): String {
        val encrypted = encryptPayload(payload.toByteArray(Charsets.UTF_8), userId)
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }
    
    override fun decryptStringPayload(encryptedPayload: String, userId: String): String {
        val encryptedBytes = android.util.Base64.decode(encryptedPayload, android.util.Base64.NO_WRAP)
        val decrypted = decryptPayload(encryptedBytes, userId)
        return String(decrypted, Charsets.UTF_8)
    }
    
    override fun isEncryptionAvailable(userId: String): Boolean {
        return try {
            val keyAlias = getKeyAlias(userId)
            keyStore.containsAlias(keyAlias) || run {
                // Try to create a key
                createKey(keyAlias)
                true
            }
        } catch (e: Exception) {
            Timber.e(e, "Encryption not available for user")
            false
        }
    }
    
    override fun ensureKeyExists(userId: String) {
        getOrCreateKey(userId)
        Timber.d("Encryption key ensured for user")
    }
    
    override fun deleteUserKey(userId: String) {
        try {
            val keyAlias = getKeyAlias(userId)
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
                Timber.d("Encryption key deleted for user")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete encryption key for user")
        }
    }
}
