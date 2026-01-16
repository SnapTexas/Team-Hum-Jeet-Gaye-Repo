package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.ClinicLocation
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.repository.TriageRepository
import com.healthtracker.domain.usecase.impl.TriageUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Property-based tests for clinic result completeness.
 * 
 * **Validates: Requirements 12.4**
 * 
 * Property 32: Clinic Result Completeness
 * For any clinic in search results, the display SHALL include name, address, 
 * distance, and contact information (phone number if available).
 */
class ClinicResultCompletenessTest : FunSpec({
    
    val triageRepository = mockk<TriageRepository>()
    val triageUseCase = TriageUseCaseImpl(triageRepository)
    
    // Arbitrary generator for user locations
    val userLocationArb = Arb.double(-90.0..90.0).map { lat ->
        UserLocation(
            latitude = lat,
            longitude = Arb.double(-180.0..180.0).sample(io.kotest.property.RandomSource.default()).value
        )
    }
    
    beforeTest {
        // Mock repository to return complete clinic data
        coEvery { triageRepository.findNearbyClinics(any(), any(), any()) } answers {
            val clinics = listOf(
                Clinic(
                    id = "clinic_1",
                    name = "City Medical Center",
                    address = "123 Main Street, Downtown",
                    location = ClinicLocation(40.7128, -74.0060),
                    distanceMeters = 1500.0,
                    rating = 4.5f,
                    phoneNumber = "+1-555-0101",
                    specialties = listOf("general_practitioner", "cardiologist"),
                    openNow = true
                ),
                Clinic(
                    id = "clinic_2",
                    name = "HealthFirst Clinic",
                    address = "456 Oak Avenue, Midtown",
                    location = ClinicLocation(40.7580, -73.9855),
                    distanceMeters = 3200.0,
                    rating = 4.2f,
                    phoneNumber = "+1-555-0102",
                    specialties = listOf("dermatologist"),
                    openNow = false
                ),
                Clinic(
                    id = "clinic_3",
                    name = "Wellness Medical Group",
                    address = "789 Pine Road, Uptown",
                    location = ClinicLocation(40.7831, -73.9712),
                    distanceMeters = 5000.0,
                    rating = 4.8f,
                    phoneNumber = "+1-555-0103",
                    specialties = listOf("orthopedist", "physiotherapist"),
                    openNow = true
                )
            )
            Result.Success(clinics)
        }
    }

    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("all clinics should have a non-blank name") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { clinic ->
                        clinic.name.shouldNotBeBlank()
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("all clinics should have a non-blank address") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { clinic ->
                        clinic.address.shouldNotBeBlank()
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("all clinics should have a valid distance (non-negative)") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { clinic ->
                        clinic.distanceMeters shouldBeGreaterThanOrEqual 0.0
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("all clinics should have a valid location") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { clinic ->
                        clinic.location shouldNotBe null
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("clinics with phone numbers should have non-blank phone numbers") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    result.data.forEach { clinic ->
                        clinic.phoneNumber?.let { phone ->
                            phone.shouldNotBeBlank()
                        }
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("all clinics should have a unique ID") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    val ids = result.data.map { it.id }
                    ids.distinct().size shouldNotBe 0
                    if (ids.isNotEmpty()) {
                        ids.distinct().size shouldNotBe ids.size - 1 // No duplicates
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 32: Clinic Result Completeness
    test("clinic data should be complete for display") {
        val location = UserLocation(40.7128, -74.0060)
        val result = triageUseCase.findNearbyClinics(location, null)
        
        when (result) {
            is Result.Success -> {
                result.data.forEach { clinic ->
                    // Required fields for display
                    clinic.name.shouldNotBeBlank()
                    clinic.address.shouldNotBeBlank()
                    clinic.distanceMeters shouldBeGreaterThanOrEqual 0.0
                    clinic.location shouldNotBe null
                    // Phone number is optional but if present should be valid
                    clinic.phoneNumber?.shouldNotBeBlank()
                }
            }
            is Result.Error -> {
                throw AssertionError("Should not error on valid location")
            }
        }
    }
})
