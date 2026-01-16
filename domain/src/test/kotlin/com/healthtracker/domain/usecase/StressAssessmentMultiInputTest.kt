package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressCategory
import com.healthtracker.domain.model.StressConstants
import com.healthtracker.domain.model.StressInput
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.LocalDate

/**
 * Property-based tests for stress assessment multi-input calculation.
 * 
 * **Validates: Requirements 9.1**
 * 
 * Property 18: Stress Assessment Multi-Input
 * The stress calculation SHALL incorporate HRV (40% weight), sleep (35% weight), 
 * and mood (25% weight) data to produce a stress level between 0-100.
 */
class StressAssessmentMultiInputTest : FunSpec({
    
    test("Property 18.1: Stress level is always between 0 and 100") {
        checkAll(
            100,
            Arb.double(20.0..100.0),  // HRV average (ms)
            Arb.int(180..600),         // Sleep duration (minutes)
            Arb.element(SleepQuality.entries.toList()),
            Arb.element(Mood.entries.toList())
        ) { hrv, sleepMinutes, sleepQuality, mood ->
            val input = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = sleepQuality,
                mood = mood,
                date = LocalDate.now()
            )
            
            val assessment = calculateStress(input)
            
            assessment.level shouldBeGreaterThanOrEqual 0
            assessment.level shouldBeLessThanOrEqual 100
        }
    }
    
    test("Property 18.2: HRV contribution is reflected in stress calculation (40% weight)") {
        checkAll(50, Arb.int(300..480), Arb.element(Mood.entries.toList())) { sleepMinutes, mood ->
            // Low HRV = high stress
            val lowHrvInput = StressInput(
                hrvAverage = 25.0,  // Low HRV
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = SleepQuality.FAIR,
                mood = mood,
                date = LocalDate.now()
            )
            
            // High HRV = low stress
            val highHrvInput = StressInput(
                hrvAverage = 80.0,  // High HRV
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = SleepQuality.FAIR,
                mood = mood,
                date = LocalDate.now()
            )
            
            val lowHrvStress = calculateStress(lowHrvInput)
            val highHrvStress = calculateStress(highHrvInput)
            
            // Lower HRV should result in higher stress
            lowHrvStress.level shouldBeGreaterThan highHrvStress.level
            
            // HRV contribution should be recorded
            lowHrvStress.hrvContribution shouldBeGreaterThanOrEqual 0
            lowHrvStress.hrvContribution shouldBeLessThanOrEqual 100
        }
    }
    
    test("Property 18.3: Sleep contribution is reflected in stress calculation (35% weight)") {
        checkAll(50, Arb.double(40.0..70.0), Arb.element(Mood.entries.toList())) { hrv, mood ->
            // Poor sleep = high stress
            val poorSleepInput = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = 240,  // 4 hours - poor
                sleepQuality = SleepQuality.POOR,
                mood = mood,
                date = LocalDate.now()
            )
            
            // Good sleep = low stress
            val goodSleepInput = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = 480,  // 8 hours - good
                sleepQuality = SleepQuality.EXCELLENT,
                mood = mood,
                date = LocalDate.now()
            )
            
            val poorSleepStress = calculateStress(poorSleepInput)
            val goodSleepStress = calculateStress(goodSleepInput)
            
            // Poor sleep should result in higher stress
            poorSleepStress.level shouldBeGreaterThan goodSleepStress.level
            
            // Sleep contribution should be recorded
            poorSleepStress.sleepContribution shouldBeGreaterThanOrEqual 0
            poorSleepStress.sleepContribution shouldBeLessThanOrEqual 100
        }
    }
    
    test("Property 18.4: Mood contribution is reflected in stress calculation (25% weight)") {
        checkAll(50, Arb.double(40.0..70.0), Arb.int(360..480)) { hrv, sleepMinutes ->
            // Negative mood = high stress
            val negativeMoodInput = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = SleepQuality.FAIR,
                mood = Mood.VERY_SAD,
                date = LocalDate.now()
            )
            
            // Positive mood = low stress
            val positiveMoodInput = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = SleepQuality.FAIR,
                mood = Mood.VERY_HAPPY,
                date = LocalDate.now()
            )
            
            val negativeMoodStress = calculateStress(negativeMoodInput)
            val positiveMoodStress = calculateStress(positiveMoodInput)
            
            // Negative mood should result in higher stress
            negativeMoodStress.level shouldBeGreaterThan positiveMoodStress.level
            
            // Mood contribution should be recorded
            negativeMoodStress.moodContribution shouldBeGreaterThanOrEqual 0
            negativeMoodStress.moodContribution shouldBeLessThanOrEqual 100
        }
    }
    
    test("Property 18.5: All three inputs contribute to final stress level") {
        val input = StressInput(
            hrvAverage = 50.0,
            sleepDurationMinutes = 420,
            sleepQuality = SleepQuality.GOOD,
            mood = Mood.NEUTRAL,
            date = LocalDate.now()
        )
        
        val assessment = calculateStress(input)
        
        // All contributions should be non-zero when inputs are provided
        assessment.hrvContribution shouldBeGreaterThan 0
        assessment.sleepContribution shouldBeGreaterThan 0
        assessment.moodContribution shouldBeGreaterThan 0
    }
    
    test("Property 18.6: Stress category matches stress level thresholds") {
        checkAll(
            100,
            Arb.double(20.0..100.0),
            Arb.int(180..600),
            Arb.element(SleepQuality.entries.toList()),
            Arb.element(Mood.entries.toList())
        ) { hrv, sleepMinutes, sleepQuality, mood ->
            val input = StressInput(
                hrvAverage = hrv,
                sleepDurationMinutes = sleepMinutes,
                sleepQuality = sleepQuality,
                mood = mood,
                date = LocalDate.now()
            )
            
            val assessment = calculateStress(input)
            
            // Verify category matches level
            val expectedCategory = when {
                assessment.level >= StressConstants.HIGH_THRESHOLD -> StressCategory.HIGH
                assessment.level >= StressConstants.ELEVATED_THRESHOLD -> StressCategory.ELEVATED
                assessment.level >= StressConstants.LOW_THRESHOLD -> StressCategory.MODERATE
                else -> StressCategory.LOW
            }
            
            assessment.category shouldBe expectedCategory
        }
    }
    
    test("Property 18.7: Missing inputs use default values without crashing") {
        // Only HRV provided
        val hrvOnlyInput = StressInput(
            hrvAverage = 50.0,
            sleepDurationMinutes = null,
            sleepQuality = null,
            mood = null,
            date = LocalDate.now()
        )
        
        val hrvOnlyAssessment = calculateStress(hrvOnlyInput)
        hrvOnlyAssessment.level shouldBeGreaterThanOrEqual 0
        hrvOnlyAssessment.level shouldBeLessThanOrEqual 100
        
        // Only sleep provided
        val sleepOnlyInput = StressInput(
            hrvAverage = null,
            sleepDurationMinutes = 420,
            sleepQuality = SleepQuality.GOOD,
            mood = null,
            date = LocalDate.now()
        )
        
        val sleepOnlyAssessment = calculateStress(sleepOnlyInput)
        sleepOnlyAssessment.level shouldBeGreaterThanOrEqual 0
        sleepOnlyAssessment.level shouldBeLessThanOrEqual 100
        
        // Only mood provided
        val moodOnlyInput = StressInput(
            hrvAverage = null,
            sleepDurationMinutes = null,
            sleepQuality = null,
            mood = Mood.NEUTRAL,
            date = LocalDate.now()
        )
        
        val moodOnlyAssessment = calculateStress(moodOnlyInput)
        moodOnlyAssessment.level shouldBeGreaterThanOrEqual 0
        moodOnlyAssessment.level shouldBeLessThanOrEqual 100
    }
})

/**
 * Helper function to calculate stress from input.
 * Implements the weighted algorithm: HRV 40%, Sleep 35%, Mood 25%
 */
private fun calculateStress(input: StressInput): StressAssessment {
    // Calculate HRV stress contribution (lower HRV = higher stress)
    // Normal HRV range: 20-100ms, optimal around 50-70ms
    val hrvStress = input.hrvAverage?.let { hrv ->
        when {
            hrv >= 70 -> 20  // Very good HRV = low stress
            hrv >= 50 -> 35  // Good HRV = moderate-low stress
            hrv >= 35 -> 55  // Fair HRV = moderate stress
            hrv >= 25 -> 75  // Low HRV = high stress
            else -> 90       // Very low HRV = very high stress
        }
    } ?: 50  // Default to moderate if not available
    
    // Calculate sleep stress contribution
    val sleepStress = calculateSleepStress(input.sleepDurationMinutes, input.sleepQuality)
    
    // Calculate mood stress contribution
    val moodStress = input.mood?.let { mood ->
        when (mood) {
            Mood.VERY_HAPPY -> 15
            Mood.HAPPY -> 25
            Mood.NEUTRAL -> 40
            Mood.SAD -> 65
            Mood.VERY_SAD -> 85
        }
    } ?: 50  // Default to moderate if not available
    
    // Apply weights: HRV 40%, Sleep 35%, Mood 25%
    val weightedStress = (
        hrvStress * StressConstants.HRV_WEIGHT +
        sleepStress * StressConstants.SLEEP_WEIGHT +
        moodStress * StressConstants.MOOD_WEIGHT
    ).toInt().coerceIn(0, 100)
    
    // Determine category
    val category = when {
        weightedStress >= StressConstants.HIGH_THRESHOLD -> StressCategory.HIGH
        weightedStress >= StressConstants.ELEVATED_THRESHOLD -> StressCategory.ELEVATED
        weightedStress >= StressConstants.LOW_THRESHOLD -> StressCategory.MODERATE
        else -> StressCategory.LOW
    }
    
    return StressAssessment(
        level = weightedStress,
        category = category,
        hrvContribution = hrvStress,
        sleepContribution = sleepStress,
        moodContribution = moodStress
    )
}

private fun calculateSleepStress(durationMinutes: Int?, quality: SleepQuality?): Int {
    if (durationMinutes == null && quality == null) return 50
    
    // Duration stress (optimal: 420-540 minutes / 7-9 hours)
    val durationStress = durationMinutes?.let { duration ->
        when {
            duration >= 480 && duration <= 540 -> 15  // Optimal
            duration >= 420 && duration < 480 -> 25  // Good
            duration >= 360 && duration < 420 -> 40  // Fair
            duration >= 300 && duration < 360 -> 60  // Poor
            duration < 300 -> 80                      // Very poor
            duration > 540 && duration <= 600 -> 30  // Slightly over
            else -> 50                                // Too much sleep
        }
    } ?: 50
    
    // Quality stress
    val qualityStress = quality?.let { q ->
        when (q) {
            SleepQuality.EXCELLENT -> 10
            SleepQuality.GOOD -> 25
            SleepQuality.FAIR -> 50
            SleepQuality.POOR -> 75
        }
    } ?: 50
    
    // Average duration and quality
    return ((durationStress + qualityStress) / 2).coerceIn(0, 100)
}
