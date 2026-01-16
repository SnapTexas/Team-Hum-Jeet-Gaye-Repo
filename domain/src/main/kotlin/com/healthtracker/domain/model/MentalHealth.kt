package com.healthtracker.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain models for Mental Health & Stress Management feature.
 */

// ============================================
// STRESS ASSESSMENT MODELS
// ============================================

/**
 * Stress assessment result combining multiple inputs.
 * 
 * @property level Calculated stress level (0-100)
 * @property category Stress category based on level
 * @property hrvContribution HRV contribution to stress score (0-100)
 * @property sleepContribution Sleep contribution to stress score (0-100)
 * @property moodContribution Mood contribution to stress score (0-100)
 * @property assessedAt When the assessment was made
 * @property recommendations Suggested actions based on stress level
 */
data class StressAssessment(
    val level: Int,
    val category: StressCategory,
    val hrvContribution: Int,
    val sleepContribution: Int,
    val moodContribution: Int,
    val assessedAt: Instant = Instant.now(),
    val recommendations: List<StressRecommendation> = emptyList()
) {
    init {
        require(level in 0..100) { "Stress level must be between 0 and 100" }
        require(hrvContribution in 0..100) { "HRV contribution must be between 0 and 100" }
        require(sleepContribution in 0..100) { "Sleep contribution must be between 0 and 100" }
        require(moodContribution in 0..100) { "Mood contribution must be between 0 and 100" }
    }
    
    /**
     * Returns true if stress is elevated (>= 60).
     */
    fun isElevated(): Boolean = level >= StressConstants.ELEVATED_THRESHOLD
    
    /**
     * Returns true if stress is high (>= 80).
     */
    fun isHigh(): Boolean = level >= StressConstants.HIGH_THRESHOLD
}

/**
 * Stress category based on level.
 */
enum class StressCategory {
    LOW,      // 0-39
    MODERATE, // 40-59
    ELEVATED, // 60-79
    HIGH      // 80-100
}

/**
 * Recommendation for stress management.
 */
data class StressRecommendation(
    val type: RecommendationType,
    val title: String,
    val description: String,
    val priority: Int = 0
)

/**
 * Type of stress recommendation.
 */
enum class RecommendationType {
    BREATHING_EXERCISE,
    MEDITATION,
    PHYSICAL_ACTIVITY,
    SLEEP_IMPROVEMENT,
    SOCIAL_CONNECTION,
    BREAK_REMINDER
}

/**
 * Input data for stress calculation.
 */
data class StressInput(
    val hrvAverage: Double?,      // Average HRV in ms (higher = less stress)
    val sleepDurationMinutes: Int?,
    val sleepQuality: SleepQuality?,
    val mood: Mood?,
    val date: LocalDate = LocalDate.now()
)

// ============================================
// BREATHING EXERCISE MODELS
// ============================================

/**
 * A breathing exercise pattern.
 */
data class BreathingExercise(
    val id: String,
    val name: String,
    val description: String,
    val pattern: BreathingPattern,
    val durationSeconds: Int,
    val difficulty: ExerciseDifficulty,
    val benefits: List<String>
)

/**
 * Breathing pattern timing.
 */
data class BreathingPattern(
    val name: String,
    val inhaleSeconds: Int,
    val holdAfterInhaleSeconds: Int,
    val exhaleSeconds: Int,
    val holdAfterExhaleSeconds: Int,
    val cycles: Int
) {
    /**
     * Total duration of one cycle in seconds.
     */
    val cycleDurationSeconds: Int
        get() = inhaleSeconds + holdAfterInhaleSeconds + exhaleSeconds + holdAfterExhaleSeconds
    
    /**
     * Total exercise duration in seconds.
     */
    val totalDurationSeconds: Int
        get() = cycleDurationSeconds * cycles
}

/**
 * Current phase of breathing exercise.
 */
enum class BreathingPhase {
    INHALE,
    HOLD_AFTER_INHALE,
    EXHALE,
    HOLD_AFTER_EXHALE,
    COMPLETE
}

/**
 * State of an ongoing breathing exercise.
 */
data class BreathingExerciseState(
    val exercise: BreathingExercise,
    val currentCycle: Int,
    val currentPhase: BreathingPhase,
    val phaseProgress: Float, // 0.0 to 1.0
    val totalProgress: Float, // 0.0 to 1.0
    val isActive: Boolean
)

// ============================================
// MEDITATION MODELS
// ============================================

/**
 * A guided meditation session.
 */
data class MeditationSession(
    val id: String,
    val title: String,
    val description: String,
    val category: MeditationCategory,
    val durationMinutes: Int,
    val audioUrl: String?,
    val backgroundImageUrl: String?,
    val difficulty: ExerciseDifficulty,
    val tags: List<String> = emptyList()
)

/**
 * Category of meditation.
 */
enum class MeditationCategory {
    STRESS_RELIEF,
    SLEEP,
    FOCUS,
    ANXIETY,
    GRATITUDE,
    BODY_SCAN,
    BREATHING,
    MINDFULNESS
}

/**
 * State of an ongoing meditation session.
 */
data class MeditationState(
    val session: MeditationSession,
    val elapsedSeconds: Int,
    val isPlaying: Boolean,
    val progress: Float // 0.0 to 1.0
)

// ============================================
// WELLNESS ACTIVITY MODELS
// ============================================

/**
 * A completed wellness activity.
 */
data class WellnessActivity(
    val id: String,
    val userId: String,
    val type: WellnessActivityType,
    val name: String,
    val durationSeconds: Int,
    val completedAt: Instant,
    val stressLevelBefore: Int?,
    val stressLevelAfter: Int?,
    val notes: String? = null
)

/**
 * Type of wellness activity.
 */
enum class WellnessActivityType {
    BREATHING_EXERCISE,
    MEDITATION,
    MINDFULNESS_BREAK,
    JOURNALING,
    GRATITUDE_PRACTICE
}

/**
 * Difficulty level for exercises.
 */
enum class ExerciseDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

// ============================================
// MINDFULNESS REMINDER MODELS
// ============================================

/**
 * A mindfulness reminder configuration.
 */
data class MindfulnessReminder(
    val id: String,
    val userId: String,
    val enabled: Boolean,
    val intervalMinutes: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val daysOfWeek: Set<java.time.DayOfWeek>,
    val reminderType: MentalReminderType,
    val message: String? = null
)

/**
 * Type of mindfulness reminder.
 */
enum class MentalReminderType {
    BREATHING_BREAK,
    STRETCH_BREAK,
    HYDRATION,
    POSTURE_CHECK,
    GRATITUDE_MOMENT,
    CUSTOM
}

/**
 * A scheduled reminder instance.
 */
data class MentalScheduledReminder(
    val id: String,
    val reminderId: String,
    val scheduledTime: Instant,
    val type: MentalReminderType,
    val message: String,
    val delivered: Boolean = false,
    val deliveredAt: Instant? = null
)

// ============================================
// STRESS TRENDS
// ============================================

/**
 * Daily stress summary.
 */
data class DailyStressSummary(
    val date: LocalDate,
    val averageStressLevel: Int,
    val peakStressLevel: Int,
    val lowestStressLevel: Int,
    val assessmentCount: Int,
    val wellnessActivitiesCompleted: Int,
    val totalWellnessMinutes: Int
)

/**
 * Stress trend over time.
 */
data class StressTrend(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailySummaries: List<DailyStressSummary>,
    val averageStressLevel: Int,
    val trend: StressTrendDirection,
    val insights: List<String>
)

/**
 * Direction of a trend.
 */
enum class StressTrendDirection {
    IMPROVING,
    STABLE,
    WORSENING
}

// ============================================
// CONSTANTS
// ============================================

/**
 * Constants for stress management.
 */
object StressConstants {
    const val LOW_THRESHOLD = 40
    const val ELEVATED_THRESHOLD = 60
    const val HIGH_THRESHOLD = 80
    
    // Weights for stress calculation
    const val HRV_WEIGHT = 0.4f
    const val SLEEP_WEIGHT = 0.35f
    const val MOOD_WEIGHT = 0.25f
    
    // Default reminder interval
    const val DEFAULT_REMINDER_INTERVAL_MINUTES = 60
    
    // Tolerance for reminder timing (±1 minute)
    const val REMINDER_TOLERANCE_SECONDS = 60L
}

// ============================================
// PREDEFINED BREATHING EXERCISES
// ============================================

/**
 * Predefined breathing exercises.
 */
object BreathingExercises {
    
    val BOX_BREATHING = BreathingExercise(
        id = "box_breathing",
        name = "Box Breathing",
        description = "Equal duration inhale, hold, exhale, hold. Used by Navy SEALs for stress relief.",
        pattern = BreathingPattern(
            name = "4-4-4-4",
            inhaleSeconds = 4,
            holdAfterInhaleSeconds = 4,
            exhaleSeconds = 4,
            holdAfterExhaleSeconds = 4,
            cycles = 4
        ),
        durationSeconds = 64,
        difficulty = ExerciseDifficulty.BEGINNER,
        benefits = listOf("Reduces stress", "Improves focus", "Calms nervous system")
    )
    
    val RELAXING_BREATH = BreathingExercise(
        id = "relaxing_breath_478",
        name = "4-7-8 Relaxing Breath",
        description = "Dr. Andrew Weil's relaxing breath technique for anxiety and sleep.",
        pattern = BreathingPattern(
            name = "4-7-8",
            inhaleSeconds = 4,
            holdAfterInhaleSeconds = 7,
            exhaleSeconds = 8,
            holdAfterExhaleSeconds = 0,
            cycles = 4
        ),
        durationSeconds = 76,
        difficulty = ExerciseDifficulty.INTERMEDIATE,
        benefits = listOf("Promotes sleep", "Reduces anxiety", "Lowers heart rate")
    )
    
    val ENERGIZING_BREATH = BreathingExercise(
        id = "energizing_breath",
        name = "Energizing Breath",
        description = "Quick, rhythmic breathing to increase energy and alertness.",
        pattern = BreathingPattern(
            name = "2-0-2-0",
            inhaleSeconds = 2,
            holdAfterInhaleSeconds = 0,
            exhaleSeconds = 2,
            holdAfterExhaleSeconds = 0,
            cycles = 10
        ),
        durationSeconds = 40,
        difficulty = ExerciseDifficulty.BEGINNER,
        benefits = listOf("Increases energy", "Improves alertness", "Boosts mood")
    )
    
    val DEEP_CALM = BreathingExercise(
        id = "deep_calm",
        name = "Deep Calm",
        description = "Extended exhale breathing for deep relaxation.",
        pattern = BreathingPattern(
            name = "4-2-6-2",
            inhaleSeconds = 4,
            holdAfterInhaleSeconds = 2,
            exhaleSeconds = 6,
            holdAfterExhaleSeconds = 2,
            cycles = 6
        ),
        durationSeconds = 84,
        difficulty = ExerciseDifficulty.INTERMEDIATE,
        benefits = listOf("Deep relaxation", "Activates parasympathetic system", "Reduces tension")
    )
    
    val ALL = listOf(BOX_BREATHING, RELAXING_BREATH, ENERGIZING_BREATH, DEEP_CALM)
}

/**
 * Predefined meditation sessions.
 */
object MeditationSessions {
    
    val QUICK_STRESS_RELIEF = MeditationSession(
        id = "quick_stress_relief",
        title = "Quick Stress Relief",
        description = "A 5-minute guided meditation to quickly reduce stress and tension.",
        category = MeditationCategory.STRESS_RELIEF,
        durationMinutes = 5,
        audioUrl = null, // Would be actual URL in production
        backgroundImageUrl = null,
        difficulty = ExerciseDifficulty.BEGINNER,
        tags = listOf("quick", "stress", "beginner")
    )
    
    val SLEEP_PREPARATION = MeditationSession(
        id = "sleep_preparation",
        title = "Sleep Preparation",
        description = "Gentle meditation to prepare your mind and body for restful sleep.",
        category = MeditationCategory.SLEEP,
        durationMinutes = 10,
        audioUrl = null,
        backgroundImageUrl = null,
        difficulty = ExerciseDifficulty.BEGINNER,
        tags = listOf("sleep", "relaxation", "evening")
    )
    
    val MORNING_MINDFULNESS = MeditationSession(
        id = "morning_mindfulness",
        title = "Morning Mindfulness",
        description = "Start your day with clarity and intention.",
        category = MeditationCategory.MINDFULNESS,
        durationMinutes = 10,
        audioUrl = null,
        backgroundImageUrl = null,
        difficulty = ExerciseDifficulty.BEGINNER,
        tags = listOf("morning", "mindfulness", "focus")
    )
    
    val ANXIETY_RELIEF = MeditationSession(
        id = "anxiety_relief",
        title = "Anxiety Relief",
        description = "Calm anxious thoughts and find inner peace.",
        category = MeditationCategory.ANXIETY,
        durationMinutes = 15,
        audioUrl = null,
        backgroundImageUrl = null,
        difficulty = ExerciseDifficulty.INTERMEDIATE,
        tags = listOf("anxiety", "calm", "peace")
    )
    
    val BODY_SCAN = MeditationSession(
        id = "body_scan",
        title = "Body Scan Relaxation",
        description = "Progressive relaxation through body awareness.",
        category = MeditationCategory.BODY_SCAN,
        durationMinutes = 20,
        audioUrl = null,
        backgroundImageUrl = null,
        difficulty = ExerciseDifficulty.INTERMEDIATE,
        tags = listOf("body", "relaxation", "awareness")
    )
    
    val ALL = listOf(QUICK_STRESS_RELIEF, SLEEP_PREPARATION, MORNING_MINDFULNESS, ANXIETY_RELIEF, BODY_SCAN)
}
