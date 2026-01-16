package com.healthtracker.data.repository

import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.ClinicLocation
import com.healthtracker.domain.model.CommonSymptoms
import com.healthtracker.domain.model.MedicalSpecialties
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.SymptomSeverity
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.repository.TriageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Implementation of TriageRepository.
 * Uses OpenStreetMap Nominatim API for clinic search (FREE, no API key needed).
 */
@Singleton
class TriageRepositoryImpl @Inject constructor() : TriageRepository {
    
    private val httpClient = OkHttpClient()
    
    override suspend fun mapSymptomsToSpecialists(
        symptoms: List<Symptom>
    ): Result<List<SpecialistRecommendation>> = withContext(Dispatchers.IO) {
        try {
            if (symptoms.isEmpty()) {
                return@withContext Result.Success(emptyList())
            }
            
            val specialistScores = mutableMapOf<String, MutableList<Pair<Float, String>>>()
            
            for (symptom in symptoms) {
                val template = findSymptomTemplate(symptom)
                
                if (template != null) {
                    val severityMultiplier = getSeverityMultiplier(symptom.severity)
                    
                    template.associatedSpecialists.forEachIndexed { index, specialistId ->
                        val baseScore = 1.0f - (index * 0.15f)
                        val score = (baseScore * severityMultiplier).coerceIn(0f, 1f)
                        val reasoning = buildReasoning(symptom, specialistId, index == 0)
                        
                        specialistScores.getOrPut(specialistId) { mutableListOf() }
                            .add(score to reasoning)
                    }
                } else {
                    // Default to general practitioner
                    val score = getSeverityMultiplier(symptom.severity) * 0.8f
                    val reasoning = "General practitioner recommended for evaluation of ${symptom.name}"
                    specialistScores.getOrPut("general_practitioner") { mutableListOf() }
                        .add(score to reasoning)
                }
            }
            
            val recommendations = specialistScores.mapNotNull { (specialistId, scores) ->
                val specialty = MedicalSpecialties.findById(specialistId) ?: return@mapNotNull null
                val maxScore = scores.maxOf { it.first }
                val combinedReasoning = scores.map { it.second }.distinct().joinToString("; ")
                
                if (maxScore > 0.5f) {
                    SpecialistRecommendation(
                        specialtyId = specialistId,
                        specialtyName = specialty.name,
                        relevanceScore = maxScore,
                        reasoning = combinedReasoning
                    )
                } else null
            }.sortedByDescending { it.relevanceScore }
            
            Result.Success(recommendations)
        } catch (e: Exception) {
            Result.Error(AppException.UnknownException("Failed to map symptoms to specialists: ${e.message}"))
        }
    }
    
    /**
     * Finds nearby clinics using OpenStreetMap Nominatim API (FREE, no API key).
     * Searches for hospitals, clinics, and doctors near the user's location.
     */
    override suspend fun findNearbyClinics(
        location: UserLocation,
        specialtyId: String?,
        radiusMeters: Int
    ): Result<List<Clinic>> = withContext(Dispatchers.IO) {
        try {
            val clinics = mutableListOf<Clinic>()
            
            // Search for hospitals and clinics using Overpass API (OpenStreetMap)
            // This is completely FREE with no API key required
            val searchTypes = listOf("hospital", "clinic", "doctors")
            
            for (searchType in searchTypes) {
                try {
                    val overpassQuery = buildOverpassQuery(location, radiusMeters, searchType)
                    val results = executeOverpassQuery(overpassQuery)
                    clinics.addAll(results.map { it.copy(distanceMeters = calculateDistance(
                        location.latitude, location.longitude,
                        it.location.latitude, it.location.longitude
                    ))})
                } catch (e: Exception) {
                    Timber.w(e, "Failed to search for $searchType")
                }
            }
            
            // Sort by distance and filter by specialty if provided
            val sortedClinics = clinics
                .distinctBy { it.id }
                .filter { clinic ->
                    specialtyId == null || clinic.specialties.any { 
                        it.contains(specialtyId, ignoreCase = true) 
                    }
                }
                .sortedBy { it.distanceMeters }
                .take(20) // Limit results
            
            Timber.d("Found ${sortedClinics.size} clinics nearby")
            Result.Success(sortedClinics)
        } catch (e: Exception) {
            Timber.e(e, "Failed to find nearby clinics")
            Result.Error(AppException.UnknownException("Failed to find nearby clinics: ${e.message}"))
        }
    }
    
    /**
     * Builds Overpass API query for searching medical facilities.
     */
    private fun buildOverpassQuery(location: UserLocation, radiusMeters: Int, amenityType: String): String {
        return """
            [out:json][timeout:10];
            (
              node["amenity"="$amenityType"](around:$radiusMeters,${location.latitude},${location.longitude});
              way["amenity"="$amenityType"](around:$radiusMeters,${location.latitude},${location.longitude});
            );
            out center body;
        """.trimIndent()
    }
    
    /**
     * Executes Overpass API query and parses results.
     */
    private fun executeOverpassQuery(query: String): List<Clinic> {
        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "HealthTracker/1.0")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            Timber.w("Overpass API returned ${response.code}")
            return emptyList()
        }
        
        val responseBody = response.body?.string() ?: return emptyList()
        return parseOverpassResponse(responseBody)
    }
    
    /**
     * Parses Overpass API JSON response into Clinic objects.
     */
    private fun parseOverpassResponse(json: String): List<Clinic> {
        val clinics = mutableListOf<Clinic>()
        
        try {
            val jsonObject = org.json.JSONObject(json)
            val elements = jsonObject.optJSONArray("elements") ?: return emptyList()
            
            for (i in 0 until elements.length()) {
                val element = elements.getJSONObject(i)
                val tags = element.optJSONObject("tags") ?: continue
                
                val name = tags.optString("name", "").ifEmpty { 
                    tags.optString("amenity", "Medical Facility").replaceFirstChar { it.uppercase() }
                }
                
                // Get coordinates (for ways, use center)
                val lat = element.optDouble("lat", 0.0).takeIf { it != 0.0 }
                    ?: element.optJSONObject("center")?.optDouble("lat", 0.0) ?: continue
                val lon = element.optDouble("lon", 0.0).takeIf { it != 0.0 }
                    ?: element.optJSONObject("center")?.optDouble("lon", 0.0) ?: continue
                
                if (lat == 0.0 || lon == 0.0) continue
                
                val clinic = Clinic(
                    id = "osm_${element.optLong("id", System.currentTimeMillis())}",
                    name = name,
                    address = buildAddress(tags),
                    location = ClinicLocation(lat, lon),
                    distanceMeters = 0.0, // Will be calculated later
                    rating = null, // OSM doesn't have ratings
                    phoneNumber = tags.optString("phone", null)?.takeIf { it.isNotEmpty() }
                        ?: tags.optString("contact:phone", null)?.takeIf { it.isNotEmpty() },
                    specialties = extractSpecialties(tags),
                    openNow = null // Would need opening_hours parsing
                )
                
                clinics.add(clinic)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse Overpass response")
        }
        
        return clinics
    }
    
    /**
     * Builds address string from OSM tags.
     */
    private fun buildAddress(tags: org.json.JSONObject): String {
        val parts = mutableListOf<String>()
        
        tags.optString("addr:housenumber", "").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tags.optString("addr:street", "").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tags.optString("addr:city", "").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        tags.optString("addr:postcode", "").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        
        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Address not available"
    }
    
    /**
     * Extracts specialties from OSM tags.
     */
    private fun extractSpecialties(tags: org.json.JSONObject): List<String> {
        val specialties = mutableListOf<String>()
        
        tags.optString("healthcare:speciality", "").takeIf { it.isNotEmpty() }?.let {
            specialties.addAll(it.split(";").map { s -> s.trim() })
        }
        tags.optString("healthcare", "").takeIf { it.isNotEmpty() }?.let {
            specialties.add(it)
        }
        tags.optString("amenity", "").takeIf { it.isNotEmpty() }?.let {
            specialties.add(it)
        }
        
        return specialties.distinct()
    }
    
    /**
     * Calculates distance between two points using Haversine formula.
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    override suspend fun getClinicDetails(clinicId: String): Result<Clinic> = withContext(Dispatchers.IO) {
        try {
            // For OSM data, we don't have detailed clinic info beyond what's in the list
            Result.Error(AppException.UnknownException("Clinic details - tap to open in Maps app"))
        } catch (e: Exception) {
            Result.Error(AppException.UnknownException("Failed to get clinic details: ${e.message}"))
        }
    }
    
    private fun findSymptomTemplate(symptom: Symptom) = 
        CommonSymptoms.ALL.find { 
            it.id == symptom.name.lowercase().replace(" ", "_") ||
            it.displayName.equals(symptom.name, ignoreCase = true)
        }
    
    private fun getSeverityMultiplier(severity: SymptomSeverity): Float = when (severity) {
        SymptomSeverity.MILD -> 0.7f
        SymptomSeverity.MODERATE -> 0.85f
        SymptomSeverity.SEVERE -> 1.0f
    }
    
    private fun buildReasoning(symptom: Symptom, specialistId: String, isPrimary: Boolean): String {
        val specialty = MedicalSpecialties.findById(specialistId)
        val specialtyName = specialty?.name ?: specialistId.replace("_", " ")
        val severityText = symptom.severity.name.lowercase()
        val primaryText = if (isPrimary) "Primary recommendation" else "Alternative specialist"
        val bodyPartText = symptom.bodyPart?.let { " affecting the $it" } ?: ""
        
        return "$primaryText: $specialtyName for $severityText ${symptom.name}$bodyPartText"
    }
}
