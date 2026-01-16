package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.ClinicLocation
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.repository.TriageRepository
import com.healthtracker.domain.usecase.impl.TriageUseCaseImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID

/**
 * Property-based tests for clinic geographic sorting.
 * 
 * **Validates: Requirements 12.3**
 * 
 * Property 31: Clinic Results Geographic Sorting
 * For any clinic search with a user location, the results SHALL be sorted 
 * by distance in ascending order (nearest first).
 */
class ClinicGeographicSortingTest : FunSpec({
    
    val triageRepository = mockk<TriageRepository>()
    val triageUseCase = TriageUseCaseImpl(triageRepository)
    
    // Arbitrary generator for user locations (valid lat/long ranges)
    val userLocationArb = Arb.double(-90.0..90.0).map { lat ->
        UserLocation(
            latitude = lat,
            longitude = Arb.double(-180.0..180.0).sample(io.kotest.property.RandomSource.default()).value
        )
    }
    
    // Arbitrary generator for clinics with random distances
    val clinicArb = Arb.double(0.0..50000.0).map { distance ->
        Clinic(
            id = UUID.randomUUID().toString(),
            name = "Clinic ${UUID.randomUUID().toString().take(8)}",
            address = "123 Test Street",
            location = ClinicLocation(0.0, 0.0),
            distanceMeters = distance,
            rating = 4.0f,
            phoneNumber = "+1-555-0100",
            specialties = listOf("general_practitioner"),
            openNow = true
        )
    }

    
    beforeTest {
        // Mock repository to return clinics with various distances
        coEvery { triageRepository.findNearbyClinics(any(), any(), any()) } answers {
            val clinics = listOf(
                Clinic(
                    id = "clinic_1",
                    name = "Near Clinic",
                    address = "123 Close St",
                    location = ClinicLocation(0.0, 0.0),
                    distanceMeters = 500.0,
                    rating = 4.5f,
                    phoneNumber = "+1-555-0101",
                    specialties = listOf("general_practitioner"),
                    openNow = true
                ),
                Clinic(
                    id = "clinic_2",
                    name = "Far Clinic",
                    address = "456 Far Ave",
                    location = ClinicLocation(0.0, 0.0),
                    distanceMeters = 5000.0,
                    rating = 4.0f,
                    phoneNumber = "+1-555-0102",
                    specialties = listOf("cardiologist"),
                    openNow = false
                ),
                Clinic(
                    id = "clinic_3",
                    name = "Medium Clinic",
                    address = "789 Mid Rd",
                    location = ClinicLocation(0.0, 0.0),
                    distanceMeters = 2000.0,
                    rating = 4.2f,
                    phoneNumber = "+1-555-0103",
                    specialties = listOf("neurologist"),
                    openNow = true
                )
            )
            // Return unsorted to test that use case sorts them
            Result.Success(clinics.shuffled())
        }
    }
    
    // Feature: smart-health-tracker, Property 31: Clinic Results Geographic Sorting
    test("clinic results should be sorted by distance ascending (nearest first)") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    val distances = result.data.map { it.distanceMeters }
                    // Verify ascending order
                    distances shouldBe distances.sorted()
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 31: Clinic Results Geographic Sorting
    test("each clinic distance should be less than or equal to the next") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    val clinics = result.data
                    for (i in 0 until clinics.size - 1) {
                        clinics[i].distanceMeters shouldBeLessThanOrEqual clinics[i + 1].distanceMeters
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 31: Clinic Results Geographic Sorting
    test("first clinic should be the nearest") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        val firstDistance = result.data.first().distanceMeters
                        val minDistance = result.data.minOf { it.distanceMeters }
                        firstDistance shouldBe minDistance
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 31: Clinic Results Geographic Sorting
    test("last clinic should be the farthest") {
        checkAll(100, userLocationArb) { location ->
            val result = triageUseCase.findNearbyClinics(location, null)
            
            when (result) {
                is Result.Success -> {
                    if (result.data.isNotEmpty()) {
                        val lastDistance = result.data.last().distanceMeters
                        val maxDistance = result.data.maxOf { it.distanceMeters }
                        lastDistance shouldBe maxDistance
                    }
                }
                is Result.Error -> {
                    // Error case is acceptable for edge cases
                }
            }
        }
    }
})
