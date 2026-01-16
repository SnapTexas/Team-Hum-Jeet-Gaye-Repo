package com.healthtracker

import com.healthtracker.domain.model.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Common property-based test generators for the Health Tracker project.
 * 
 * These generators provide reusable arbitrary value generators for domain models,
 * ensuring consistent test data generation across all property-based tests.
 */
object TestGenerators {
    
    // ============================================
    // DATE AND TIME GENERATORS
    // ============================================
    
    /**
     * Generates random LocalDate within a reasonable range (2020-2030).
     */
    fun localDateArb(): Arb<LocalDate> = Arb.bind(
        Arb.int(2020..2030),
        Arb.int(1..12),
        Arb.int(1..28) // Use 28 to avoid invalid dates
    ) { year, month, day ->
        LocalDate.of(year, month, day)
    }
    
    /**
     * Generates random Instant within a reasonable range.
     */
    fun instantArb(): Arb<Instant> = localDateArb().map { date ->
        date.atStartOfDay().toInstant(ZoneOffset.UTC)
    }
    
    // ============================================
    // USER PROFILE GENERATORS
    // ============================================
    
    /**
     * Generates valid user names (1-100 chars, letters/spaces/hyphens/apostrophes).
     */
    fun validNameArb(): Arb<String> = Arb.string(1..100, Arb.element(
        ('a'..'z').toList() + ('A'..'Z').toList() + listOf(' ', '-', '\'')
    )).filter { it.isNotBlank() }
    
    /**
     * Generates valid ages (13-120 years).
     */
    fun validAgeArb(): Arb<Int> = Arb.int(13..120)
    
    /**
     * Generates valid weights (20-500 kg).
     */
    fun validWeightArb(): Arb<Float> = Arb.float(20f..500f)
    
    /**
     * Generates valid heights (50-300 cm).
     */
    fun validHeightArb(): Arb<Float> = Arb.float(50f..300f)
    
    /**
     * Generates random HealthGoal enum values.
     */
    fun healthGoalArb(): Arb<HealthGoal> = Arb.enum<HealthGoal>()
    
    /**
     * Generates valid UserProfile instances.
     */
    fun validUserProfileArb(): Arb<UserProfile> = Arb.bind(
        validNameArb(),
        validAgeArb(),
        validWeightArb(),
        validHeightArb(),
        healthGoalArb()
    ) { name, age, weight, height, goal ->
        UserProfile(name, age, weight, height, goal)
    }
    
    // ============================================
    // HEALTH METRICS GENERATORS
    // ============================================
    
    /**
     * Generates realistic step counts (0-30000).
     */
    fun stepsArb(): Arb<Int> = Arb.int(0..30000)
    
    /**
     * Generates realistic distances in meters (0-25000).
     */
    fun distanceArb(): Arb<Float> = Arb.float(0f..25000f)
    
    /**
     * Generates realistic calorie burns (0-5000).
     */
    fun caloriesArb(): Arb<Int> = Arb.int(0..5000)
    
    /**
     * Generates realistic sleep durations in minutes (0-960 = 16 hours).
     */
    fun sleepMinutesArb(): Arb<Int> = Arb.int(0..960)
    
    /**
     * Generates realistic screen time in minutes (0-1440 = 24 hours).
     */
    fun screenTimeMinutesArb(): Arb<Int> = Arb.int(0..1440)
    
    /**
     * Generates realistic heart rate values (40-200 bpm).
     */
    fun heartRateArb(): Arb<Int> = Arb.int(40..200)
    
    /**
     * Generates realistic HRV values (10-150 ms).
     */
    fun hrvArb(): Arb<Int> = Arb.int(10..150)
    
    /**
     * Generates realistic water intake in ml (0-5000).
     */
    fun waterIntakeArb(): Arb<Int> = Arb.int(0..5000)
    
    /**
     * Generates mood scores (1-10).
     */
    fun moodScoreArb(): Arb<Int> = Arb.int(1..10)
    
    // ============================================
    // NUTRITION GENERATORS
    // ============================================
    
    /**
     * Generates realistic calorie values for meals (0-2000).
     */
    fun mealCaloriesArb(): Arb<Float> = Arb.float(0f..2000f)
    
    /**
     * Generates realistic protein values in grams (0-200).
     */
    fun proteinArb(): Arb<Float> = Arb.float(0f..200f)
    
    /**
     * Generates realistic carb values in grams (0-300).
     */
    fun carbsArb(): Arb<Float> = Arb.float(0f..300f)
    
    /**
     * Generates realistic fat values in grams (0-150).
     */
    fun fatArb(): Arb<Float> = Arb.float(0f..150f)
    
    /**
     * Generates ML confidence scores (0.0-1.0).
     */
    fun confidenceArb(): Arb<Float> = Arb.float(0f..1f)
    
    // ============================================
    // LOCATION GENERATORS
    // ============================================
    
    /**
     * Generates valid latitude values (-90 to 90).
     */
    fun latitudeArb(): Arb<Double> = Arb.double(-90.0..90.0)
    
    /**
     * Generates valid longitude values (-180 to 180).
     */
    fun longitudeArb(): Arb<Double> = Arb.double(-180.0..180.0)
    
    /**
     * Generates realistic distances in kilometers (0-100).
     */
    fun distanceKmArb(): Arb<Double> = Arb.double(0.0..100.0)
    
    // ============================================
    // GAMIFICATION GENERATORS
    // ============================================
    
    /**
     * Generates streak counts (0-365).
     */
    fun streakArb(): Arb<Int> = Arb.int(0..365)
    
    /**
     * Generates points/scores (0-100000).
     */
    fun pointsArb(): Arb<Int> = Arb.int(0..100000)
    
    /**
     * Generates badge tier levels (1-5).
     */
    fun badgeTierArb(): Arb<Int> = Arb.int(1..5)
    
    // ============================================
    // STRING GENERATORS
    // ============================================
    
    /**
     * Generates non-empty alphanumeric strings (1-50 chars).
     */
    fun nonEmptyStringArb(): Arb<String> = Arb.string(1..50, Arb.alphanumeric())
    
    /**
     * Generates user IDs (UUID-like strings).
     */
    fun userIdArb(): Arb<String> = Arb.string(32..36, Arb.element(
        ('a'..'f').toList() + ('0'..'9').toList() + listOf('-')
    ))
    
    /**
     * Generates email addresses.
     */
    fun emailArb(): Arb<String> = Arb.bind(
        Arb.string(5..20, Arb.alphanumeric()),
        Arb.element("gmail.com", "yahoo.com", "outlook.com", "example.com")
    ) { username, domain ->
        "$username@$domain"
    }
    
    // ============================================
    // PERCENTAGE GENERATORS
    // ============================================
    
    /**
     * Generates percentage values (0-100).
     */
    fun percentageArb(): Arb<Float> = Arb.float(0f..100f)
    
    /**
     * Generates decimal percentage values (0.0-1.0).
     */
    fun decimalPercentageArb(): Arb<Float> = Arb.float(0f..1f)
    
    // ============================================
    // POSITIVE NUMBER GENERATORS
    // ============================================
    
    /**
     * Generates positive integers (1-10000).
     */
    fun positiveIntArb(): Arb<Int> = Arb.int(1..10000)
    
    /**
     * Generates positive floats (0.1-10000.0).
     */
    fun positiveFloatArb(): Arb<Float> = Arb.float(0.1f..10000f)
    
    /**
     * Generates positive doubles (0.1-10000.0).
     */
    fun positiveDoubleArb(): Arb<Double> = Arb.double(0.1..10000.0)
    
    // ============================================
    // DURATION GENERATORS
    // ============================================
    
    /**
     * Generates durations in minutes (1-180 = 3 hours).
     */
    fun durationMinutesArb(): Arb<Int> = Arb.int(1..180)
    
    /**
     * Generates durations in seconds (1-3600 = 1 hour).
     */
    fun durationSecondsArb(): Arb<Int> = Arb.int(1..3600)
}
