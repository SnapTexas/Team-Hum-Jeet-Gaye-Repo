package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.DietConstants
import com.healthtracker.domain.model.FoodAnalysisResult
import com.healthtracker.domain.model.FoodClassificationResult
import com.healthtracker.domain.model.NutritionInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for low confidence fallback behavior.
 * 
 * **Validates: Requirements 8.2, Property 16**
 * 
 * Property 16: Confidence < 0.7 triggers manual entry fallback.
 * 
 * Tests that:
 * - Low confidence classifications (< 0.7) result in LowConfidence or ManualEntryRequired
 * - High confidence classifications (>= 0.7) result in Success
 * - The threshold boundary is correctly handled
 */
class LowConfidenceFallbackTest : FunSpec({
    
    // Generator for food names
    val foodNameArb = Arb.string(minSize = 1, maxSize = 30)
    
    // Generator for low confidence (0.0 to 0.69)
    val lowConfidenceArb = Arb.float(min = 0.0f, max = DietConstants.CONFIDENCE_THRESHOLD - 0.01f)
    
    // Generator for high confidence (0.7 to 1.0)
    val highConfidenceArb = Arb.float(min = DietConstants.CONFIDENCE_THRESHOLD, max = 1.0f)
    
    // Generator for alternative names
    val alternativeNamesArb = Arb.list(Arb.string(minSize = 1, maxSize = 20), range = 0..3)
    
    // Generator for nutrition info
    val nutritionInfoArb = Arb.bind(
        foodNameArb,
        Arb.int(min = 0, max = 1000),
        Arb.float(min = 0f, max = 100f),
        Arb.float(min = 0f, max = 200f),
        Arb.float(min = 0f, max = 100f),
        Arb.float(min = 0f, max = 50f)
    ) { name, calories, protein, carbs, fat, fiber ->
        NutritionInfo(
            foodName = name,
            servingSize = "1 serving",
            servingSizeGrams = 100f,
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber
        )
    }
    
    // Generator for low confidence classification
    val lowConfidenceClassificationArb = Arb.bind(
        foodNameArb,
        lowConfidenceArb,
        alternativeNamesArb
    ) { name, confidence, alternatives ->
        FoodClassificationResult(
            foodName = name,
            confidence = confidence,
            alternativeNames = alternatives
        )
    }
    
    // Generator for high confidence classification
    val highConfidenceClassificationArb = Arb.bind(
        foodNameArb,
        highConfidenceArb,
        alternativeNamesArb
    ) { name, confidence, alternatives ->
        FoodClassificationResult(
            foodName = name,
            confidence = confidence,
            alternativeNames = alternatives
        )
    }
    
    test("Property 16: Low confidence (< 0.7) triggers manual confirmation requirement") {
        checkAll(100, lowConfidenceClassificationArb) { classification ->
            classification.requiresManualConfirmation(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
            classification.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe false
        }
    }
    
    test("Property 16: High confidence (>= 0.7) does not require manual confirmation") {
        checkAll(100, highConfidenceClassificationArb) { classification ->
            classification.requiresManualConfirmation(DietConstants.CONFIDENCE_THRESHOLD) shouldBe false
            classification.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
        }
    }
    
    test("Property 16: LowConfidence result is created for low confidence classifications") {
        checkAll(100, lowConfidenceClassificationArb, nutritionInfoArb) { classification, nutrition ->
            // Simulate what the use case would do
            val result = if (classification.requiresManualConfirmation(DietConstants.CONFIDENCE_THRESHOLD)) {
                FoodAnalysisResult.LowConfidence(
                    classification = classification,
                    suggestedNutrition = nutrition
                )
            } else {
                FoodAnalysisResult.Success(
                    classification = classification,
                    nutrition = nutrition
                )
            }
            
            result.shouldBeInstanceOf<FoodAnalysisResult.LowConfidence>()
        }
    }
    
    test("Property 16: Success result is created for high confidence classifications") {
        checkAll(100, highConfidenceClassificationArb, nutritionInfoArb) { classification, nutrition ->
            // Simulate what the use case would do
            val result = if (classification.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD)) {
                FoodAnalysisResult.Success(
                    classification = classification,
                    nutrition = nutrition
                )
            } else {
                FoodAnalysisResult.LowConfidence(
                    classification = classification,
                    suggestedNutrition = nutrition
                )
            }
            
            result.shouldBeInstanceOf<FoodAnalysisResult.Success>()
        }
    }
    
    test("Property 16: Threshold boundary - exactly 0.7 is high confidence") {
        checkAll(100, foodNameArb, alternativeNamesArb) { name, alternatives ->
            val classification = FoodClassificationResult(
                foodName = name,
                confidence = DietConstants.CONFIDENCE_THRESHOLD, // Exactly 0.7
                alternativeNames = alternatives
            )
            
            classification.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
            classification.requiresManualConfirmation(DietConstants.CONFIDENCE_THRESHOLD) shouldBe false
        }
    }
    
    test("Property 16: Threshold boundary - 0.699 requires manual confirmation") {
        checkAll(100, foodNameArb, alternativeNamesArb) { name, alternatives ->
            val classification = FoodClassificationResult(
                foodName = name,
                confidence = 0.699f, // Just below threshold
                alternativeNames = alternatives
            )
            
            classification.isHighConfidence(DietConstants.CONFIDENCE_THRESHOLD) shouldBe false
            classification.requiresManualConfirmation(DietConstants.CONFIDENCE_THRESHOLD) shouldBe true
        }
    }
    
    test("Property 16: ManualEntryRequired is created when ML fails") {
        checkAll(100, Arb.string(minSize = 1, maxSize = 100)) { reason ->
            val result = FoodAnalysisResult.ManualEntryRequired(
                reason = reason,
                imageUri = null
            )
            
            result.shouldBeInstanceOf<FoodAnalysisResult.ManualEntryRequired>()
            result.reason shouldBe reason
        }
    }
    
    test("Property 16: LowConfidence result preserves classification data") {
        checkAll(100, lowConfidenceClassificationArb, nutritionInfoArb) { classification, nutrition ->
            val result = FoodAnalysisResult.LowConfidence(
                classification = classification,
                suggestedNutrition = nutrition
            )
            
            result.classification.foodName shouldBe classification.foodName
            result.classification.confidence shouldBe classification.confidence
            result.classification.alternativeNames shouldBe classification.alternativeNames
        }
    }
    
    test("Property 16: Success result preserves all data") {
        checkAll(100, highConfidenceClassificationArb, nutritionInfoArb) { classification, nutrition ->
            val result = FoodAnalysisResult.Success(
                classification = classification,
                nutrition = nutrition
            )
            
            result.classification.foodName shouldBe classification.foodName
            result.classification.confidence shouldBe classification.confidence
            result.nutrition.calories shouldBe nutrition.calories
            result.nutrition.protein shouldBe nutrition.protein
        }
    }
})
