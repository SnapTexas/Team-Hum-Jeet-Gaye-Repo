package com.healthtracker.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 * Service for encrypting and decrypting sensitive health data.
 * 
 * Uses Android Keystore for secure key management and AES-256-GCM
 * for encryption. All keys are hardware-backed when available.
 * 
 * CRITICAL: This service is essential for HIPAA-style data protection.
 */
interface EncryptionService {
    /**
     * Encrypts raw byte data.
     * @param data The data to encrypt
     * @return Encrypted data with IV prepended
     */
    fun encrypt(data: ByteArray): ByteArray
    
    /**
     * Decrypts encrypted byte data.
     * @param encryptedData The encrypted data with IV prepended
     * @return Original decrypted data
     */
    fun decrypt(encryptedData: ByteArray): ByteArray
    
    /**
     * Encrypts a string value.
     * @param text The string to encrypt
     * @return Base64-encoded encrypted string
     */
    fun encryptString(text: String): String
    
    /**
     * Decrypts an encrypted string.
     * @param encrypted Base64-encoded encrypted string
     * @return Original decrypted string
     */
    fun decryptString(encrypted: String): String
    
    /**
     * Checks if encryption is available and properly initialized.
     */
    fun isAvailable(): Boolean
}

/**
 * Implementation of EncryptionService using Android Keystore.
 */
@Singleton
class EncryptionServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : EncryptionService {
    
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "health_tracker_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }
    }
    
    private val secretKey: SecretKey by lazy {
        getOrCreateKey()
    }
    
    /**
     * Gets existing key or creates a new one in Android Keystore.
     */
    private fun getOrCreateKey(): SecretKey {
        return try {
            // Try to get existing key
            val entry = keyStore.getEntry(KEY_ALIAS, null)
            if (entry is KeyStore.SecretKeyEntry) {
                entry.secretKey
            } else {
                createKey()
            }
        } catch (e: Exception) {
            Timber.w(e, "Key not found, creating new one")
            createKey()
        }
    }
    
    /**
     * Creates a new AES-256 key in Android Keystore.
     */
    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Allow background encryption
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }
    
    override fun encrypt(data: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data)
            
            // Prepend IV to encrypted data
            ByteArray(iv.size + encryptedData.size).apply {
                System.arraycopy(iv, 0, this, 0, iv.size)
                System.arraycopy(encryptedData, 0, this, iv.size, encryptedData.size)
            }
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            throw SecurityException("Failed to encrypt data", e)
        }
    }
    
    override fun decrypt(encryptedData: ByteArray): ByteArray {
        return try {
            require(encryptedData.size > GCM_IV_LENGTH) { 
                "Encrypted data too short" 
            }
            
            // Extract IV from beginning
            val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            throw SecurityException("Failed to decrypt data", e)
        }
    }
    
    override fun encryptString(text: String): String {
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
    }
    
    override fun decryptString(encrypted: String): String {
        val encryptedBytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
        val decrypted = decrypt(encryptedBytes)
        return String(decrypted, Charsets.UTF_8)
    }
    
    override fun isAvailable(): Boolean {
        return try {
            // Test encryption/decryption
            val testData = "test".toByteArray()
            val encrypted = encrypt(testData)
            val decrypted = decrypt(encrypted)
            testData.contentEquals(decrypted)
        } catch (e: Exception) {
            Timber.e(e, "Encryption service not available")
            false
        }
    }
}
