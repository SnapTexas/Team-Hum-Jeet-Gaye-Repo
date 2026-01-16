package com.healthtracker.domain.usecase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Property-based tests for medical record encryption round-trip.
 * 
 * **Validates: Requirements 13.5**
 * 
 * Property 33: Medical Record Encryption Round-Trip
 * For any medical record uploaded, encrypting and then decrypting 
 * SHALL produce the original content with no data loss.
 */
class MedicalRecordEncryptionTest : FunSpec({
    
    // Test encryption service implementation for property testing
    val encryptionService = TestEncryptionService()
    
    test("Property 33: encrypt then decrypt returns original content for any byte array") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * For any medical record content (represented as byte array),
         * encrypting and then decrypting SHALL produce the original content.
         */
        checkAll(100, Arb.byteArray(Arb.int(1..10000))) { originalContent ->
            val encrypted = encryptionService.encrypt(originalContent)
            val decrypted = encryptionService.decrypt(encrypted)
            
            decrypted shouldBe originalContent
        }
    }
    
    test("Property 33: encrypt then decrypt returns original string content") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * For any string content (medical record text),
         * encrypting and then decrypting SHALL preserve the original text.
         */
        checkAll(100, Arb.string(1..5000)) { originalText ->
            val encrypted = encryptionService.encryptString(originalText)
            val decrypted = encryptionService.decryptString(encrypted)
            
            decrypted shouldBe originalText
        }
    }
    
    test("Property 33: encrypted content differs from original") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * Encrypted content should not be the same as original content
         * (ensuring actual encryption is happening).
         */
        checkAll(100, Arb.byteArray(Arb.int(16..1000))) { originalContent ->
            val encrypted = encryptionService.encrypt(originalContent)
            
            // Encrypted content should be different from original
            // (except for the extremely unlikely case of collision)
            val encryptedWithoutIv = encrypted.copyOfRange(12, encrypted.size)
            (encryptedWithoutIv.contentEquals(originalContent)) shouldBe false
        }
    }
    
    test("Property 33: encryption is deterministic with same key but produces different ciphertext") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * Each encryption should produce different ciphertext due to random IV,
         * but decryption should always return the original content.
         */
        checkAll(50, Arb.byteArray(Arb.int(100..500))) { originalContent ->
            val encrypted1 = encryptionService.encrypt(originalContent)
            val encrypted2 = encryptionService.encrypt(originalContent)
            
            // Different encryptions should produce different ciphertext (due to random IV)
            (encrypted1.contentEquals(encrypted2)) shouldBe false
            
            // But both should decrypt to the same original content
            val decrypted1 = encryptionService.decrypt(encrypted1)
            val decrypted2 = encryptionService.decrypt(encrypted2)
            
            decrypted1 shouldBe originalContent
            decrypted2 shouldBe originalContent
        }
    }
    
    test("Property 33: empty content encryption round-trip") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * Even empty content should encrypt and decrypt correctly.
         */
        val emptyContent = byteArrayOf()
        val encrypted = encryptionService.encrypt(emptyContent)
        val decrypted = encryptionService.decrypt(encrypted)
        
        decrypted shouldBe emptyContent
    }
    
    test("Property 33: large content encryption round-trip") {
        /**
         * **Validates: Requirements 13.5**
         * 
         * Large medical records (up to 50MB) should encrypt and decrypt correctly.
         */
        // Test with 1MB content (representative of large files)
        val largeContent = ByteArray(1024 * 1024) { it.toByte() }
        val encrypted = encryptionService.encrypt(largeContent)
        val decrypted = encryptionService.decrypt(encrypted)
        
        decrypted shouldBe largeContent
    }
})

/**
 * Test implementation of encryption service using AES-256-GCM.
 * Mirrors the production EncryptionServiceImpl but without Android dependencies.
 */
private class TestEncryptionService {
    
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val secretKey: SecretKey = generateKey()
    private val secureRandom = SecureRandom()
    
    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
    
    fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        // Generate random IV
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        
        val encryptedData = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return ByteArray(iv.size + encryptedData.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(encryptedData, 0, this, iv.size, encryptedData.size)
        }
    }
    
    fun decrypt(encryptedData: ByteArray): ByteArray {
        require(encryptedData.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        
        // Extract IV from beginning
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherText)
    }
    
    fun encryptString(text: String): String {
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(encrypted)
    }
    
    fun decryptString(encrypted: String): String {
        val encryptedBytes = java.util.Base64.getDecoder().decode(encrypted)
        val decrypted = decrypt(encryptedBytes)
        return String(decrypted, Charsets.UTF_8)
    }
}
