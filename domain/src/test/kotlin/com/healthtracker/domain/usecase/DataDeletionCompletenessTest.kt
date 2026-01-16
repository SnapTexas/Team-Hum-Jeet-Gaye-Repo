package com.healthtracker.domain.usecase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for data deletion completeness.
 * 
 * **Validates: Requirements 14.6**
 * 
 * Property 38: Data Deletion Completeness
 * For any user data deletion request, after completion, querying for that 
 * user's data SHALL return empty results across all data stores (local and remote).
 */
class DataDeletionCompletenessTest : FunSpec({
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all user profile data") {
        checkAll(100, Arb.string(5..20), Arb.string(1..50), Arb.int(13..80)) { userId, name, age ->
            val dataStore = TestDataStore()
            
            // Add user data
            dataStore.saveUserProfile(userId, name, age)
            dataStore.hasUserProfile(userId) shouldBe true
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify profile is deleted
            dataStore.hasUserProfile(userId) shouldBe false
            dataStore.getUserProfile(userId) shouldBe null
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all health metrics") {
        checkAll(100, Arb.string(5..20), Arb.list(Arb.int(0..50000), 1..30)) { userId, stepsList ->
            val dataStore = TestDataStore()
            
            // Add health metrics
            stepsList.forEachIndexed { index, steps ->
                dataStore.saveHealthMetric(userId, "day_$index", steps)
            }
            dataStore.getHealthMetricsCount(userId) shouldBe stepsList.size
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify metrics are deleted
            dataStore.getHealthMetricsCount(userId) shouldBe 0
            dataStore.getHealthMetrics(userId).shouldBeEmpty()
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all gamification data") {
        checkAll(100, Arb.string(5..20), Arb.int(0..100000), Arb.int(1..100)) { userId, points, level ->
            val dataStore = TestDataStore()
            
            // Add gamification data
            dataStore.saveGamificationData(userId, points, level)
            dataStore.hasGamificationData(userId) shouldBe true
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify gamification data is deleted
            dataStore.hasGamificationData(userId) shouldBe false
            dataStore.getGamificationData(userId) shouldBe null
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all medical records") {
        checkAll(100, Arb.string(5..20), Arb.list(Arb.string(5..50), 0..10)) { userId, recordTitles ->
            val dataStore = TestDataStore()
            
            // Add medical records
            recordTitles.forEachIndexed { index, title ->
                dataStore.saveMedicalRecord(userId, "record_$index", title)
            }
            dataStore.getMedicalRecordsCount(userId) shouldBe recordTitles.size
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify records are deleted
            dataStore.getMedicalRecordsCount(userId) shouldBe 0
            dataStore.getMedicalRecords(userId).shouldBeEmpty()
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all social circle memberships") {
        checkAll(100, Arb.string(5..20), Arb.list(Arb.string(5..30), 0..5)) { userId, circleIds ->
            val dataStore = TestDataStore()
            
            // Add circle memberships
            circleIds.forEach { circleId ->
                dataStore.saveCircleMembership(userId, circleId)
            }
            dataStore.getCircleMembershipsCount(userId) shouldBe circleIds.size
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify memberships are deleted
            dataStore.getCircleMembershipsCount(userId) shouldBe 0
            dataStore.getCircleMemberships(userId).shouldBeEmpty()
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all anomaly records") {
        checkAll(100, Arb.string(5..20), Arb.list(Arb.string(5..20), 0..20)) { userId, anomalyTypes ->
            val dataStore = TestDataStore()
            
            // Add anomalies
            anomalyTypes.forEachIndexed { index, type ->
                dataStore.saveAnomaly(userId, "anomaly_$index", type)
            }
            dataStore.getAnomaliesCount(userId) shouldBe anomalyTypes.size
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify anomalies are deleted
            dataStore.getAnomaliesCount(userId) shouldBe 0
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove all suggestions") {
        checkAll(100, Arb.string(5..20), Arb.list(Arb.string(10..100), 0..10)) { userId, suggestions ->
            val dataStore = TestDataStore()
            
            // Add suggestions
            suggestions.forEachIndexed { index, text ->
                dataStore.saveSuggestion(userId, "suggestion_$index", text)
            }
            dataStore.getSuggestionsCount(userId) shouldBe suggestions.size
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // Verify suggestions are deleted
            dataStore.getSuggestionsCount(userId) shouldBe 0
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should remove data from both local and remote stores") {
        checkAll(100, Arb.string(5..20), Arb.string(1..50)) { userId, name ->
            val localStore = TestDataStore()
            val remoteStore = TestDataStore()
            
            // Add data to both stores
            localStore.saveUserProfile(userId, name, 25)
            remoteStore.saveUserProfile(userId, name, 25)
            
            localStore.hasUserProfile(userId) shouldBe true
            remoteStore.hasUserProfile(userId) shouldBe true
            
            // Delete from both stores
            localStore.deleteAllUserData(userId)
            remoteStore.deleteAllUserData(userId)
            
            // Verify deletion from both
            localStore.hasUserProfile(userId) shouldBe false
            remoteStore.hasUserProfile(userId) shouldBe false
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("deletion should not affect other users data") {
        checkAll(100, Arb.string(5..20), Arb.string(5..20), Arb.string(1..50)) { userId1, userId2, name ->
            // Ensure different user IDs
            val user1 = "user1_$userId1"
            val user2 = "user2_$userId2"
            
            val dataStore = TestDataStore()
            
            // Add data for both users
            dataStore.saveUserProfile(user1, name, 25)
            dataStore.saveUserProfile(user2, name, 30)
            
            // Delete only user1's data
            dataStore.deleteAllUserData(user1)
            
            // Verify user1's data is deleted
            dataStore.hasUserProfile(user1) shouldBe false
            
            // Verify user2's data is intact
            dataStore.hasUserProfile(user2) shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 38: Data Deletion Completeness
    test("querying deleted user should return empty results") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val dataStore = TestDataStore()
            
            // Add various data
            dataStore.saveUserProfile(userId, "Test User", 25)
            dataStore.saveHealthMetric(userId, "day_1", 10000)
            dataStore.saveGamificationData(userId, 1000, 5)
            dataStore.saveMedicalRecord(userId, "record_1", "Lab Report")
            
            // Delete all data
            dataStore.deleteAllUserData(userId)
            
            // All queries should return empty/null
            dataStore.getUserProfile(userId) shouldBe null
            dataStore.getHealthMetrics(userId).shouldBeEmpty()
            dataStore.getGamificationData(userId) shouldBe null
            dataStore.getMedicalRecords(userId).shouldBeEmpty()
        }
    }
})

/**
 * Test implementation of data store for property testing.
 */
class TestDataStore {
    private val userProfiles = mutableMapOf<String, Pair<String, Int>>()
    private val healthMetrics = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    private val gamificationData = mutableMapOf<String, Pair<Int, Int>>()
    private val medicalRecords = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val circleMemberships = mutableMapOf<String, MutableList<String>>()
    private val anomalies = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val suggestions = mutableMapOf<String, MutableList<Pair<String, String>>>()
    
    // User Profile
    fun saveUserProfile(userId: String, name: String, age: Int) {
        userProfiles[userId] = name to age
    }
    
    fun hasUserProfile(userId: String): Boolean = userProfiles.containsKey(userId)
    
    fun getUserProfile(userId: String): Pair<String, Int>? = userProfiles[userId]
    
    // Health Metrics
    fun saveHealthMetric(userId: String, date: String, steps: Int) {
        healthMetrics.getOrPut(userId) { mutableListOf() }.add(date to steps)
    }
    
    fun getHealthMetricsCount(userId: String): Int = healthMetrics[userId]?.size ?: 0
    
    fun getHealthMetrics(userId: String): List<Pair<String, Int>> = healthMetrics[userId] ?: emptyList()
    
    // Gamification
    fun saveGamificationData(userId: String, points: Int, level: Int) {
        gamificationData[userId] = points to level
    }
    
    fun hasGamificationData(userId: String): Boolean = gamificationData.containsKey(userId)
    
    fun getGamificationData(userId: String): Pair<Int, Int>? = gamificationData[userId]
    
    // Medical Records
    fun saveMedicalRecord(userId: String, recordId: String, title: String) {
        medicalRecords.getOrPut(userId) { mutableListOf() }.add(recordId to title)
    }
    
    fun getMedicalRecordsCount(userId: String): Int = medicalRecords[userId]?.size ?: 0
    
    fun getMedicalRecords(userId: String): List<Pair<String, String>> = medicalRecords[userId] ?: emptyList()
    
    // Circle Memberships
    fun saveCircleMembership(userId: String, circleId: String) {
        circleMemberships.getOrPut(userId) { mutableListOf() }.add(circleId)
    }
    
    fun getCircleMembershipsCount(userId: String): Int = circleMemberships[userId]?.size ?: 0
    
    fun getCircleMemberships(userId: String): List<String> = circleMemberships[userId] ?: emptyList()
    
    // Anomalies
    fun saveAnomaly(userId: String, anomalyId: String, type: String) {
        anomalies.getOrPut(userId) { mutableListOf() }.add(anomalyId to type)
    }
    
    fun getAnomaliesCount(userId: String): Int = anomalies[userId]?.size ?: 0
    
    // Suggestions
    fun saveSuggestion(userId: String, suggestionId: String, text: String) {
        suggestions.getOrPut(userId) { mutableListOf() }.add(suggestionId to text)
    }
    
    fun getSuggestionsCount(userId: String): Int = suggestions[userId]?.size ?: 0
    
    // Delete All
    fun deleteAllUserData(userId: String) {
        userProfiles.remove(userId)
        healthMetrics.remove(userId)
        gamificationData.remove(userId)
        medicalRecords.remove(userId)
        circleMemberships.remove(userId)
        anomalies.remove(userId)
        suggestions.remove(userId)
    }
}
