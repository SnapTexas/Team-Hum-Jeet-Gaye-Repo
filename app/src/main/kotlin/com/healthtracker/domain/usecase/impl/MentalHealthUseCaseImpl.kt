package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.BreathingExercise
import com.healthtracker.domain.model.BreathingExerciseState
import com.healthtracker.domain.model.BreathingExercises
import com.healthtracker.domain.model.BreathingPhase
import com.healthtracker.domain.model.DailyStressSummary
import com.healthtracker.domain.model.MeditationCategory
import com.healthtracker.domain.model.MeditationSession
import com.healthtracker.domain.model.MeditationSessions
import com.healthtracker.domain.model.MeditationState
import com.healthtracker.domain.model.MentalReminderType
import com.healthtracker.domain.model.MentalScheduledReminder
import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.Mood
import com.healthtracker.domain.model.RecommendationType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SleepQuality
import com.healthtracker.domain.model.StressAssessment
import com.healthtracker.domain.model.StressCategory
import com.healthtracker.domain.model.StressConstants
import com.healthtracker.domain.model.StressInput
import com.healthtracker.domain.model.StressRecommendation
import com.healthtracker.domain.model.StressTrend
import com.healthtracker.domain.model.WellnessActivity
import com.healthtracker.domain.model.WellnessActivityType
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.repository.MentalHealthRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.MentalHealthUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MentalHealthUseCaseImpl @Inject constructor(
    private val mentalHealthRepository: MentalHealthRepository,
    private val healthDataRepository: HealthDataRepository,
    private val userRepository: UserRepository
) : MentalHealthUseCase {
    
    companion object {
        private const val TAG = "MentalHealthUseCase"
        private const val HRV_EXCELLENT = 60.0
        private const val HRV_GOOD = 45.0
        private const val HRV_FAIR = 30.0
        private const val SLEEP_EXCELLENT = 480
        private const val SLEEP_GOOD = 420
        private const val SLEEP_FAIR = 360
    }

    override suspend fun calculateStress(input: StressInput): StressAssessment {
        val hrvContribution = calculateHrvContribution(input.hrvAverage)
        val sleepContribution = calculateSleepContribution(input.sleepDurationMinutes, input.sleepQuality)
        val moodContribution = calculateMoodContribution(input.mood)
        
        val stressLevel = (
            hrvContribution * StressConstants.HRV_WEIGHT +
            sleepContribution * StressConstants.SLEEP_WEIGHT +
            moodContribution * StressConstants.MOOD_WEIGHT
        ).toInt().coerceIn(0, 100)
        
        val category = when {
            stressLevel >= StressConstants.HIGH_THRESHOLD -> StressCategory.HIGH
            stressLevel >= StressConstants.ELEVATED_THRESHOLD -> StressCategory.ELEVATED
            stressLevel >= StressConstants.LOW_THRESHOLD -> StressCategory.MODERATE
            else -> StressCategory.LOW
        }
        
        val recommendations = generateRecommendations(stressLevel, category)
        
        val assessment = StressAssessment(
            level = stressLevel,
            category = category,
            hrvContribution = hrvContribution,
            sleepContribution = sleepContribution,
            moodContribution = moodContribution,
            assessedAt = Instant.now(),
            recommendations = recommendations
        )
        
        mentalHealthRepository.saveStressAssessment(assessment)
        return assessment
    }
    
    private fun calculateHrvContribution(hrvAverage: Double?): Int {
        if (hrvAverage == null) return 50
        return when {
            hrvAverage >= HRV_EXCELLENT -> 10
            hrvAverage >= HRV_GOOD -> 30
            hrvAverage >= HRV_FAIR -> 50
            hrvAverage >= 20 -> 70
            else -> 90
        }
    }
    
    private fun calculateSleepContribution(durationMinutes: Int?, quality: SleepQuality?): Int {
        val durationScore = when {
            durationMinutes == null -> 50
            durationMinutes >= SLEEP_EXCELLENT -> 10
            durationMinutes >= SLEEP_GOOD -> 30
            durationMinutes >= SLEEP_FAIR -> 50
            durationMinutes >= 300 -> 70
            else -> 90
        }
        
        val qualityScore = when (quality) {
            SleepQuality.EXCELLENT -> 10
            SleepQuality.GOOD -> 30
            SleepQuality.FAIR -> 50
            SleepQuality.POOR -> 80
            null -> 50
        }
        
        return (durationScore + qualityScore) / 2
    }
    
    private fun calculateMoodContribution(mood: Mood?): Int {
        return when (mood) {
            Mood.VERY_HAPPY -> 10
            Mood.HAPPY -> 25
            Mood.NEUTRAL -> 50
            Mood.SAD -> 75
            Mood.VERY_SAD -> 90
            null -> 50
        }
    }
    
    private fun generateRecommendations(stressLevel: Int, category: StressCategory): List<StressRecommendation> {
        val recommendations = mutableListOf<StressRecommendation>()
        
        if (stressLevel >= StressConstants.ELEVATED_THRESHOLD) {
            recommendations.add(StressRecommendation(
                type = RecommendationType.BREATHING_EXERCISE,
                title = "Try a breathing exercise",
                description = "Box breathing can help reduce stress quickly",
                priority = 1
            ))
        }
        
        if (stressLevel >= StressConstants.HIGH_THRESHOLD) {
            recommendations.add(StressRecommendation(
                type = RecommendationType.MEDITATION,
                title = "Take a meditation break",
                description = "A 5-minute meditation can help calm your mind",
                priority = 2
            ))
        }
        
        if (category == StressCategory.MODERATE || category == StressCategory.ELEVATED) {
            recommendations.add(StressRecommendation(
                type = RecommendationType.PHYSICAL_ACTIVITY,
                title = "Get moving",
                description = "A short walk can help reduce stress hormones",
                priority = 3
            ))
        }
        
        return recommendations.sortedBy { it.priority }
    }
    
    override suspend fun getCurrentStressAssessment(): StressAssessment {
        val userId = getCurrentUserId()
        
        val todayAssessments = mentalHealthRepository.getStressAssessments(userId, LocalDate.now())
        if (todayAssessments.isNotEmpty()) {
            return todayAssessments.first()
        }
        
        val healthMetrics = try {
            healthDataRepository.getHealthMetrics(LocalDate.now()).first()
        } catch (e: Exception) {
            null
        }
        
        val input = StressInput(
            hrvAverage = healthMetrics?.hrvSamples?.map { it.sdnn }?.average(),
            sleepDurationMinutes = healthMetrics?.sleepDurationMinutes,
            sleepQuality = healthMetrics?.sleepQuality,
            mood = healthMetrics?.mood
        )
        
        return calculateStress(input)
    }
    
    override suspend fun getTodayStressAssessments(): List<StressAssessment> {
        return mentalHealthRepository.getStressAssessments(getCurrentUserId(), LocalDate.now())
    }
    
    override suspend fun getDailyStressSummary(date: LocalDate): DailyStressSummary? {
        return mentalHealthRepository.getDailyStressSummary(getCurrentUserId(), date)
    }
    
    override suspend fun getStressTrend(days: Int): StressTrend {
        val userId = getCurrentUserId()
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong() - 1)
        return mentalHealthRepository.getStressTrend(userId, startDate, endDate)
    }

    override fun getBreathingExercises(): List<BreathingExercise> = BreathingExercises.ALL
    
    override fun getBreathingExercise(exerciseId: String): BreathingExercise? {
        return BreathingExercises.ALL.find { it.id == exerciseId }
    }
    
    override fun getRecommendedBreathingExercise(stressLevel: Int): BreathingExercise {
        return when {
            stressLevel >= StressConstants.HIGH_THRESHOLD -> BreathingExercises.RELAXING_BREATH
            stressLevel >= StressConstants.ELEVATED_THRESHOLD -> BreathingExercises.DEEP_CALM
            stressLevel >= StressConstants.LOW_THRESHOLD -> BreathingExercises.BOX_BREATHING
            else -> BreathingExercises.ENERGIZING_BREATH
        }
    }
    
    override fun startBreathingExercise(exercise: BreathingExercise): Flow<BreathingExerciseState> = flow {
        val pattern = exercise.pattern
        val totalCycles = pattern.cycles
        val totalDuration = pattern.totalDurationSeconds
        var elapsedSeconds = 0
        
        for (cycle in 1..totalCycles) {
            for (second in 1..pattern.inhaleSeconds) {
                emit(BreathingExerciseState(exercise, cycle, BreathingPhase.INHALE, 
                    second.toFloat() / pattern.inhaleSeconds, elapsedSeconds.toFloat() / totalDuration, true))
                delay(1000)
                elapsedSeconds++
            }
            
            if (pattern.holdAfterInhaleSeconds > 0) {
                for (second in 1..pattern.holdAfterInhaleSeconds) {
                    emit(BreathingExerciseState(exercise, cycle, BreathingPhase.HOLD_AFTER_INHALE,
                        second.toFloat() / pattern.holdAfterInhaleSeconds, elapsedSeconds.toFloat() / totalDuration, true))
                    delay(1000)
                    elapsedSeconds++
                }
            }
            
            for (second in 1..pattern.exhaleSeconds) {
                emit(BreathingExerciseState(exercise, cycle, BreathingPhase.EXHALE,
                    second.toFloat() / pattern.exhaleSeconds, elapsedSeconds.toFloat() / totalDuration, true))
                delay(1000)
                elapsedSeconds++
            }
            
            if (pattern.holdAfterExhaleSeconds > 0) {
                for (second in 1..pattern.holdAfterExhaleSeconds) {
                    emit(BreathingExerciseState(exercise, cycle, BreathingPhase.HOLD_AFTER_EXHALE,
                        second.toFloat() / pattern.holdAfterExhaleSeconds, elapsedSeconds.toFloat() / totalDuration, true))
                    delay(1000)
                    elapsedSeconds++
                }
            }
        }
        
        emit(BreathingExerciseState(exercise, totalCycles, BreathingPhase.COMPLETE, 1f, 1f, false))
    }
    
    override suspend fun completeBreathingExercise(
        exercise: BreathingExercise,
        durationSeconds: Int,
        stressLevelBefore: Int?,
        stressLevelAfter: Int?
    ): WellnessActivity {
        val activity = WellnessActivity(
            id = UUID.randomUUID().toString(),
            userId = getCurrentUserId(),
            type = WellnessActivityType.BREATHING_EXERCISE,
            name = exercise.name,
            durationSeconds = durationSeconds,
            completedAt = Instant.now(),
            stressLevelBefore = stressLevelBefore,
            stressLevelAfter = stressLevelAfter
        )
        return mentalHealthRepository.logWellnessActivity(activity)
    }

    override fun getMeditationSessions(): List<MeditationSession> = MeditationSessions.ALL
    
    override fun getMeditationSessionsByCategory(category: MeditationCategory): List<MeditationSession> {
        return MeditationSessions.ALL.filter { it.category == category }
    }
    
    override fun getMeditationSession(sessionId: String): MeditationSession? {
        return MeditationSessions.ALL.find { it.id == sessionId }
    }
    
    override fun startMeditationSession(session: MeditationSession): Flow<MeditationState> = flow {
        val totalSeconds = session.durationMinutes * 60
        var elapsedSeconds = 0
        
        while (elapsedSeconds < totalSeconds) {
            emit(MeditationState(session, elapsedSeconds, true, elapsedSeconds.toFloat() / totalSeconds))
            delay(1000)
            elapsedSeconds++
        }
        
        emit(MeditationState(session, totalSeconds, false, 1f))
    }
    
    override suspend fun completeMeditationSession(
        session: MeditationSession,
        durationSeconds: Int,
        stressLevelBefore: Int?,
        stressLevelAfter: Int?
    ): WellnessActivity {
        val activity = WellnessActivity(
            id = UUID.randomUUID().toString(),
            userId = getCurrentUserId(),
            type = WellnessActivityType.MEDITATION,
            name = session.title,
            durationSeconds = durationSeconds,
            completedAt = Instant.now(),
            stressLevelBefore = stressLevelBefore,
            stressLevelAfter = stressLevelAfter
        )
        return mentalHealthRepository.logWellnessActivity(activity)
    }

    override suspend fun getTodayWellnessActivities(): List<WellnessActivity> {
        return mentalHealthRepository.getWellnessActivities(getCurrentUserId(), LocalDate.now())
    }
    
    override suspend fun getWellnessActivities(startDate: LocalDate, endDate: LocalDate): List<WellnessActivity> {
        return mentalHealthRepository.getWellnessActivitiesRange(getCurrentUserId(), startDate, endDate)
    }
    
    override suspend fun getTodayWellnessMinutes(): Int {
        return mentalHealthRepository.getTotalWellnessMinutes(getCurrentUserId(), LocalDate.now())
    }
    
    override suspend fun logWellnessActivity(
        type: WellnessActivityType,
        name: String,
        durationSeconds: Int,
        notes: String?
    ): WellnessActivity {
        val activity = WellnessActivity(
            id = UUID.randomUUID().toString(),
            userId = getCurrentUserId(),
            type = type,
            name = name,
            durationSeconds = durationSeconds,
            completedAt = Instant.now(),
            stressLevelBefore = null,
            stressLevelAfter = null,
            notes = notes
        )
        return mentalHealthRepository.logWellnessActivity(activity)
    }

    override fun getReminders(): Flow<List<MindfulnessReminder>> {
        return mentalHealthRepository.getReminders("default")
    }
    
    override suspend fun createReminder(reminder: MindfulnessReminder): MindfulnessReminder {
        val reminderWithUser = reminder.copy(userId = getCurrentUserId())
        return mentalHealthRepository.saveReminder(reminderWithUser)
    }
    
    override suspend fun updateReminder(reminder: MindfulnessReminder): MindfulnessReminder {
        return mentalHealthRepository.saveReminder(reminder)
    }
    
    override suspend fun deleteReminder(reminderId: String) {
        mentalHealthRepository.deleteReminder(reminderId)
    }
    
    override suspend fun toggleReminder(reminderId: String, enabled: Boolean) {
        mentalHealthRepository.updateReminderEnabled(reminderId, enabled)
    }
    
    override suspend fun scheduleDailyReminders() {
        val userId = getCurrentUserId()
        val reminders = mentalHealthRepository.getReminders(userId).first()
        val now = Instant.now()
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek
        
        for (reminder in reminders.filter { it.enabled }) {
            if (currentDayOfWeek !in reminder.daysOfWeek) continue
            
            var currentTime = reminder.startTime
            while (currentTime.isBefore(reminder.endTime)) {
                val scheduledInstant = today.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant()
                
                if (scheduledInstant.isAfter(now)) {
                    val scheduled = MentalScheduledReminder(
                        id = UUID.randomUUID().toString(),
                        reminderId = reminder.id,
                        scheduledTime = scheduledInstant,
                        type = reminder.reminderType,
                        message = reminder.message ?: getDefaultReminderMessage(reminder.reminderType)
                    )
                    mentalHealthRepository.scheduleReminder(scheduled)
                }
                
                currentTime = currentTime.plusMinutes(reminder.intervalMinutes.toLong())
            }
        }
    }
    
    private fun getDefaultReminderMessage(type: MentalReminderType): String {
        return when (type) {
            MentalReminderType.BREATHING_BREAK -> "Time for a breathing break!"
            MentalReminderType.STRETCH_BREAK -> "Time to stretch!"
            MentalReminderType.HYDRATION -> "Remember to drink water!"
            MentalReminderType.POSTURE_CHECK -> "Check your posture!"
            MentalReminderType.GRATITUDE_MOMENT -> "Take a moment for gratitude"
            MentalReminderType.CUSTOM -> "Mindfulness reminder"
        }
    }

    private suspend fun getCurrentUserId(): String {
        return when (val result = userRepository.getCurrentUser()) {
            is Result.Success -> result.data.id
            else -> "default"
        }
    }
}
