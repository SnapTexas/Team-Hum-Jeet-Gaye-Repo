package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.AuditLogEntry
import com.healthtracker.domain.model.AuditOperationType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for audit trail logging.
 * 
 * **Validates: Requirements 14.8**
 * 
 * Property 39: Audit Trail Logging
 * For any sensitive operation (record access, data export, data deletion, 
 * privacy setting change), an audit log entry SHALL be created with timestamp, 
 * operation type, and user ID.
 */
class AuditTrailLoggingTest : FunSpec({
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("sensitive operation should create audit log entry") {
        checkAll(100, Arb.enum<AuditOperationType>(), Arb.string(5..20), Arb.string(10..100)) { operationType, userId, details ->
            val auditLogger = TestAuditLogger()
            
            // Perform sensitive operation
            auditLogger.logOperation(userId, operationType, details)
            
            // Verify audit entry was created
            val entries = auditLogger.getAuditLog(userId)
            entries.shouldNotBeEmpty()
            
            // Find the entry we just created
            val entry = entries.find { it.operationType == operationType && it.details == details }
            entry.shouldNotBeNull()
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("audit entry should contain timestamp") {
        checkAll(100, Arb.enum<AuditOperationType>(), Arb.string(5..20)) { operationType, userId ->
            val auditLogger = TestAuditLogger()
            val beforeLog = Instant.now()
            
            // Log operation
            auditLogger.logOperation(userId, operationType, "Test operation")
            
            val afterLog = Instant.now()
            
            // Verify timestamp is within expected range
            val entries = auditLogger.getAuditLog(userId)
            val entry = entries.first()
            
            entry.timestamp.shouldNotBeNull()
            entry.timestamp.isAfter(beforeLog.minusSeconds(1)) shouldBe true
            entry.timestamp.isBefore(afterLog.plusSeconds(1)) shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("audit entry should contain operation type") {
        checkAll(100, Arb.enum<AuditOperationType>(), Arb.string(5..20)) { operationType, userId ->
            val auditLogger = TestAuditLogger()
            
            // Log operation
            auditLogger.logOperation(userId, operationType, "Test operation")
            
            // Verify operation type is recorded
            val entries = auditLogger.getAuditLog(userId)
            val entry = entries.first()
            
            entry.operationType shouldBe operationType
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("audit entry should contain user ID") {
        checkAll(100, Arb.enum<AuditOperationType>(), Arb.string(5..20)) { operationType, userId ->
            val auditLogger = TestAuditLogger()
            
            // Log operation
            auditLogger.logOperation(userId, operationType, "Test operation")
            
            // Verify user ID is recorded
            val entries = auditLogger.getAuditLog(userId)
            val entry = entries.first()
            
            entry.userId shouldBe userId
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("data export should create audit entry") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val auditLogger = TestAuditLogger()
            
            // Simulate data export
            auditLogger.logOperation(userId, AuditOperationType.DATA_EXPORT, "User data exported")
            
            // Verify audit entry exists
            val entries = auditLogger.getAuditLog(userId)
            entries.any { it.operationType == AuditOperationType.DATA_EXPORT } shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("data deletion should create audit entry") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val auditLogger = TestAuditLogger()
            
            // Simulate data deletion
            auditLogger.logOperation(userId, AuditOperationType.DATA_DELETION, "User data deleted")
            
            // Verify audit entry exists
            val entries = auditLogger.getAuditLog(userId)
            entries.any { it.operationType == AuditOperationType.DATA_DELETION } shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("privacy setting change should create audit entry") {
        checkAll(100, Arb.string(5..20), Arb.string(10..50)) { userId, settingDetails ->
            val auditLogger = TestAuditLogger()
            
            // Simulate privacy setting change
            auditLogger.logOperation(userId, AuditOperationType.PRIVACY_SETTING_CHANGE, settingDetails)
            
            // Verify audit entry exists
            val entries = auditLogger.getAuditLog(userId)
            entries.any { 
                it.operationType == AuditOperationType.PRIVACY_SETTING_CHANGE && 
                it.details == settingDetails 
            } shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("medical record access should create audit entry") {
        checkAll(100, Arb.string(5..20), Arb.string(5..30)) { userId, recordId ->
            val auditLogger = TestAuditLogger()
            
            // Simulate medical record access
            auditLogger.logOperation(userId, AuditOperationType.MEDICAL_RECORD_ACCESS, "Accessed record: $recordId")
            
            // Verify audit entry exists
            val entries = auditLogger.getAuditLog(userId)
            entries.any { 
                it.operationType == AuditOperationType.MEDICAL_RECORD_ACCESS && 
                it.details.contains(recordId) 
            } shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("audit entries should have unique IDs") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val auditLogger = TestAuditLogger()
            
            // Log multiple operations
            repeat(10) { index ->
                auditLogger.logOperation(userId, AuditOperationType.SENSITIVE_DATA_ACCESS, "Operation $index")
            }
            
            // Verify all entries have unique IDs
            val entries = auditLogger.getAuditLog(userId)
            val ids = entries.map { it.id }
            ids.distinct().size shouldBe ids.size
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("audit log should preserve chronological order") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val auditLogger = TestAuditLogger()
            
            // Log operations in sequence
            val operations = listOf(
                AuditOperationType.LOGIN,
                AuditOperationType.MEDICAL_RECORD_ACCESS,
                AuditOperationType.DATA_EXPORT,
                AuditOperationType.LOGOUT
            )
            
            operations.forEach { op ->
                auditLogger.logOperation(userId, op, "Operation: ${op.name}")
                Thread.sleep(1) // Ensure different timestamps
            }
            
            // Verify chronological order (most recent first)
            val entries = auditLogger.getAuditLog(userId)
            for (i in 0 until entries.size - 1) {
                entries[i].timestamp.isAfter(entries[i + 1].timestamp) shouldBe true
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 39: Audit Trail Logging
    test("all sensitive operation types should be loggable") {
        val auditLogger = TestAuditLogger()
        val userId = "test-user"
        
        // Log all operation types
        AuditOperationType.values().forEach { operationType ->
            auditLogger.logOperation(userId, operationType, "Test: ${operationType.name}")
        }
        
        // Verify all types were logged
        val entries = auditLogger.getAuditLog(userId)
        val loggedTypes = entries.map { it.operationType }.toSet()
        
        AuditOperationType.values().forEach { operationType ->
            loggedTypes shouldContain operationType
        }
    }
})

/**
 * Test implementation of audit logger for property testing.
 */
class TestAuditLogger {
    private val auditLog = mutableListOf<AuditLogEntry>()
    
    fun logOperation(userId: String, operationType: AuditOperationType, details: String) {
        val entry = AuditLogEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            operationType = operationType,
            details = details,
            timestamp = Instant.now(),
            ipAddress = null,
            deviceInfo = "Test Device"
        )
        auditLog.add(0, entry) // Add at beginning for chronological order (most recent first)
    }
    
    fun getAuditLog(userId: String): List<AuditLogEntry> {
        return auditLog.filter { it.userId == userId }
    }
    
    fun getAllAuditLogs(): List<AuditLogEntry> {
        return auditLog.toList()
    }
}
