package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.CommonSymptoms
import com.healthtracker.domain.model.MEDICAL_DISCLAIMER
import com.healthtracker.domain.model.MedicalSpecialties
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.SymptomSeverity
import com.healthtracker.domain.model.TriageResult
import com.healthtracker.domain.model.UrgencyLevel
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.repository.TriageRepository
import com.healthtracker.domain.usecase.TriageUseCase
import javax.inject.Inject

/**
 * Implementation of TriageUseCase for health issue detection and specialist mapping.
 */
class TriageUseCaseImpl @Inject constructor(
    private val triageRepository: TriageRepository
) : TriageUseCase {
    
    override suspend fun mapSymptomsToSpecialists(
        symptoms: List<Symptom>
    ): Result<List<SpecialistRecommendation>> {
        if (symptoms.isEmpty()) {
            return Result.Success(emptyList())
        }
        
        // Calculate specialist relevance based on symptoms
        val specialistScores = mutableMapOf<String, MutableList<Pair<Float, String>>>()
        
        for (symptom in symptoms) {
            val template = CommonSymptoms.ALL.find { it.id == symptom.name.lowercase().replace(" ", "_") }
                ?: CommonSymptoms.ALL.find { it.displayName.equals(symptom.name, ignoreCase = true) }
            
            if (template != null) {
                // Use predefined specialist associations
                val severityMultiplier = when (symptom.severity) {
                    SymptomSeverity.MILD -> 0.7f
                    SymptomSeverity.MODERATE -> 0.85f
                    SymptomSeverity.SEVERE -> 1.0f
                }
                
                template.associatedSpecialists.forEachIndexed { index, specialistId ->
                    val baseScore = 1.0f - (index * 0.15f) // Primary specialist gets higher score
                    val score = (baseScore * severityMultiplier).coerceIn(0f, 1f)
                    val reasoning = buildReasoning(symptom, specialistId, index == 0)
                    
                    specialistScores.getOrPut(specialistId) { mutableListOf() }
                        .add(score to reasoning)
                }
            } else {
                // Default to general practitioner for unknown symptoms
                val score = when (symptom.severity) {
                    SymptomSeverity.MILD -> 0.6f
                    SymptomSeverity.MODERATE -> 0.75f
                    SymptomSeverity.SEVERE -> 0.9f
                }
                val reasoning = "General practitioner recommended for evaluation of ${symptom.name}"
                specialistScores.getOrPut("general_practitioner") { mutableListOf() }
                    .add(score to reasoning)
            }
        }
        
        // Aggregate scores and create recommendations
        val recommendations = specialistScores.mapNotNull { (specialistId, scores) ->
            val specialty = MedicalSpecialties.findById(specialistId) ?: return@mapNotNull null
            
            // Combine scores - take max score and combine reasonings
            val maxScore = scores.maxOf { it.first }
            val combinedReasoning = scores.map { it.second }.distinct().joinToString("; ")
            
            // Only include recommendations with relevance > 0.5
            if (maxScore > 0.5f) {
                SpecialistRecommendation(
                    specialtyId = specialistId,
                    specialtyName = specialty.name,
                    relevanceScore = maxScore,
                    reasoning = combinedReasoning
                )
            } else null
        }.sortedByDescending { it.relevanceScore }
        
        return Result.Success(recommendations)
    }
    
    override suspend fun findNearbyClinics(
        location: UserLocation,
        specialtyId: String?
    ): Result<List<Clinic>> {
        return when (val result = triageRepository.findNearbyClinics(location, specialtyId)) {
            is Result.Success -> {
                // Ensure results are sorted by distance ascending
                val sortedClinics = result.data.sortedBy { it.distanceMeters }
                Result.Success(sortedClinics)
            }
            is Result.Error -> result
        }
    }
    
    override suspend fun performTriage(symptoms: List<Symptom>): Result<TriageResult> {
        val recommendationsResult = mapSymptomsToSpecialists(symptoms)
        
        return when (recommendationsResult) {
            is Result.Success -> {
                val urgencyLevel = calculateUrgencyLevel(symptoms)
                Result.Success(
                    TriageResult(
                        symptoms = symptoms,
                        recommendations = recommendationsResult.data,
                        urgencyLevel = urgencyLevel,
                        disclaimer = MEDICAL_DISCLAIMER
                    )
                )
            }
            is Result.Error -> recommendationsResult
        }
    }
    
    override fun calculateUrgencyLevel(symptoms: List<Symptom>): UrgencyLevel {
        if (symptoms.isEmpty()) return UrgencyLevel.LOW
        
        val severeCounts = symptoms.count { it.severity == SymptomSeverity.SEVERE }
        val moderateCounts = symptoms.count { it.severity == SymptomSeverity.MODERATE }
        
        // Check for emergency symptoms
        val emergencySymptoms = setOf("chest_pain", "difficulty_breathing", "severe_bleeding", "unconsciousness")
        val hasEmergencySymptom = symptoms.any { symptom ->
            val normalizedName = symptom.name.lowercase().replace(" ", "_")
            emergencySymptoms.contains(normalizedName) && symptom.severity == SymptomSeverity.SEVERE
        }
        
        return when {
            hasEmergencySymptom -> UrgencyLevel.EMERGENCY
            severeCounts >= 2 -> UrgencyLevel.HIGH
            severeCounts == 1 || moderateCounts >= 3 -> UrgencyLevel.MODERATE
            else -> UrgencyLevel.LOW
        }
    }
    
    private fun buildReasoning(symptom: Symptom, specialistId: String, isPrimary: Boolean): String {
        val specialty = MedicalSpecialties.findById(specialistId)
        val specialtyName = specialty?.name ?: specialistId.replace("_", " ").capitalize()
        
        val severityText = when (symptom.severity) {
            SymptomSeverity.MILD -> "mild"
            SymptomSeverity.MODERATE -> "moderate"
            SymptomSeverity.SEVERE -> "severe"
        }
        
        val primaryText = if (isPrimary) "Primary recommendation" else "Alternative specialist"
        val bodyPartText = symptom.bodyPart?.let { " affecting the $it" } ?: ""
        
        return "$primaryText: $specialtyName for $severityText ${symptom.name}$bodyPartText"
    }
    
    private fun String.capitalize(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
