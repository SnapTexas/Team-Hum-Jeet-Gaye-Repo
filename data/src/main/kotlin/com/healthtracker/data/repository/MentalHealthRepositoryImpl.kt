package com.healthtracker.data.repository

import com.healthtracker.domain.model.DailyStressSummary
import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.MentalReminderType
import com.healthtracker.domain.model.MentalScheduledReminder
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressCategory
import com.healthtracker.domain.model.StressTrend
import com.healthtracker.domain.model.StressTrendDirection
import com.healthtracker.domain.model.WellnessActivity
import com.healthtracker.domain.repository.MentalHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MentalHealthRepository.
 * 
 * Manages stress assessments, wellness activities, and mindfulness reminders.
 * Uses in-memory storage (would use Room database in production).
 */
@Singleton
class MentalHealthRepositoryImpl @Inject constructor() : MentalHealthRepository {
    
    // In-memory storage (would be Room database in production)
    private val stressAssessments = MutableStateFlow<List<StressAssessmentRecord>>(emptyList())
    private val wellnessActivities = MutableStateFlow<List<WellnessActivity>>(emptyList())
    private val reminders = MutableStateFlow<List<MindfulnessReminder>>(emptyList())
    private val scheduledReminders = MutableStateFlow<List<MentalScheduledReminder>>(emptyList())
    
    // Internal record with userId
    private data class StressAssessmentRecord(
        val userId: String,
        val assessment: StressAssessment
    )
    
    // ============================================
    // STRESS ASSESSMENTS
    // ============================================
    
    override suspend fun saveStressAssessment(assessment: StressAssessment) {
        val record = StressAssessmentRecord(
            userId = "current_user", // Would come from auth
            assessment = assessment
        )
        stressAssessments.value = stressAssessments.value + record
    }
    
    override suspend fun getStressAssessments(userId: String, date: LocalDate): List<StressAssessment> {
        return stressAssessments.value
            .filter { it.userId == userId }
            .filter { 
                val assessmentDate = it.assessment.assessedAt
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                assessmentDate == date
            }
            .map { it.assessment }
            .sortedByDescending { it.assessedAt }
    }
    
    override suspend fun getLatestStressAssessment(userId: String): StressAssessment? {
        return stressAssessments.value
            .filter { it.userId == userId }
            .maxByOrNull { it.assessment.assessedAt }
            ?.assessment
    }

    override suspend fun getDailyStressSummary(userId: String, date: LocalDate): DailyStressSummary? {
        val assessments = getStressAssessments(userId, date)
        if (assessments.isEmpty()) return null
        
        val activities = getWellnessActivities(userId, date)
        val totalWellnessSeconds = activities.sumOf { it.durationSeconds }
        
        return DailyStressSummary(
            date = date,
            averageStressLevel = assessments.map { it.level }.average().toInt(),
            peakStressLevel = assessments.maxOf { it.level },
            lowestStressLevel = assessments.minOf { it.level },
            assessmentCount = assessments.size,
            wellnessActivitiesCompleted = activities.size,
            totalWellnessMinutes = totalWellnessSeconds / 60
        )
    }
    
    override suspend fun getStressTrend(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): StressTrend {
        val dailySummaries = mutableListOf<DailyStressSummary>()
        var currentDate = startDate
        
        while (!currentDate.isAfter(endDate)) {
            getDailyStressSummary(userId, currentDate)?.let {
                dailySummaries.add(it)
            }
            currentDate = currentDate.plusDays(1)
        }
        
        val averageStress = if (dailySummaries.isNotEmpty()) {
            dailySummaries.map { it.averageStressLevel }.average().toInt()
        } else 0
        
        // Calculate trend direction
        val trend = if (dailySummaries.size >= 2) {
            val firstHalf = dailySummaries.take(dailySummaries.size / 2)
            val secondHalf = dailySummaries.drop(dailySummaries.size / 2)
            
            val firstAvg = firstHalf.map { it.averageStressLevel }.average()
            val secondAvg = secondHalf.map { it.averageStressLevel }.average()
            
            when {
                secondAvg < firstAvg - 5 -> StressTrendDirection.IMPROVING
                secondAvg > firstAvg + 5 -> StressTrendDirection.WORSENING
                else -> StressTrendDirection.STABLE
            }
        } else StressTrendDirection.STABLE
        
        // Generate insights
        val insights = generateStressInsights(dailySummaries, averageStress, trend)
        
        return StressTrend(
            startDate = startDate,
            endDate = endDate,
            dailySummaries = dailySummaries,
            averageStressLevel = averageStress,
            trend = trend,
            insights = insights
        )
    }
    
    private fun generateStressInsights(
        summaries: List<DailyStressSummary>,
        averageStress: Int,
        trend: StressTrendDirection
    ): List<String> {
        val insights = mutableListOf<String>()
        
        when (trend) {
            StressTrendDirection.IMPROVING -> insights.add("Your stress levels are improving! Keep up the good work.")
            StressTrendDirection.WORSENING -> insights.add("Your stress has been increasing. Consider more wellness activities.")
            StressTrendDirection.STABLE -> insights.add("Your stress levels have been stable.")
        }
        
        if (averageStress >= 60) {
            insights.add("Your average stress is elevated. Try breathing exercises daily.")
        }
        
        val totalWellnessMinutes = summaries.sumOf { it.totalWellnessMinutes }
        if (totalWellnessMinutes > 0) {
            insights.add("You've completed $totalWellnessMinutes minutes of wellness activities.")
        }
        
        return insights
    }
    
    // ============================================
    // WELLNESS ACTIVITIES
    // ============================================
    
    override suspend fun logWellnessActivity(activity: WellnessActivity): WellnessActivity {
        val activityWithId = if (activity.id.isEmpty()) {
            activity.copy(id = UUID.randomUUID().toString())
        } else activity
        
        wellnessActivities.value = wellnessActivities.value + activityWithId
        return activityWithId
    }
    
    override suspend fun getWellnessActivities(userId: String, date: LocalDate): List<WellnessActivity> {
        return wellnessActivities.value
            .filter { it.userId == userId }
            .filter {
                val activityDate = it.completedAt
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                activityDate == date
            }
            .sortedByDescending { it.completedAt }
    }
    
    override suspend fun getWellnessActivitiesRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<WellnessActivity> {
        return wellnessActivities.value
            .filter { it.userId == userId }
            .filter {
                val activityDate = it.completedAt
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                !activityDate.isBefore(startDate) && !activityDate.isAfter(endDate)
            }
            .sortedByDescending { it.completedAt }
    }
    
    override suspend fun getTotalWellnessMinutes(userId: String, date: LocalDate): Int {
        return getWellnessActivities(userId, date)
            .sumOf { it.durationSeconds } / 60
    }

    // ============================================
    // MINDFULNESS REMINDERS
    // ============================================
    
    override suspend fun saveReminder(reminder: MindfulnessReminder): MindfulnessReminder {
        val reminderWithId = if (reminder.id.isEmpty()) {
            reminder.copy(id = UUID.randomUUID().toString())
        } else reminder
        
        // Update or add
        val existing = reminders.value.find { it.id == reminderWithId.id }
        reminders.value = if (existing != null) {
            reminders.value.map { if (it.id == reminderWithId.id) reminderWithId else it }
        } else {
            reminders.value + reminderWithId
        }
        
        return reminderWithId
    }
    
    override fun getReminders(userId: String): Flow<List<MindfulnessReminder>> {
        return reminders.map { list ->
            list.filter { it.userId == userId }
        }
    }
    
    override suspend fun getReminder(reminderId: String): MindfulnessReminder? {
        return reminders.value.find { it.id == reminderId }
    }
    
    override suspend fun deleteReminder(reminderId: String) {
        reminders.value = reminders.value.filter { it.id != reminderId }
        // Also delete scheduled instances
        scheduledReminders.value = scheduledReminders.value.filter { it.reminderId != reminderId }
    }
    
    override suspend fun updateReminderEnabled(reminderId: String, enabled: Boolean) {
        reminders.value = reminders.value.map {
            if (it.id == reminderId) it.copy(enabled = enabled) else it
        }
    }
    
    override suspend fun scheduleReminder(scheduledReminder: MentalScheduledReminder) {
        val reminderWithId = if (scheduledReminder.id.isEmpty()) {
            scheduledReminder.copy(id = UUID.randomUUID().toString())
        } else scheduledReminder
        
        scheduledReminders.value = scheduledReminders.value + reminderWithId
    }
    
    override suspend fun markReminderDelivered(scheduledReminderId: String) {
        scheduledReminders.value = scheduledReminders.value.map {
            if (it.id == scheduledReminderId) {
                it.copy(delivered = true, deliveredAt = Instant.now())
            } else it
        }
    }
    
    override suspend fun getPendingReminders(userId: String): List<MentalScheduledReminder> {
        val userReminderIds = reminders.value
            .filter { it.userId == userId }
            .map { it.id }
            .toSet()
        
        return scheduledReminders.value
            .filter { it.reminderId in userReminderIds }
            .filter { !it.delivered }
            .sortedBy { it.scheduledTime }
    }
}
