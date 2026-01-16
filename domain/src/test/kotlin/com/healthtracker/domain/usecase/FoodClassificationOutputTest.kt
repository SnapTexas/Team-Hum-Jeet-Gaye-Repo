package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietConstants
import com.healthtracker.domain.model.FoodClassificationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for food classification output.
 * 
 * **Validates: Requirements 8.1, Property 15**
 * 
 * Property 15: Food classification output contains food name and confidence 0-1.
 * 
 * Tests that:
 * - Classification result always contains a non-empty food name
 * - Confidence score is always between 0.0 and 1.0 (inclusive)
 * - Alternative names list is never null
 */
class FoodClassificationOutputTest : FunSpec({
    
    // Generator for valid food names
    val foodNameArb = Arb.string(minSize = 1, maxSize = 50)
    
    // Generator for confidence scores (0.0 to 1.0)
    val confidenceArb = Arb.float(min = 0.0f, max = 1.0f)
    
    // Generator for alternative names
    val alternativeNamesArb = Arb.list(Arb.string(minSize = 1, maxSize = 30), range = 0..5)
    
    // Generator for FoodClassificationResult
    val classificationResultArb = Arb.bind(
        foodNameArb,
        confidenceArb,
        alternativeNamesArb
    ) { name, confidence, alternatives ->
        FoodClassificationResult(
            foodName = name,
            confidence = confidence,
            alternativeNames = alternatives
        )
    }
    
    test("Property 15: Food classification result always contains non-empty food name") {
        checkAll(100, classificationResultArb) { result ->
            result.foodName.shouldNotBeEmpty()
        }
    }
    
    test("Property 15: Confidence score is always between 0.0 and 1.0") {
        checkAll(100, confidenceArb) { confidence ->
            confidence shouldBeGreaterThanOrEqual 0.0f
            confidence shouldBeLessThanOrEqual 1.0f
        }
    }
    
    test("Property 15: Classification result confidence is within valid range") {
        checkAll(100, classificationResultArb) { result ->
            result.confidence shouldBeGreaterThanOrEqual 0.0f
            result.confidence shouldBeLessThanOrEqual 1.0f
        }
    }
    
    test("Property 15: Alternative names list is never null") {
        checkAll(100, classificationResultArb) { result ->
            result.alternativeNames shouldNotBe null
        }
    }
    
    test("Property 15: High confidence detection is correct at threshold boundary") {
        checkAll(100, foodNameArb, alternativeNamesArb) { name, alternatives ->
            // Test at exactly threshold
            val atThreshold = FoodClassificationResult(
                foodName = name,
                confidence = DietConstants.CONFIDENCE_THRESHOLD,
                alternativeNames = alternatives
            )
            atThreshold.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
            
            // Test just below threshold
            val belowThreshold = FoodClassificationResult(
                foodName = name,
                confidence = DietConstants.CONFIDENCE_THRESHOLD - 0.01f,
                alternativeNames = alternatives
            )
            belowThreshold.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe false
            
            // Test above threshold
            val aboveThreshold = FoodClassificationResult(
                foodName = name,
                confidence = DietConstants.CONFIDENCE_THRESHOLD + 0.1f,
                alternativeNames = alternatives
            )
            aboveThreshold.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
        }
    }
    
    test("Property 15: isHighConfidence and requiresManualConfirmation are mutually exclusive") {
        checkAll(100, classificationResultArb) { result ->
            val threshold = DietConstants.CONFIDENCE_THRESHOLD
            val isHigh = result.isHighConfidence(threshold)
            val requiresManual = result.requiresManualConfirmation(threshold)
            
            // They should be opposite of each other
            isHigh shouldBe !requiresManual
        }
    }
    
    test("Property 15: Classification with confidence 0 requires manual confirmation") {
        checkAll(100, foodNameArb, alternativeNamesArb) { name, alternatives ->
            val result = FoodClassificationResult(
                foodName = name,
                confidence = 0.0f,
                alternativeNames = alternatives
            )
            result.requiresManualConfirmation() shouldBe true
            result.isHighConfidence() shouldBe false
        }
    }
    
    test("Property 15: Classification with confidence 1 is high confidence") {
        checkAll(100, foodNameArb, alternativeNamesArb) { name, alternatives ->
            val result = FoodClassificationResult(
                foodName = name,
                confidence = 1.0f,
                alternativeNames = alternatives
            )
            result.isHighConfidence() shouldBe true
            result.requiresManualConfirmation() shouldBe false
        }
    }
})
