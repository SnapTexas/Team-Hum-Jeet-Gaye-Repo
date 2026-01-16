package com.healthtracker.domain.usecase.impl

import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.CircleChallenge
import com.healthtracker.domain.model.CircleLeaderboardEntry
import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleProgress
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.JoinCircleResult
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.repository.SocialRepository
import com.healthtracker.domain.usecase.SocialUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SocialUseCase.
 * Provides business logic for Health Circles with privacy enforcement.
 */
@Singleton
class SocialUseCaseImpl @Inject constructor(
    private val socialRepository: SocialRepository,
    private val healthDataRepository: HealthDataRepository
) : SocialUseCase {

    // ==================== Circle Management ====================

    override fun getMyCircles(): Flow<List<HealthCircle>> = socialRepository.getCircles()

    override fun getCircle(circleId: String): Flow<HealthCircle?> = socialRepository.getCircle(circleId)

    override suspend fun createCircle(
        name: String,
        type: CircleType,
        description: String?
    ): Result<HealthCircle> {
        // Validate input
        if (name.isBlank()) {
            return Result.Error(AppException.ValidationException("name", "Circle name cannot be empty"))
        }
        if (name.length > 50) {
            return Result.Error(AppException.ValidationException("name", "Circle name too long (max 50 chars)"))
        }
        
        return socialRepository.createCircle(name.trim(), type, description?.trim())
    }

    override suspend fun joinCircle(code: String): JoinCircleResult {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.length != 8) {
            return JoinCircleResult.InvalidCode("Join code must be 8 characters")
        }
        return socialRepository.joinCircle(normalizedCode)
    }

    override suspend fun leaveCircle(circleId: String): Result<Unit> = 
        socialRepository.leaveCircle(circleId)

    override suspend fun deleteCircle(circleId: String): Result<Unit> = 
        socialRepository.deleteCircle(circleId)

    // ==================== Member Management ====================

    override fun getCircleMembers(circleId: String): Flow<List<CircleMember>> = 
        socialRepository.getCircleMembers(circleId)

    override suspend fun removeMember(circleId: String, userId: String): Result<Unit> = 
        socialRepository.removeMember(circleId, userId)

    override suspend fun updateMemberRole(
        circleId: String,
        userId: String,
        newRole: MemberRole
    ): Result<Unit> = socialRepository.updateMemberRole(circleId, userId, newRole)

    // ==================== Leaderboards & Progress ====================

    override fun getCircleLeaderboard(
        circleId: String,
        metricType: MetricType,
        limit: Int
    ): Flow<List<CircleLeaderboardEntry>> = 
        socialRepository.getCircleLeaderboard(circleId, metricType, limit)

    override suspend fun getCircleProgress(
        circleId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleProgress> = socialRepository.getCircleProgress(circleId, startDate, endDate)

    override suspend fun getUserRank(circleId: String, metricType: MetricType): Result<Int> = 
        socialRepository.getUserRank(circleId, metricType)

    // ==================== Privacy Controls ====================

    override fun getPrivacySettings(): Flow<CirclePrivacySettings> = 
        socialRepository.getPrivacySettings()

    override suspend fun updateSharedMetrics(
        circleId: String,
        sharedMetrics: Set<MetricType>
    ): Result<Unit> {
        // Validate that only shareable metrics are included
        val invalidMetrics = sharedMetrics - CirclePrivacySettings.SHAREABLE_METRICS
        if (invalidMetrics.isNotEmpty()) {
            return Result.Error(
                AppException.ValidationException(
                    "metrics", 
                    "Cannot share sensitive metrics: ${invalidMetrics.joinToString()}"
                )
            )
        }
        return socialRepository.updateSharedMetrics(circleId, sharedMetrics)
    }

    override suspend fun updateDefaultSharedMetrics(sharedMetrics: Set<MetricType>): Result<Unit> {
        val invalidMetrics = sharedMetrics - CirclePrivacySettings.SHAREABLE_METRICS
        if (invalidMetrics.isNotEmpty()) {
            return Result.Error(
                AppException.ValidationException(
                    "metrics", 
                    "Cannot share sensitive metrics: ${invalidMetrics.joinToString()}"
                )
            )
        }
        return socialRepository.updateDefaultSharedMetrics(sharedMetrics)
    }

    override suspend fun isMetricShared(circleId: String, metricType: MetricType): Boolean = 
        socialRepository.isMetricShared(circleId, metricType)

    // ==================== Challenges ====================

    override fun getCircleChallenges(circleId: String): Flow<List<CircleChallenge>> = 
        socialRepository.getCircleChallenges(circleId)

    override suspend fun createChallenge(
        circleId: String,
        name: String,
        description: String,
        metricType: MetricType,
        targetValue: Double,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleChallenge> {
        // Validate input
        if (name.isBlank()) {
            return Result.Error(AppException.ValidationException("name", "Challenge name cannot be empty"))
        }
        if (targetValue <= 0) {
            return Result.Error(AppException.ValidationException("targetValue", "Target must be positive"))
        }
        if (endDate.isBefore(startDate)) {
            return Result.Error(AppException.ValidationException("dates", "End date must be after start date"))
        }
        
        return socialRepository.createChallenge(
            circleId, name.trim(), description.trim(), metricType, targetValue, startDate, endDate
        )
    }

    override suspend fun joinChallenge(challengeId: String): Result<Unit> = 
        socialRepository.joinChallenge(challengeId)

    // ==================== Aggregation with Privacy Enforcement ====================

    /**
     * Calculate aggregated metrics for a circle, respecting privacy settings.
     * 
     * Property 27: Circle aggregation includes only opted-in metrics.
     * - Only includes data from members who have opted to share the specific metric type
     * - Members who haven't shared the metric are excluded from aggregation
     */
    override suspend fun calculateCircleAggregation(
        circleId: String,
        metricType: MetricType,
        startDate: Instant,
        endDate: Instant
    ): Result<Double> {
        return try {
            val circle = socialRepository.getCircle(circleId).first()
                ?: return Result.Error(AppException.ValidationException("circleId", "Circle not found"))

            // Filter members who have opted to share this metric
            val optedInMembers = circle.members.filter { member ->
                member.sharesMetric(metricType)
            }

            if (optedInMembers.isEmpty()) {
                return Result.Success(0.0)
            }

            // Aggregate data only from opted-in members
            var totalValue = 0.0
            var contributingMembers = 0

            for (member in optedInMembers) {
                val memberMetrics = getMemberMetricValue(member.userId, metricType, startDate, endDate)
                if (memberMetrics != null) {
                    totalValue += memberMetrics
                    contributingMembers++
                }
            }

            Timber.d("Circle aggregation: $contributingMembers/${optedInMembers.size} members contributed to $metricType")
            Result.Success(totalValue)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate circle aggregation")
            Result.Error(AppException.StorageException("Failed to calculate aggregation: ${e.message}", e))
        }
    }

    /**
     * Get member metrics for display, filtering out unshared metrics.
     * 
     * Property 28: Unshared metrics don't appear in views.
     * - Only returns metrics that the member has explicitly opted to share
     * - Sensitive metrics are never returned unless explicitly shared
     */
    override suspend fun getMemberMetricsForDisplay(
        circleId: String,
        memberId: String,
        metricTypes: Set<MetricType>
    ): Result<Map<MetricType, Double>> {
        return try {
            val circle = socialRepository.getCircle(circleId).first()
                ?: return Result.Error(AppException.ValidationException("circleId", "Circle not found"))

            val member = circle.members.find { it.userId == memberId }
                ?: return Result.Error(AppException.ValidationException("memberId", "Member not found"))

            // Filter to only shared metrics
            val sharedMetricTypes = metricTypes.filter { metricType ->
                member.sharesMetric(metricType)
            }.toSet()

            // Get values only for shared metrics
            val result = mutableMapOf<MetricType, Double>()
            val now = Instant.now()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

            for (metricType in sharedMetricTypes) {
                val value = getMemberMetricValue(memberId, metricType, startOfDay, now)
                if (value != null) {
                    result[metricType] = value
                }
            }

            Timber.d("Returning ${result.size}/${metricTypes.size} metrics for member $memberId (privacy filtered)")
            Result.Success(result)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get member metrics for display")
            Result.Error(AppException.StorageException("Failed to get member metrics: ${e.message}", e))
        }
    }

    /**
     * Helper to get a specific metric value for a member.
     */
    private suspend fun getMemberMetricValue(
        userId: String,
        metricType: MetricType,
        startDate: Instant,
        endDate: Instant
    ): Double? {
        return try {
            val startLocalDate = startDate.atZone(ZoneId.systemDefault()).toLocalDate()
            val endLocalDate = endDate.atZone(ZoneId.systemDefault()).toLocalDate()
            
            val metrics = healthDataRepository.getHealthMetricsRange(startLocalDate, endLocalDate).first()
            val userMetrics = metrics.filter { it.userId == userId }
            
            if (userMetrics.isEmpty()) return null

            when (metricType) {
                MetricType.STEPS -> userMetrics.sumOf { it.steps }.toDouble()
                MetricType.DISTANCE -> userMetrics.sumOf { it.distanceMeters }
                MetricType.CALORIES -> userMetrics.sumOf { it.caloriesBurned }
                MetricType.SLEEP -> userMetrics.map { it.sleepDurationMinutes }.average()
                MetricType.SCREEN_TIME -> userMetrics.map { it.screenTimeMinutes }.average()
                MetricType.HEART_RATE -> userMetrics.flatMap { it.heartRateSamples }.map { it.bpm }.average()
                MetricType.HRV -> userMetrics.flatMap { it.hrvSamples }.map { it.sdnn }.average()
                MetricType.MOOD -> null // Mood is not numeric
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get metric value for user $userId")
            null
        }
    }
}
