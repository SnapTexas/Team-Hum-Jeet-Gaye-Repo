package com.healthtracker.domain.model

import java.time.Duration

/**
 * Represents a symptom reported by the user for triage purposes.
 */
data class Symptom(
    val id: String,
    val name: String,
    val severity: SymptomSeverity,
    val duration: Duration? = null,
    val bodyPart: String? = null
)

/**
 * Severity levels for reported symptoms.
 */
enum class SymptomSeverity {
    MILD,
    MODERATE,
    SEVERE
}

/**
 * Represents a specialist recommendation based on reported symptoms.
 */
data class SpecialistRecommendation(
    val specialtyId: String,
    val specialtyName: String,
    val relevanceScore: Float,
    val reasoning: String
)

/**
 * Represents a clinic or medical facility.
 */
data class Clinic(
    val id: String,
    val name: String,
    val address: String,
    val location: ClinicLocation,
    val distanceMeters: Double,
    val rating: Float? = null,
    val phoneNumber: String? = null,
    val specialties: List<String> = emptyList(),
    val openNow: Boolean? = null
)

/**
 * Geographic location for a clinic.
 */
data class ClinicLocation(
    val latitude: Double,
    val longitude: Double
)

/**
 * User's current location for clinic search.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

/**
 * Predefined symptom categories for easier selection.
 */
enum class SymptomCategory {
    HEAD,
    CHEST,
    ABDOMEN,
    LIMBS,
    SKIN,
    GENERAL
}

/**
 * Common symptoms with their associated body parts and typical specialists.
 */
object CommonSymptoms {
    val HEADACHE = SymptomTemplate("headache", "Headache", "HEAD", listOf("neurologist", "general_practitioner"))
    val CHEST_PAIN = SymptomTemplate("chest_pain", "Chest Pain", "CHEST", listOf("cardiologist", "pulmonologist"))
    val STOMACH_PAIN = SymptomTemplate("stomach_pain", "Stomach Pain", "ABDOMEN", listOf("gastroenterologist", "general_practitioner"))
    val BACK_PAIN = SymptomTemplate("back_pain", "Back Pain", "BACK", listOf("orthopedist", "physiotherapist"))
    val FEVER = SymptomTemplate("fever", "Fever", null, listOf("general_practitioner", "infectious_disease"))
    val COUGH = SymptomTemplate("cough", "Cough", "CHEST", listOf("pulmonologist", "general_practitioner"))
    val FATIGUE = SymptomTemplate("fatigue", "Fatigue", null, listOf("general_practitioner", "endocrinologist"))
    val SKIN_RASH = SymptomTemplate("skin_rash", "Skin Rash", "SKIN", listOf("dermatologist"))
    val JOINT_PAIN = SymptomTemplate("joint_pain", "Joint Pain", "LIMBS", listOf("rheumatologist", "orthopedist"))
    val DIZZINESS = SymptomTemplate("dizziness", "Dizziness", "HEAD", listOf("neurologist", "ent_specialist"))
    
    val ALL = listOf(
        HEADACHE, CHEST_PAIN, STOMACH_PAIN, BACK_PAIN, FEVER,
        COUGH, FATIGUE, SKIN_RASH, JOINT_PAIN, DIZZINESS
    )
}

/**
 * Template for common symptoms with associated specialists.
 */
data class SymptomTemplate(
    val id: String,
    val displayName: String,
    val bodyPart: String?,
    val associatedSpecialists: List<String>
)

/**
 * Medical specialties with their descriptions.
 */
object MedicalSpecialties {
    val GENERAL_PRACTITIONER = Specialty("general_practitioner", "General Practitioner", "Primary care physician for general health concerns")
    val CARDIOLOGIST = Specialty("cardiologist", "Cardiologist", "Heart and cardiovascular system specialist")
    val NEUROLOGIST = Specialty("neurologist", "Neurologist", "Brain and nervous system specialist")
    val GASTROENTEROLOGIST = Specialty("gastroenterologist", "Gastroenterologist", "Digestive system specialist")
    val PULMONOLOGIST = Specialty("pulmonologist", "Pulmonologist", "Lung and respiratory system specialist")
    val DERMATOLOGIST = Specialty("dermatologist", "Dermatologist", "Skin specialist")
    val ORTHOPEDIST = Specialty("orthopedist", "Orthopedist", "Bone and joint specialist")
    val RHEUMATOLOGIST = Specialty("rheumatologist", "Rheumatologist", "Autoimmune and joint disease specialist")
    val ENDOCRINOLOGIST = Specialty("endocrinologist", "Endocrinologist", "Hormone and metabolism specialist")
    val ENT_SPECIALIST = Specialty("ent_specialist", "ENT Specialist", "Ear, nose, and throat specialist")
    val PHYSIOTHERAPIST = Specialty("physiotherapist", "Physiotherapist", "Physical therapy and rehabilitation specialist")
    val INFECTIOUS_DISEASE = Specialty("infectious_disease", "Infectious Disease Specialist", "Specialist in infections and communicable diseases")
    
    val ALL = listOf(
        GENERAL_PRACTITIONER, CARDIOLOGIST, NEUROLOGIST, GASTROENTEROLOGIST,
        PULMONOLOGIST, DERMATOLOGIST, ORTHOPEDIST, RHEUMATOLOGIST,
        ENDOCRINOLOGIST, ENT_SPECIALIST, PHYSIOTHERAPIST, INFECTIOUS_DISEASE
    )
    
    fun findById(id: String): Specialty? = ALL.find { it.id == id }
}

/**
 * Represents a medical specialty.
 */
data class Specialty(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Result of a triage assessment.
 */
data class TriageResult(
    val symptoms: List<Symptom>,
    val recommendations: List<SpecialistRecommendation>,
    val urgencyLevel: UrgencyLevel,
    val disclaimer: String = MEDICAL_DISCLAIMER
)

/**
 * Urgency level for seeking medical care.
 */
enum class UrgencyLevel {
    LOW,        // Can wait for regular appointment
    MODERATE,   // Should see a doctor within a few days
    HIGH,       // Should see a doctor today
    EMERGENCY   // Seek emergency care immediately
}

/**
 * Medical disclaimer to be displayed prominently.
 */
val MEDICAL_DISCLAIMER = """
IMPORTANT MEDICAL DISCLAIMER:
This app provides general health information and is NOT a substitute for professional medical advice, diagnosis, or treatment. Always seek the advice of your physician or other qualified health provider with any questions you may have regarding a medical condition. Never disregard professional medical advice or delay in seeking it because of something you have read in this app.

If you think you may have a medical emergency, call your doctor, go to the emergency department, or call emergency services immediately.
""".trimIndent()
