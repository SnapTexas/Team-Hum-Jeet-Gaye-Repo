package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.TriageResult
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.model.Result

/**
 * Use case interface for health issue triage operations.
 */
interface TriageUseCase {
    
    /**
     * Maps reported symptoms to relevant medical specialists.
     * Returns recommendations with relevance scores above 0.5 and reasoning.
     * 
     * @param symptoms List of symptoms reported by the user
     * @return List of specialist recommendations sorted by relevance
     */
    suspend fun mapSymptomsToSpecialists(symptoms: List<Symptom>): Result<List<SpecialistRecommendation>>
    
    /**
     * Finds nearby clinics based on user location.
     * Results are sorted by distance in ascending order (nearest first).
     * 
     * @param location User's current location
     * @param specialtyId Optional specialty to filter clinics
     * @return List of clinics sorted by distance
     */
    suspend fun findNearbyClinics(
        location: UserLocation,
        specialtyId: String? = null
    ): Result<List<Clinic>>
    
    /**
     * Performs a complete triage assessment based on symptoms.
     * Includes specialist recommendations and urgency level.
     * 
     * @param symptoms List of symptoms reported by the user
     * @return Complete triage result with recommendations and urgency
     */
    suspend fun performTriage(symptoms: List<Symptom>): Result<TriageResult>
    
    /**
     * Calculates the urgency level based on symptom severity.
     * 
     * @param symptoms List of symptoms to assess
     * @return Urgency level for seeking medical care
     */
    fun calculateUrgencyLevel(symptoms: List<Symptom>): com.healthtracker.domain.model.UrgencyLevel
}
