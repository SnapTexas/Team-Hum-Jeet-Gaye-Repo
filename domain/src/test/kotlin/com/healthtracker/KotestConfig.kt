package com.healthtracker

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.IsolationMode
import io.kotest.property.PropertyTesting

/**
 * Global Kotest configuration for the Health Tracker project.
 * 
 * This configuration ensures:
 * - Minimum 100 iterations per property test (as per Task 18.1)
 * - Consistent test isolation
 * - Proper test execution settings
 */
object KotestConfig : AbstractProjectConfig() {
    
    /**
     * Set minimum iterations for property-based tests to 100.
     * This ensures thorough testing of properties across a wide range of inputs.
     */
    init {
        PropertyTesting.defaultIterationCount = 100
    }
    
    /**
     * Use InstancePerLeaf isolation mode for better test isolation.
     * Each test gets a fresh instance of the spec class.
     */
    override val isolationMode = IsolationMode.InstancePerLeaf
    
    /**
     * Enable parallel test execution for faster test runs.
     */
    override val parallelism = 4
    
    /**
     * Timeout for individual tests (30 seconds).
     */
    override val timeout = 30_000L
}
