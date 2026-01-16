package com.healthtracker.core.config

import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Unit tests for FeatureFlagManager.
 * 
 * Note: These tests verify the default behavior.
 * Integration tests with Firebase Remote Config would require
 * Firebase Test Lab or emulator setup.
 */
class FeatureFlagManagerTest {
    
    private lateinit var featureFlagManager: FeatureFlagManager
    
    @Before
    fun setup() {
        featureFlagManager = FeatureFlagManager()
    }
    
    @Test
    fun `default flags should be enabled`() {
        // All features should be enabled by default
        assertTrue(featureFlagManager.isMlEnabled())
        assertTrue(featureFlagManager.isAvatarEnabled())
        assertTrue(featureFlagManager.isCvFoodEnabled())
        assertTrue(featureFlagManager.isAnomalyDetectionEnabled())
    }
}
