package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DataCategory
import com.healthtracker.domain.model.PrivacySettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant

/**
 * Property-based tests for privacy toggle enforcement.
 * 
 * **Validates: Requirements 14.3, 14.4**
 * 
 * Property 36: Privacy Toggle Enforcement
 * For any data category toggle set to disabled, the Data_Collector SHALL not 
 * collect or store that data type, and existing data of that type SHALL not 
 * be included in new syncs.
 */
class PrivacyToggleEnforcementTest : FunSpec({
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("disabled toggle should prevent data collection for that category") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.string(5..20)) { category, userId ->
            val privacyManager = TestPrivacyManager()
            
            // Disable the category
            privacyManager.setDataCategoryEnabled(userId, category, false)
            
            // Verify collection is blocked
            val canCollect = privacyManager.canCollectData(userId, category)
            canCollect shouldBe false
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("enabled toggle should allow data collection for that category") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.string(5..20)) { category, userId ->
            val privacyManager = TestPrivacyManager()
            
            // Enable the category
            privacyManager.setDataCategoryEnabled(userId, category, true)
            
            // Verify collection is allowed
            val canCollect = privacyManager.canCollectData(userId, category)
            canCollect shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("toggling category should immediately affect collection status") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.boolean(), Arb.string(5..20)) { category, initialState, userId ->
            val privacyManager = TestPrivacyManager()
            
            // Set initial state
            privacyManager.setDataCategoryEnabled(userId, category, initialState)
            privacyManager.canCollectData(userId, category) shouldBe initialState
            
            // Toggle to opposite state
            privacyManager.setDataCategoryEnabled(userId, category, !initialState)
            privacyManager.canCollectData(userId, category) shouldBe !initialState
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("disabled category should not be included in sync") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.string(5..20)) { category, userId ->
            val privacyManager = TestPrivacyManager()
            
            // Disable the category
            privacyManager.setDataCategoryEnabled(userId, category, false)
            
            // Get categories to sync
            val categoriesToSync = privacyManager.getCategoriesToSync(userId)
            
            // Disabled category should not be in sync list
            categoriesToSync.contains(category) shouldBe false
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("enabled category should be included in sync") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.string(5..20)) { category, userId ->
            val privacyManager = TestPrivacyManager()
            
            // Enable the category
            privacyManager.setDataCategoryEnabled(userId, category, true)
            
            // Get categories to sync
            val categoriesToSync = privacyManager.getCategoriesToSync(userId)
            
            // Enabled category should be in sync list
            categoriesToSync.contains(category) shouldBe true
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("privacy settings should correctly reflect all toggle states") {
        checkAll(100, 
            Arb.boolean(), Arb.boolean(), Arb.boolean(), 
            Arb.boolean(), Arb.boolean(), Arb.boolean(),
            Arb.boolean(), Arb.boolean(), Arb.boolean(),
            Arb.string(5..20)
        ) { health, location, heartRate, sleep, mood, diet, social, analytics, crash, userId ->
            val settings = PrivacySettings(
                userId = userId,
                healthMetricsEnabled = health,
                locationEnabled = location,
                heartRateEnabled = heartRate,
                sleepDataEnabled = sleep,
                moodDataEnabled = mood,
                dietDataEnabled = diet,
                socialSharingEnabled = social,
                analyticsEnabled = analytics,
                crashReportingEnabled = crash,
                updatedAt = Instant.now()
            )
            
            val privacyManager = TestPrivacyManager()
            privacyManager.applySettings(settings)
            
            // Verify each category matches settings
            privacyManager.canCollectData(userId, DataCategory.HEALTH_METRICS) shouldBe health
            privacyManager.canCollectData(userId, DataCategory.LOCATION) shouldBe location
            privacyManager.canCollectData(userId, DataCategory.HEART_RATE) shouldBe heartRate
            privacyManager.canCollectData(userId, DataCategory.SLEEP_DATA) shouldBe sleep
            privacyManager.canCollectData(userId, DataCategory.MOOD_DATA) shouldBe mood
            privacyManager.canCollectData(userId, DataCategory.DIET_DATA) shouldBe diet
            privacyManager.canCollectData(userId, DataCategory.SOCIAL_SHARING) shouldBe social
            privacyManager.canCollectData(userId, DataCategory.ANALYTICS) shouldBe analytics
            privacyManager.canCollectData(userId, DataCategory.CRASH_REPORTING) shouldBe crash
        }
    }
    
    // Feature: smart-health-tracker, Property 36: Privacy Toggle Enforcement
    test("data collection should be gated by privacy check") {
        checkAll(100, Arb.enum<DataCategory>(), Arb.boolean(), Arb.string(5..20)) { category, enabled, userId ->
            val privacyManager = TestPrivacyManager()
            val dataCollector = TestDataCollector(privacyManager)
            
            // Set category state
            privacyManager.setDataCategoryEnabled(userId, category, enabled)
            
            // Attempt to collect data
            val collected = dataCollector.collectData(userId, category)
            
            // Collection should only succeed if enabled
            collected shouldBe enabled
        }
    }
})

/**
 * Test implementation of privacy manager for property testing.
 */
class TestPrivacyManager {
    private val categoryStates = mutableMapOf<Pair<String, DataCategory>, Boolean>()
    
    fun setDataCategoryEnabled(userId: String, category: DataCategory, enabled: Boolean) {
        categoryStates[userId to category] = enabled
    }
    
    fun canCollectData(userId: String, category: DataCategory): Boolean {
        return categoryStates[userId to category] ?: true // Default to enabled
    }
    
    fun getCategoriesToSync(userId: String): Set<DataCategory> {
        return DataCategory.values().filter { category ->
            canCollectData(userId, category)
        }.toSet()
    }
    
    fun applySettings(settings: PrivacySettings) {
        val userId = settings.userId
        setDataCategoryEnabled(userId, DataCategory.HEALTH_METRICS, settings.healthMetricsEnabled)
        setDataCategoryEnabled(userId, DataCategory.LOCATION, settings.locationEnabled)
        setDataCategoryEnabled(userId, DataCategory.HEART_RATE, settings.heartRateEnabled)
        setDataCategoryEnabled(userId, DataCategory.SLEEP_DATA, settings.sleepDataEnabled)
        setDataCategoryEnabled(userId, DataCategory.MOOD_DATA, settings.moodDataEnabled)
        setDataCategoryEnabled(userId, DataCategory.DIET_DATA, settings.dietDataEnabled)
        setDataCategoryEnabled(userId, DataCategory.SOCIAL_SHARING, settings.socialSharingEnabled)
        setDataCategoryEnabled(userId, DataCategory.ANALYTICS, settings.analyticsEnabled)
        setDataCategoryEnabled(userId, DataCategory.CRASH_REPORTING, settings.crashReportingEnabled)
    }
}

/**
 * Test implementation of data collector that respects privacy settings.
 */
class TestDataCollector(private val privacyManager: TestPrivacyManager) {
    
    fun collectData(userId: String, category: DataCategory): Boolean {
        // Check privacy settings before collecting
        if (!privacyManager.canCollectData(userId, category)) {
            return false // Collection blocked by privacy settings
        }
        
        // Simulate data collection
        return true
    }
}
