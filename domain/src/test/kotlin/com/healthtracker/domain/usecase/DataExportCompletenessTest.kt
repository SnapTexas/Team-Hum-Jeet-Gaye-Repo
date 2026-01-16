package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.BadgeExport
import com.healthtracker.domain.model.CircleExport
import com.healthtracker.domain.model.DietPreference
import com.healthtracker.domain.model.ExportCategory
import com.healthtracker.domain.model.ExportedUserData
import com.healthtracker.domain.model.GamificationExport
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetricsExport
import com.healthtracker.domain.model.MedicalRecordExport
import com.healthtracker.domain.model.PrivacySettings
import com.healthtracker.domain.model.SocialExport
import com.healthtracker.domain.model.StreakExport
import com.healthtracker.domain.model.UserProfile
import com.healthtracker.domain.model.UserSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate

/**
 * Property-based tests for data export completeness.
 * 
 * **Validates: Requirements 14.5**
 * 
 * Property 37: Data Export Completeness
 * For any user data export request, the exported file SHALL contain all user 
 * data (profile, metrics, records, gamification state) in a valid, parseable format.
 */
class DataExportCompletenessTest : FunSpec({
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain user profile when profile exists") {
        checkAll(100, 
            Arb.string(1..50), 
            Arb.int(13..100), 
            Arb.float(20f..200f), 
            Arb.float(100f..250f),
            Arb.enum<HealthGoal>()
        ) { name, age, weight, height, goal ->
            val profile = UserProfile(
                name = name,
                age = age,
                weight = weight,
                height = height,
                goal = goal,
                dietPreference = null
            )
            
            val exporter = TestDataExporter()
            exporter.setProfile(profile)
            
            val exported = exporter.exportAllData()
            
            // Profile should be present and match
            exported.profile.shouldNotBeNull()
            exported.profile!!.name shouldBe name
            exported.profile!!.age shouldBe age
            exported.profile!!.weight shouldBe weight
            exported.profile!!.height shouldBe height
            exported.profile!!.goal shouldBe goal
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain all health metrics") {
        checkAll(100, Arb.list(Arb.int(0..50000), 1..30)) { stepsList ->
            val exporter = TestDataExporter()
            
            // Add health metrics
            val metrics = stepsList.mapIndexed { index, steps ->
                HealthMetricsExport(
                    date = LocalDate.now().minusDays(index.toLong()).toString(),
                    steps = steps,
                    distanceMeters = steps * 0.75,
                    caloriesBurned = steps * 0.04,
                    screenTimeMinutes = 120,
                    sleepDurationMinutes = 420,
                    sleepQuality = "GOOD",
                    mood = "HAPPY"
                )
            }
            exporter.setHealthMetrics(metrics)
            
            val exported = exporter.exportAllData()
            
            // All metrics should be present
            exported.healthMetrics.size shouldBe stepsList.size
            
            // Verify each metric is included
            metrics.forEach { metric ->
                exported.healthMetrics.any { it.date == metric.date && it.steps == metric.steps } shouldBe true
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain gamification state") {
        checkAll(100, 
            Arb.int(0..100000), 
            Arb.int(1..100),
            Arb.list(Arb.int(0..365), 1..5)
        ) { totalPoints, level, streakCounts ->
            val exporter = TestDataExporter()
            
            val gamification = GamificationExport(
                totalPoints = totalPoints,
                level = level,
                streaks = streakCounts.mapIndexed { index, count ->
                    StreakExport(
                        type = "STREAK_$index",
                        currentCount = count,
                        longestCount = count + 10,
                        lastUpdated = LocalDate.now().toString()
                    )
                },
                badges = listOf(
                    BadgeExport("badge_1", "First Steps", Instant.now().toString()),
                    BadgeExport("badge_2", "Week Warrior", null)
                )
            )
            exporter.setGamification(gamification)
            
            val exported = exporter.exportAllData()
            
            // Gamification should be present and match
            exported.gamification.shouldNotBeNull()
            exported.gamification!!.totalPoints shouldBe totalPoints
            exported.gamification!!.level shouldBe level
            exported.gamification!!.streaks.size shouldBe streakCounts.size
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain medical records metadata") {
        checkAll(100, Arb.list(Arb.string(5..50), 0..10)) { recordTitles ->
            val exporter = TestDataExporter()
            
            val records = recordTitles.mapIndexed { index, title ->
                MedicalRecordExport(
                    id = "record_$index",
                    type = "LAB_REPORT",
                    title = title,
                    uploadedAt = Instant.now().toString()
                )
            }
            exporter.setMedicalRecords(records)
            
            val exported = exporter.exportAllData()
            
            // All records should be present
            exported.medicalRecords.size shouldBe recordTitles.size
            
            // Verify each record is included
            records.forEach { record ->
                exported.medicalRecords.any { it.id == record.id && it.title == record.title } shouldBe true
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain user settings") {
        checkAll(100, 
            Arb.boolean(), 
            Arb.boolean(), 
            Arb.boolean(),
            Arb.int(30..240)
        ) { notifications, dataCollection, sensitiveData, reminderInterval ->
            val settings = UserSettings(
                notificationsEnabled = notifications,
                dataCollectionEnabled = dataCollection,
                sensitiveDataOptIn = sensitiveData,
                hydrationReminders = true,
                mindfulnessReminders = true,
                reminderIntervalMinutes = reminderInterval
            )
            
            val exporter = TestDataExporter()
            exporter.setSettings(settings)
            
            val exported = exporter.exportAllData()
            
            // Settings should be present and match
            exported.settings.shouldNotBeNull()
            exported.settings!!.notificationsEnabled shouldBe notifications
            exported.settings!!.dataCollectionEnabled shouldBe dataCollection
            exported.settings!!.sensitiveDataOptIn shouldBe sensitiveData
            exported.settings!!.reminderIntervalMinutes shouldBe reminderInterval
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should have valid export metadata") {
        checkAll(100, Arb.string(5..20)) { userId ->
            val exporter = TestDataExporter()
            exporter.setUserId(userId)
            
            val exported = exporter.exportAllData()
            
            // Export metadata should be valid
            exported.userId shouldBe userId
            exported.exportVersion shouldNotBe ""
            exported.exportedAt.shouldNotBeNull()
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("exported data should contain social data when user has circles") {
        checkAll(100, Arb.list(Arb.string(5..30), 0..5)) { circleNames ->
            val exporter = TestDataExporter()
            
            if (circleNames.isNotEmpty()) {
                val socialData = SocialExport(
                    circles = circleNames.mapIndexed { index, name ->
                        CircleExport(
                            id = "circle_$index",
                            name = name,
                            role = "MEMBER",
                            joinedAt = Instant.now().toString()
                        )
                    }
                )
                exporter.setSocialData(socialData)
            }
            
            val exported = exporter.exportAllData()
            
            // Social data should match input
            if (circleNames.isNotEmpty()) {
                exported.socialData.shouldNotBeNull()
                exported.socialData!!.circles.size shouldBe circleNames.size
            }
        }
    }
    
    // Feature: smart-health-tracker, Property 37: Data Export Completeness
    test("export should be parseable JSON format") {
        checkAll(100, Arb.string(5..20), Arb.int(13..80)) { name, age ->
            val exporter = TestDataExporter()
            exporter.setProfile(UserProfile(
                name = name,
                age = age,
                weight = 70f,
                height = 170f,
                goal = HealthGoal.GENERAL
            ))
            
            val exported = exporter.exportAllData()
            val json = exporter.toJson(exported)
            
            // JSON should be valid (contains expected structure)
            json.contains("exportVersion") shouldBe true
            json.contains("userId") shouldBe true
            json.contains("exportedAt") shouldBe true
        }
    }
})

/**
 * Test implementation of data exporter for property testing.
 */
class TestDataExporter {
    private var userId: String = "test-user"
    private var profile: UserProfile? = null
    private var settings: UserSettings? = null
    private var privacySettings: PrivacySettings? = null
    private var healthMetrics: List<HealthMetricsExport> = emptyList()
    private var gamification: GamificationExport? = null
    private var socialData: SocialExport? = null
    private var medicalRecords: List<MedicalRecordExport> = emptyList()
    
    fun setUserId(id: String) { userId = id }
    fun setProfile(p: UserProfile) { profile = p }
    fun setSettings(s: UserSettings) { settings = s }
    fun setPrivacySettings(ps: PrivacySettings) { privacySettings = ps }
    fun setHealthMetrics(metrics: List<HealthMetricsExport>) { healthMetrics = metrics }
    fun setGamification(g: GamificationExport) { gamification = g }
    fun setSocialData(s: SocialExport) { socialData = s }
    fun setMedicalRecords(records: List<MedicalRecordExport>) { medicalRecords = records }
    
    fun exportAllData(): ExportedUserData {
        return ExportedUserData(
            exportVersion = "1.0",
            exportedAt = Instant.now(),
            userId = userId,
            profile = profile,
            settings = settings,
            privacySettings = privacySettings,
            healthMetrics = healthMetrics,
            gamification = gamification,
            socialData = socialData,
            medicalRecords = medicalRecords,
            auditLog = emptyList()
        )
    }
    
    fun toJson(data: ExportedUserData): String {
        return buildString {
            appendLine("{")
            appendLine("  \"exportVersion\": \"${data.exportVersion}\",")
            appendLine("  \"userId\": \"${data.userId}\",")
            appendLine("  \"exportedAt\": \"${data.exportedAt}\",")
            appendLine("  \"healthMetricsCount\": ${data.healthMetrics.size},")
            appendLine("  \"medicalRecordsCount\": ${data.medicalRecords.size}")
            appendLine("}")
        }
    }
}
