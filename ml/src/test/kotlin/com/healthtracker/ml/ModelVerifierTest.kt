package com.healthtracker.ml

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for ModelVerifier implementation.
 */
class ModelVerifierTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var modelVerifier: ModelVerifier
    
    @Before
    fun setup() {
        modelVerifier = ModelVerifierImpl()
    }
    
    @Test
    fun `computeFileHash generates consistent hash`() = runTest {
        // Given
        val file = tempFolder.newFile("test.txt")
        file.writeText("Hello, World!")
        
        // When
        val hash1 = modelVerifier.computeFileHash(file)
        val hash2 = modelVerifier.computeFileHash(file)
        
        // Then
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 = 64 hex chars
    }
    
    @Test
    fun `computeFileHash generates different hashes for different content`() = runTest {
        // Given
        val file1 = tempFolder.newFile("test1.txt")
        file1.writeText("Content 1")
        
        val file2 = tempFolder.newFile("test2.txt")
        file2.writeText("Content 2")
        
        // When
        val hash1 = modelVerifier.computeFileHash(file1)
        val hash2 = modelVerifier.computeFileHash(file2)
        
        // Then
        assertNotEquals(hash1, hash2)
    }
    
    @Test
    fun `verifyModelIntegrity returns true for matching hash`() = runTest {
        // Given
        val file = tempFolder.newFile("model.tflite")
        file.writeText("Model content")
        val expectedHash = modelVerifier.computeFileHash(file)
        
        // When
        val isValid = modelVerifier.verifyModelIntegrity(file, expectedHash)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `verifyModelIntegrity returns false for mismatched hash`() = runTest {
        // Given
        val file = tempFolder.newFile("model.tflite")
        file.writeText("Model content")
        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
        
        // When
        val isValid = modelVerifier.verifyModelIntegrity(file, wrongHash)
        
        // Then
        assertFalse(isValid)
    }
    
    @Test
    fun `verifyModelIntegrityDetailed returns Success for valid model`() = runTest {
        // Given
        val file = tempFolder.newFile("model.tflite")
        file.writeText("Model content")
        val expectedHash = modelVerifier.computeFileHash(file)
        
        // When
        val result = modelVerifier.verifyModelIntegrityDetailed(file, expectedHash)
        
        // Then
        assertTrue(result is ModelVerificationResult.Success)
        assertEquals(file, (result as ModelVerificationResult.Success).modelFile)
    }
    
    @Test
    fun `verifyModelIntegrityDetailed returns HashMismatch for wrong hash`() = runTest {
        // Given
        val file = tempFolder.newFile("model.tflite")
        file.writeText("Model content")
        val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"
        
        // When
        val result = modelVerifier.verifyModelIntegrityDetailed(file, wrongHash)
        
        // Then
        assertTrue(result is ModelVerificationResult.HashMismatch)
        val mismatch = result as ModelVerificationResult.HashMismatch
        assertEquals(wrongHash, mismatch.expected)
        assertNotEquals(wrongHash, mismatch.actual)
    }
    
    @Test
    fun `verifyModelIntegrityDetailed returns FileNotFound for missing file`() = runTest {
        // Given
        val nonExistentFile = File(tempFolder.root, "nonexistent.tflite")
        val hash = "abc123"
        
        // When
        val result = modelVerifier.verifyModelIntegrityDetailed(nonExistentFile, hash)
        
        // Then
        assertTrue(result is ModelVerificationResult.FileNotFound)
        assertEquals(nonExistentFile.absolutePath, (result as ModelVerificationResult.FileNotFound).path)
    }
    
    @Test
    fun `verifyModelIntegrity is case insensitive`() = runTest {
        // Given
        val file = tempFolder.newFile("model.tflite")
        file.writeText("Model content")
        val hash = modelVerifier.computeFileHash(file)
        val upperCaseHash = hash.uppercase()
        
        // When
        val isValid = modelVerifier.verifyModelIntegrity(file, upperCaseHash)
        
        // Then
        assertTrue(isValid)
    }
    
    @Test
    fun `computeFileHash handles large files`() = runTest {
        // Given - Create a 1MB file
        val file = tempFolder.newFile("large.bin")
        val content = ByteArray(1024 * 1024) { it.toByte() }
        file.writeBytes(content)
        
        // When
        val hash = modelVerifier.computeFileHash(file)
        
        // Then
        assertNotNull(hash)
        assertEquals(64, hash.length)
    }
    
    @Test
    fun `computeFileHash handles empty file`() = runTest {
        // Given
        val file = tempFolder.newFile("empty.txt")
        // File is empty by default
        
        // When
        val hash = modelVerifier.computeFileHash(file)
        
        // Then
        assertNotNull(hash)
        assertEquals(64, hash.length)
        // SHA-256 of empty file
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }
}
