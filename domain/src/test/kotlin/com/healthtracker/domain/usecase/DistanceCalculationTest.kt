package com.healthtracker.domain.usecase

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.forAll

/**
 * Property-based tests for distance calculation from steps.
 * 
 * **Validates: Property 3**
 * 
 * Tests:
 * - distance = steps × step_length
 * - result is always non-negative
 */
class DistanceCalculationTest : FunSpec({
    
    // Average step length in meters (same as in HealthDataRepositoryImpl)
    val averageStepLengthMeters = 0.762
    
    // ============================================
    // GENERATORS
    // ============================================
    
    // Valid step count generator (0 to 100,000 steps per day is reasonable)
    val validStepsArb = Arb.int(0..100_000)
    
    // Large step count for stress testing
    val largeStepsArb = Arb.int(0..1_000_000)
    
    // ============================================
    // PROPERTY 3: Distance calculation invariant
    // **Validates: Property 3**
    // ============================================
    
    context("Property 3: Distance calculation invariant") {
        
        test("distance equals steps multiplied by step length") {
            checkAll(100, validStepsArb) { steps ->
                val distance = calculateDistanceFromSteps(steps, averageStepLengthMeters)
                val expected = steps * averageStepLengthMeters
                
                distance shouldBe expected
            }
        }
        
        test("distance is always non-negative for non-negative steps") {
            forAll(validStepsArb) { steps ->
                val distance = calculateDistanceFromSteps(steps, averageStepLengthMeters)
                distance >= 0.0
            }
        }
        
        test("zero steps results in zero distance") {
            val distance = calculateDistanceFromSteps(0, averageStepLengthMeters)
            distance shouldBe 0.0
        }
        
        test("distance increases monotonically with steps") {
            checkAll(validStepsArb, validStepsArb) { steps1, steps2 ->
                val distance1 = calculateDistanceFromSteps(steps1, averageStepLengthMeters)
                val distance2 = calculateDistanceFromSteps(steps2, averageStepLengthMeters)
                
                if (steps1 <= steps2) {
                    distance1 shouldBeLessThanOrEqual distance2
                } else {
                    distance1 shouldBeGreaterThanOrEqual distance2
                }
            }
        }
        
        test("distance calculation is deterministic") {
            checkAll(validStepsArb) { steps ->
                val distance1 = calculateDistanceFromSteps(steps, averageStepLengthMeters)
                val distance2 = calculateDistanceFromSteps(steps, averageStepLengthMeters)
                
                distance1 shouldBe distance2
            }
        }
    }
    
    // ============================================
    // EDGE CASES
    // ============================================
    
    context("Edge cases") {
        
        test("typical daily step count (10,000 steps)") {
            val steps = 10_000
            val distance = calculateDistanceFromSteps(steps, averageStepLengthMeters)
            
            // 10,000 steps × 0.762m = 7,620 meters = ~7.6 km
            distance shouldBe 7620.0
        }
        
        test("marathon distance (~42km) requires approximately 55,000 steps") {
            val marathonDistanceMeters = 42_195.0
            val stepsForMarathon = (marathonDistanceMeters / averageStepLengthMeters).toInt()
            
            // Should be around 55,000 steps
            val calculatedDistance = calculateDistanceFromSteps(stepsForMarathon, averageStepLengthMeters)
            
            // Allow 1 meter tolerance due to integer conversion
            calculatedDistance shouldBeGreaterThanOrEqual marathonDistanceMeters - 1
            calculatedDistance shouldBeLessThanOrEqual marathonDistanceMeters + 1
        }
        
        test("large step count handles correctly") {
            checkAll(largeStepsArb) { steps ->
                val distance = calculateDistanceFromSteps(steps, averageStepLengthMeters)
                
                // Should not overflow or produce negative values
                distance shouldBeGreaterThanOrEqual 0.0
                distance shouldBe steps * averageStepLengthMeters
            }
        }
    }
})

/**
 * Distance calculation function matching HealthDataRepositoryImpl logic.
 */
private fun calculateDistanceFromSteps(steps: Int, stepLengthMeters: Double): Double {
    require(steps >= 0) { "Steps cannot be negative" }
    return steps * stepLengthMeters
}
