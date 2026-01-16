package com.healthtracker.domain.repository

import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.model.Result

/**
 * Repository interface for triage-related operations.
 */
interface TriageRepository {
    
    /**
     * Maps symptoms to relevant medical specialists with relevance scoring.
     * 
     * @param symptoms List of symptoms reported by the user
     * @return List of specialist recommendations sorted by relevance
     */
    suspend fun mapSymptomsToSpecialists(symptoms: List<Symptom>): Result<List<SpecialistRecommendation>>
    
    /**
     * Finds nearby clinics based on user location and optional specialty filter.
     * 
     * @param location User's current location
     * @param specialtyId Optional specialty to filter clinics
     * @param radiusMeters Search radius in meters (default 10km)
     * @return List of clinics sorted by distance
     */
    suspend fun findNearbyClinics(
        location: UserLocation,
        specialtyId: String? = null,
        radiusMeters: Int = 10000
    ): Result<List<Clinic>>
    
    /**
     * Gets clinic details by ID.
     * 
     * @param clinicId The clinic's unique identifier
     * @return Clinic details or error
     */
    suspend fun getClinicDetails(clinicId: String): Result<Clinic>
}
