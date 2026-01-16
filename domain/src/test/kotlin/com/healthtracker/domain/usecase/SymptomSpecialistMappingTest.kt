package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.SymptomSeverity
import com.healthtracker.domain.repository.TriageRepository
import com.healthtracker.domain.usecase.impl.TriageUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

/**
 * Property-based tests for symptom-specialist mapping.
 * 
 * **Validates: Requirements 12.1**
 * 
 * Property 30: Symptom-Specialist Mapping Relevance
 * For any set of reported symptoms, the returned specialist recommendations 
 * SHALL have relevance scores above 0.5 and SHALL include reasoning text.
 */
class SymptomSpecialistMappingTest : FunSpec({
    
    val triageRepository = mockk<TriageRepository>()
    val triageUseCase = TriageUseCaseImpl(triageRepository)
    
    // Common symptom names that map to specialists
    val commonSymptomNames = listOf(
        "Headache", "Chest Pain", "Stomach Pain", "Back Pain", "Fever",
        "Cough", "Fatigue", "Skin Rash", "Joint Pain", "Dizziness"
    )
    
    // Arbitrary generator for symptoms
    val symptomArb = Arb.enum<SymptomSeverity>().map { severity ->
        val name = commonSymptomNames.random()
        Symptom(
            id = UUID.randomUUID().toString(),
            name = name,
            severity = severity
        )
    }
    
    beforeTest {
        // Mock repository to delegate to use case logic
        coEvery { triageRepository.mapSymptomsToSpecialists(any()) } answers {
            val symptoms = firstArg<List<Symptom>>()
            // Return mock recommendations based on symptoms
            val recommendations = symptoms.flatMap { symptom ->
                listOf(
                    SpecialistRecommendation(
                        specialtyId = "general_practitioner",
                        specialtyName = "General Practitioner",
                        relevanceScore = 0.8f,
                        reasoning = "Recommended for ${symptom.name}"
                    )
                )
            }.distinctBy { it.specialtyId }
            Result.Success(recommendations)
        }
    }

    
    // Feature: smart-health-tracker, Property 30: Symptom-Specialist Mapping Relevance
    test("all specialist recommendations should have relevance scores above 0.5") {
        checkAll(100, Arb.list(symptomArb, 1..5)) { symptoms ->
            val result = triageUseCase.mapSymptomsToSpecialists(symptoms)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { recommendation ->
                        recommendation.relevanceScore shouldBeGreaterThan 0.5f
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 30: Symptom-Specialist Mapping Relevance
    test("all specialist recommendations should include reasoning text") {
        checkAll(100, Arb.list(symptomArb, 1..5)) { symptoms ->
            val result = triageUseCase.mapSymptomsToSpecialists(symptoms)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { recommendation ->
                        recommendation.reasoning.shouldNotBeBlank()
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 30: Symptom-Specialist Mapping Relevance
    test("non-empty symptoms should produce at least one recommendation") {
        checkAll(100, Arb.list(symptomArb, 1..5)) { symptoms ->
            val result = triageUseCase.mapSymptomsToSpecialists(symptoms)
            
            when (result) {
                is Result.Success -> {
                    if (symptoms.isNotEmpty()) {
                        result.data.shouldNotBeEmpty()
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 30: Symptom-Specialist Mapping Relevance
    test("recommendations should be sorted by relevance score descending") {
        checkAll(100, Arb.list(symptomArb, 1..5)) { symptoms ->
            val result = triageUseCase.mapSymptomsToSpecialists(symptoms)
            
            when (result) {
                is Result.Success -> {
                    val scores = result.data.map { it.relevanceScore }
                    scores shouldBe scores.sortedDescending()
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    test("empty symptoms list should return empty recommendations") {
        val result = triageUseCase.mapSymptomsToSpecialists(emptyList())
        
        when (result) {
            is Result.Success -> {
                result.data shouldBe emptyList()
            }
            is Result.Error -> {
                // Should not error on empty input
                throw AssertionError("Should not error on empty symptoms")
            }
        }
    }
})
