package com.healthtracker.data.repository

import com.healthtracker.data.local.dao.SocialDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.local.entity.CircleChallengeEntity
import com.healthtracker.data.local.entity.CircleInvitationEntity
import com.healthtracker.data.local.entity.CircleLeaderboardCacheEntity
import com.healthtracker.data.local.entity.CircleMemberEntity
import com.healthtracker.data.local.entity.CirclePrivacySettingsEntity
import com.healthtracker.data.local.entity.CircleProgressEntity
import com.healthtracker.data.local.entity.HealthCircleEntity
import com.healthtracker.domain.model.AppException
import com.healthtracker.domain.model.CircleChallenge
import com.healthtracker.domain.model.CircleConfig
import com.healthtracker.domain.model.CircleInvitation
import com.healthtracker.domain.model.CircleLeaderboardEntry
import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleProgress
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.InvitationStatus
import com.healthtracker.domain.model.JoinCircleResult
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.TrendDirection
import com.healthtracker.domain.repository.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Implementation of SocialRepository.
 * Handles local storage and sync of social/circle data.
 */
@Singleton
class SocialRepositoryImpl @Inject constructor(
    private val socialDao: SocialDao,
    private val userDao: UserDao,
    private val json: Json
) : SocialRepository {

    // ==================== Circle Management ====================

    override fun getCircles(): Flow<List<HealthCircle>> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                socialDao.getCirclesByUser(user.id).map { entity ->
                    val members = socialDao.getMembers(entity.id)
                    entity.toDomain(members.map { it.toDomain() })
                }
            } ?: emptyList()
        }
    }

    override fun getCircle(circleId: String): Flow<HealthCircle?> {
        return socialDao.getCircleFlow(circleId).map { entity ->
            entity?.let {
                val members = socialDao.getMembers(circleId)
                it.toDomain(members.map { m -> m.toDomain() })
            }
        }
    }

    override suspend fun createCircle(
        name: String,
        type: CircleType,
        description: String?
    ): Result<HealthCircle> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            // Check circle limit
            val existingCircles = socialDao.getCirclesByUser(user.id)
            if (existingCircles.size >= CircleConfig.MAX_CIRCLES_PER_USER) {
                return@withContext Result.Error(
                    AppException.ValidationException("circles", "Maximum circles limit reached")
                )
            }

            val now = System.currentTimeMillis()
            val circleId = UUID.randomUUID().toString()
            val joinCode = generateJoinCode()

            val circleEntity = HealthCircleEntity(
                id = circleId,
                name = name,
                type = type.name,
                ownerId = user.id,
                joinCode = joinCode,
                description = description,
                createdAt = now,
                isActive = true,
                lastUpdated = now,
                needsSync = true
            )

            // Create owner as first member
            val ownerMember = CircleMemberEntity(
                id = UUID.randomUUID().toString(),
                circleId = circleId,
                userId = user.id,
                displayName = user.name,
                avatarUrl = null,
                role = MemberRole.OWNER.name,
                sharedMetricsJson = json.encodeToString(
                    CirclePrivacySettings.DEFAULT_SHARED_METRICS.map { it.name }
                ),
                joinedAt = now,
                lastActiveAt = now,
                needsSync = true
            )

            socialDao.insertCircle(circleEntity)
            socialDao.insertMember(ownerMember)

            Timber.d("Circle created: $name (${type.name})")

            val circle = circleEntity.toDomain(listOf(ownerMember.toDomain()))
            Result.Success(circle)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create circle")
            Result.Error(AppException.StorageException("Failed to create circle: ${e.message}", e))
        }
    }

    override suspend fun joinCircle(code: String): JoinCircleResult = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext JoinCircleResult.Error("No user found")

            val circleEntity = socialDao.getCircleByJoinCode(code.uppercase())
                ?: return@withContext JoinCircleResult.InvalidCode()

            // Check if already a member
            val existingMember = socialDao.getMember(circleEntity.id, user.id)
            if (existingMember != null) {
                return@withContext JoinCircleResult.AlreadyMember()
            }

            // Check member limit
            val memberCount = socialDao.getMemberCount(circleEntity.id)
            val maxMembers = CircleConfig.getMaxMembers(CircleType.valueOf(circleEntity.type))
            if (memberCount >= maxMembers) {
                return@withContext JoinCircleResult.CircleFull()
            }

            val now = System.currentTimeMillis()
            val newMember = CircleMemberEntity(
                id = UUID.randomUUID().toString(),
                circleId = circleEntity.id,
                userId = user.id,
                displayName = user.name,
                avatarUrl = null,
                role = MemberRole.MEMBER.name,
                sharedMetricsJson = json.encodeToString(
                    CirclePrivacySettings.DEFAULT_SHARED_METRICS.map { it.name }
                ),
                joinedAt = now,
                lastActiveAt = now,
                needsSync = true
            )

            socialDao.insertMember(newMember)
            Timber.d("Joined circle: ${circleEntity.name}")

            val members = socialDao.getMembers(circleEntity.id)
            val circle = circleEntity.toDomain(members.map { it.toDomain() })
            JoinCircleResult.Success(circle)
        } catch (e: Exception) {
            Timber.e(e, "Failed to join circle")
            JoinCircleResult.Error("Failed to join circle: ${e.message}")
        }
    }

    override suspend fun leaveCircle(circleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                AppException.ValidationException("circleId", "Circle not found")
            )

            // Owner cannot leave, must delete circle
            if (circle.ownerId == user.id) {
                return@withContext Result.Error(
                    AppException.ValidationException("owner", "Owner cannot leave circle. Delete the circle instead.")
                )
            }

            socialDao.removeMember(circleId, user.id)
            Timber.d("Left circle: ${circle.name}")

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to leave circle")
            Result.Error(AppException.StorageException("Failed to leave circle: ${e.message}", e))
        }
    }

    override suspend fun deleteCircle(circleId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                AppException.ValidationException("circleId", "Circle not found")
            )

            if (circle.ownerId != user.id) {
                return@withContext Result.Error(
                    AppException.PermissionException("Only the owner can delete a circle")
                )
            }

            socialDao.deleteCircleWithMembers(circleId)
            Timber.d("Deleted circle: ${circle.name}")

            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete circle")
            Result.Error(AppException.StorageException("Failed to delete circle: ${e.message}", e))
        }
    }

    override suspend fun updateCircle(
        circleId: String,
        name: String?,
        description: String?
    ): Result<HealthCircle> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                AppException.ValidationException("circleId", "Circle not found")
            )

            val member = socialDao.getMember(circleId, user.id)
            if (member == null || (member.role != MemberRole.OWNER.name && member.role != MemberRole.ADMIN.name)) {
                return@withContext Result.Error(
                    AppException.PermissionException("Only owner or admin can update circle")
                )
            }

            val now = System.currentTimeMillis()
            val updatedCircle = circle.copy(
                name = name ?: circle.name,
                description = description ?: circle.description,
                lastUpdated = now,
                needsSync = true
            )

            socialDao.updateCircle(updatedCircle)
            val members = socialDao.getMembers(circleId)
            Result.Success(updatedCircle.toDomain(members.map { it.toDomain() }))
        } catch (e: Exception) {
            Timber.e(e, "Failed to update circle")
            Result.Error(AppException.StorageException("Failed to update circle: ${e.message}", e))
        }
    }

    override suspend fun regenerateJoinCode(circleId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val member = socialDao.getMember(circleId, user.id)
            if (member == null || (member.role != MemberRole.OWNER.name && member.role != MemberRole.ADMIN.name)) {
                return@withContext Result.Error(
                    AppException.PermissionException("Only owner or admin can regenerate join code")
                )
            }

            val newCode = generateJoinCode()
            socialDao.updateJoinCode(circleId, newCode, System.currentTimeMillis())
            Result.Success(newCode)
        } catch (e: Exception) {
            Timber.e(e, "Failed to regenerate join code")
            Result.Error(AppException.StorageException("Failed to regenerate join code: ${e.message}", e))
        }
    }

    // ==================== Member Management ====================

    override fun getCircleMembers(circleId: String): Flow<List<CircleMember>> {
        return socialDao.getMembersFlow(circleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun removeMember(circleId: String, userId: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                    AppException.ValidationException("circleId", "Circle not found")
                )

                val currentMember = socialDao.getMember(circleId, user.id)
                if (currentMember == null || 
                    (currentMember.role != MemberRole.OWNER.name && currentMember.role != MemberRole.ADMIN.name)) {
                    return@withContext Result.Error(
                        AppException.PermissionException("Only owner or admin can remove members")
                    )
                }

                // Cannot remove owner
                if (userId == circle.ownerId) {
                    return@withContext Result.Error(
                        AppException.ValidationException("userId", "Cannot remove circle owner")
                    )
                }

                socialDao.removeMember(circleId, userId)
                Timber.d("Removed member $userId from circle $circleId")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove member")
                Result.Error(AppException.StorageException("Failed to remove member: ${e.message}", e))
            }
        }

    override suspend fun updateMemberRole(
        circleId: String,
        userId: String,
        newRole: MemberRole
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                AppException.ValidationException("circleId", "Circle not found")
            )

            // Only owner can change roles
            if (circle.ownerId != user.id) {
                return@withContext Result.Error(
                    AppException.PermissionException("Only owner can change member roles")
                )
            }

            // Cannot change owner role
            if (userId == circle.ownerId) {
                return@withContext Result.Error(
                    AppException.ValidationException("userId", "Cannot change owner's role")
                )
            }

            socialDao.updateMemberRole(circleId, userId, newRole.name)
            Timber.d("Updated role for $userId to ${newRole.name}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update member role")
            Result.Error(AppException.StorageException("Failed to update member role: ${e.message}", e))
        }
    }

    // ==================== Leaderboards & Progress ====================

    override fun getCircleLeaderboard(
        circleId: String,
        metricType: MetricType,
        limit: Int
    ): Flow<List<CircleLeaderboardEntry>> {
        return socialDao.getLeaderboardFlow(circleId, metricType.name, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCircleProgress(
        circleId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleProgress> = withContext(Dispatchers.IO) {
        try {
            val cached = socialDao.getProgress(
                circleId,
                startDate.toEpochMilli(),
                endDate.toEpochMilli()
            )

            if (cached != null) {
                return@withContext Result.Success(cached.toDomain())
            }

            // Calculate progress from member data
            val members = socialDao.getMembers(circleId)
            val progress = calculateCircleProgress(circleId, members, startDate, endDate)

            // Cache the result
            val progressEntity = CircleProgressEntity(
                id = "${circleId}_${startDate.toEpochMilli()}_${endDate.toEpochMilli()}",
                circleId = circleId,
                totalSteps = progress.totalSteps,
                totalCalories = progress.totalCalories,
                averageSleepMinutes = progress.averageSleepMinutes,
                participatingMembers = progress.participatingMembers,
                periodStart = startDate.toEpochMilli(),
                periodEnd = endDate.toEpochMilli(),
                cachedAt = System.currentTimeMillis()
            )
            socialDao.insertProgress(progressEntity)

            Result.Success(progress)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get circle progress")
            Result.Error(AppException.StorageException("Failed to get circle progress: ${e.message}", e))
        }
    }

    override suspend fun getUserRank(circleId: String, metricType: MetricType): Result<Int> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val rank = socialDao.getUserRank(circleId, user.id, metricType.name)
                    ?: return@withContext Result.Error(
                        AppException.ValidationException("rank", "User not found in leaderboard")
                    )

                Result.Success(rank)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get user rank")
                Result.Error(AppException.StorageException("Failed to get user rank: ${e.message}", e))
            }
        }

    // ==================== Privacy Controls ====================

    override fun getPrivacySettings(): Flow<CirclePrivacySettings> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                val settings = socialDao.getPrivacySettings(user.id)
                settings?.toDomain() ?: CirclePrivacySettings(userId = user.id)
            } ?: CirclePrivacySettings(userId = "")
        }
    }

    override suspend fun updateSharedMetrics(
        circleId: String,
        sharedMetrics: Set<MetricType>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            // Update member's shared metrics
            val metricsJson = json.encodeToString(sharedMetrics.map { it.name })
            socialDao.updateMemberSharedMetrics(circleId, user.id, metricsJson)

            // Update privacy settings overrides
            val settings = socialDao.getPrivacySettings(user.id)
            val currentOverrides = settings?.let {
                json.decodeFromString<Map<String, List<String>>>(it.circleOverridesJson)
            } ?: emptyMap()

            val newOverrides = currentOverrides.toMutableMap()
            newOverrides[circleId] = sharedMetrics.map { it.name }

            val now = System.currentTimeMillis()
            val updatedSettings = CirclePrivacySettingsEntity(
                userId = user.id,
                defaultSharedMetricsJson = settings?.defaultSharedMetricsJson 
                    ?: json.encodeToString(CirclePrivacySettings.DEFAULT_SHARED_METRICS.map { it.name }),
                circleOverridesJson = json.encodeToString(newOverrides),
                lastUpdated = now,
                needsSync = true
            )
            socialDao.insertPrivacySettings(updatedSettings)

            Timber.d("Updated shared metrics for circle $circleId")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update shared metrics")
            Result.Error(AppException.StorageException("Failed to update shared metrics: ${e.message}", e))
        }
    }

    override suspend fun updateDefaultSharedMetrics(sharedMetrics: Set<MetricType>): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val now = System.currentTimeMillis()
                val settings = socialDao.getPrivacySettings(user.id)
                val updatedSettings = CirclePrivacySettingsEntity(
                    userId = user.id,
                    defaultSharedMetricsJson = json.encodeToString(sharedMetrics.map { it.name }),
                    circleOverridesJson = settings?.circleOverridesJson ?: "{}",
                    lastUpdated = now,
                    needsSync = true
                )
                socialDao.insertPrivacySettings(updatedSettings)

                Timber.d("Updated default shared metrics")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update default shared metrics")
                Result.Error(AppException.StorageException("Failed to update default shared metrics: ${e.message}", e))
            }
        }

    override suspend fun isMetricShared(circleId: String, metricType: MetricType): Boolean = 
        withContext(Dispatchers.IO) {
            val user = userDao.getUser() ?: return@withContext false
            val member = socialDao.getMember(circleId, user.id) ?: return@withContext false
            val sharedMetrics = json.decodeFromString<List<String>>(member.sharedMetricsJson)
            metricType.name in sharedMetrics
        }

    // ==================== Invitations ====================

    override fun getPendingInvitations(): Flow<List<CircleInvitation>> {
        return userDao.getUserFlow().map { userEntity ->
            userEntity?.let { user ->
                socialDao.getPendingInvitations(user.id).map { it.toDomain() }
            } ?: emptyList()
        }
    }

    override suspend fun sendInvitation(circleId: String, inviteeId: String): Result<CircleInvitation> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                    AppException.ValidationException("circleId", "Circle not found")
                )

                val member = socialDao.getMember(circleId, user.id)
                if (member == null || member.role == MemberRole.MEMBER.name) {
                    return@withContext Result.Error(
                        AppException.PermissionException("Only owner or admin can send invitations")
                    )
                }

                val now = System.currentTimeMillis()
                val expiresAt = now + (CircleConfig.INVITATION_EXPIRY_DAYS * 24 * 60 * 60 * 1000)

                val invitation = CircleInvitationEntity(
                    id = UUID.randomUUID().toString(),
                    circleId = circleId,
                    circleName = circle.name,
                    inviterId = user.id,
                    inviterName = user.name,
                    inviteeId = inviteeId,
                    createdAt = now,
                    expiresAt = expiresAt,
                    status = InvitationStatus.PENDING.name,
                    needsSync = true
                )

                socialDao.insertInvitation(invitation)
                Timber.d("Sent invitation to $inviteeId for circle ${circle.name}")
                Result.Success(invitation.toDomain())
            } catch (e: Exception) {
                Timber.e(e, "Failed to send invitation")
                Result.Error(AppException.StorageException("Failed to send invitation: ${e.message}", e))
            }
        }

    override suspend fun acceptInvitation(invitationId: String): Result<HealthCircle> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val invitation = socialDao.getInvitation(invitationId) ?: return@withContext Result.Error(
                    AppException.ValidationException("invitationId", "Invitation not found")
                )

                if (invitation.inviteeId != user.id) {
                    return@withContext Result.Error(
                        AppException.PermissionException("This invitation is not for you")
                    )
                }

                if (invitation.status != InvitationStatus.PENDING.name) {
                    return@withContext Result.Error(
                        AppException.ValidationException("status", "Invitation is no longer pending")
                    )
                }

                if (invitation.expiresAt < System.currentTimeMillis()) {
                    socialDao.updateInvitationStatus(invitationId, InvitationStatus.EXPIRED.name)
                    return@withContext Result.Error(
                        AppException.ValidationException("expiry", "Invitation has expired")
                    )
                }

                // Join the circle
                val now = System.currentTimeMillis()
                val newMember = CircleMemberEntity(
                    id = UUID.randomUUID().toString(),
                    circleId = invitation.circleId,
                    userId = user.id,
                    displayName = user.name,
                    avatarUrl = null,
                    role = MemberRole.MEMBER.name,
                    sharedMetricsJson = json.encodeToString(
                        CirclePrivacySettings.DEFAULT_SHARED_METRICS.map { it.name }
                    ),
                    joinedAt = now,
                    lastActiveAt = now,
                    needsSync = true
                )

                socialDao.insertMember(newMember)
                socialDao.updateInvitationStatus(invitationId, InvitationStatus.ACCEPTED.name)

                val circle = socialDao.getCircle(invitation.circleId)!!
                val members = socialDao.getMembers(invitation.circleId)
                Result.Success(circle.toDomain(members.map { it.toDomain() }))
            } catch (e: Exception) {
                Timber.e(e, "Failed to accept invitation")
                Result.Error(AppException.StorageException("Failed to accept invitation: ${e.message}", e))
            }
        }

    override suspend fun declineInvitation(invitationId: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUser() ?: return@withContext Result.Error(
                    AppException.AuthException("No user found")
                )

                val invitation = socialDao.getInvitation(invitationId) ?: return@withContext Result.Error(
                    AppException.ValidationException("invitationId", "Invitation not found")
                )

                if (invitation.inviteeId != user.id) {
                    return@withContext Result.Error(
                        AppException.PermissionException("This invitation is not for you")
                    )
                }

                socialDao.updateInvitationStatus(invitationId, InvitationStatus.DECLINED.name)
                Timber.d("Declined invitation $invitationId")
                Result.Success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to decline invitation")
                Result.Error(AppException.StorageException("Failed to decline invitation: ${e.message}", e))
            }
        }

    // ==================== Challenges ====================

    override fun getCircleChallenges(circleId: String): Flow<List<CircleChallenge>> {
        return socialDao.getChallengesFlow(circleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createChallenge(
        circleId: String,
        name: String,
        description: String,
        metricType: MetricType,
        targetValue: Double,
        startDate: Instant,
        endDate: Instant
    ): Result<CircleChallenge> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val member = socialDao.getMember(circleId, user.id)
            if (member == null || member.role == MemberRole.MEMBER.name) {
                return@withContext Result.Error(
                    AppException.PermissionException("Only owner or admin can create challenges")
                )
            }

            val challenge = CircleChallengeEntity(
                id = UUID.randomUUID().toString(),
                circleId = circleId,
                name = name,
                description = description,
                metricType = metricType.name,
                targetValue = targetValue,
                startDate = startDate.toEpochMilli(),
                endDate = endDate.toEpochMilli(),
                participantsJson = json.encodeToString(listOf(user.id)),
                isActive = true,
                needsSync = true
            )

            socialDao.insertChallenge(challenge)
            Timber.d("Created challenge: $name")
            Result.Success(challenge.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Failed to create challenge")
            Result.Error(AppException.StorageException("Failed to create challenge: ${e.message}", e))
        }
    }

    override suspend fun joinChallenge(challengeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUser() ?: return@withContext Result.Error(
                AppException.AuthException("No user found")
            )

            val challenge = socialDao.getChallenge(challengeId) ?: return@withContext Result.Error(
                AppException.ValidationException("challengeId", "Challenge not found")
            )

            val participants = json.decodeFromString<List<String>>(challenge.participantsJson).toMutableList()
            if (user.id in participants) {
                return@withContext Result.Error(
                    AppException.ValidationException("participant", "Already participating in this challenge")
                )
            }

            participants.add(user.id)
            socialDao.updateChallengeParticipants(challengeId, json.encodeToString(participants))
            Timber.d("Joined challenge: ${challenge.name}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to join challenge")
            Result.Error(AppException.StorageException("Failed to join challenge: ${e.message}", e))
        }
    }

    // ==================== Sync ====================

    override suspend fun syncCircles(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get items needing sync
            val circlesToSync = socialDao.getCirclesNeedingSync()
            val membersToSync = socialDao.getMembersNeedingSync()
            val settingsToSync = socialDao.getPrivacySettingsNeedingSync()

            // TODO: Sync to Firebase
            // For now, just mark as synced

            circlesToSync.forEach { socialDao.markCircleAsSynced(it.id) }
            membersToSync.forEach { socialDao.markMemberAsSynced(it.circleId, it.userId) }
            settingsToSync.forEach { socialDao.markPrivacySettingsAsSynced(it.userId) }

            Timber.d("Social data synced")
            Result.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync social data")
            Result.Error(AppException.NetworkException("Failed to sync social data: ${e.message}", e))
        }
    }

    override suspend fun refreshCircle(circleId: String): Result<HealthCircle> = 
        withContext(Dispatchers.IO) {
            try {
                // TODO: Fetch from Firebase
                // For now, return local data
                val circle = socialDao.getCircle(circleId) ?: return@withContext Result.Error(
                    AppException.ValidationException("circleId", "Circle not found")
                )
                val members = socialDao.getMembers(circleId)
                Result.Success(circle.toDomain(members.map { it.toDomain() }))
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh circle")
                Result.Error(AppException.NetworkException("Failed to refresh circle: ${e.message}", e))
            }
        }

    // ==================== Helper Functions ====================

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Exclude confusing chars
        return (1..CircleConfig.JOIN_CODE_LENGTH)
            .map { chars.random() }
            .joinToString("")
    }

    private suspend fun calculateCircleProgress(
        circleId: String,
        members: List<CircleMemberEntity>,
        startDate: Instant,
        endDate: Instant
    ): CircleProgress {
        // TODO: Aggregate actual health data from members
        // For now, return placeholder data
        return CircleProgress(
            circleId = circleId,
            totalSteps = 0L,
            totalCalories = 0.0,
            averageSleepMinutes = 0.0,
            participatingMembers = members.size,
            periodStart = startDate,
            periodEnd = endDate
        )
    }

    // ==================== Entity Conversion Extensions ====================

    private fun HealthCircleEntity.toDomain(members: List<CircleMember>): HealthCircle {
        return HealthCircle(
            id = id,
            name = name,
            type = CircleType.valueOf(type),
            ownerId = ownerId,
            members = members,
            joinCode = joinCode,
            description = description,
            createdAt = Instant.ofEpochMilli(createdAt),
            isActive = isActive
        )
    }

    private fun CircleMemberEntity.toDomain(): CircleMember {
        val sharedMetrics = try {
            json.decodeFromString<List<String>>(sharedMetricsJson)
                .mapNotNull { name -> 
                    try { MetricType.valueOf(name) } catch (e: Exception) { null }
                }
                .toSet()
        } catch (e: Exception) {
            CirclePrivacySettings.DEFAULT_SHARED_METRICS
        }

        return CircleMember(
            userId = userId,
            displayName = displayName,
            avatarUrl = avatarUrl,
            role = MemberRole.valueOf(role),
            sharedMetrics = sharedMetrics,
            joinedAt = Instant.ofEpochMilli(joinedAt),
            lastActiveAt = lastActiveAt?.let { Instant.ofEpochMilli(it) }
        )
    }

    private fun CirclePrivacySettingsEntity.toDomain(): CirclePrivacySettings {
        val defaultMetrics = try {
            json.decodeFromString<List<String>>(defaultSharedMetricsJson)
                .mapNotNull { name -> 
                    try { MetricType.valueOf(name) } catch (e: Exception) { null }
                }
                .toSet()
        } catch (e: Exception) {
            CirclePrivacySettings.DEFAULT_SHARED_METRICS
        }

        val overrides = try {
            json.decodeFromString<Map<String, List<String>>>(circleOverridesJson)
                .mapValues { (_, metrics) ->
                    metrics.mapNotNull { name -> 
                        try { MetricType.valueOf(name) } catch (e: Exception) { null }
                    }.toSet()
                }
        } catch (e: Exception) {
            emptyMap()
        }

        return CirclePrivacySettings(
            userId = userId,
            defaultSharedMetrics = defaultMetrics,
            circleOverrides = overrides
        )
    }

    private fun CircleInvitationEntity.toDomain(): CircleInvitation {
        return CircleInvitation(
            id = id,
            circleId = circleId,
            circleName = circleName,
            inviterId = inviterId,
            inviterName = inviterName,
            inviteeId = inviteeId,
            createdAt = Instant.ofEpochMilli(createdAt),
            expiresAt = Instant.ofEpochMilli(expiresAt),
            status = InvitationStatus.valueOf(status)
        )
    }

    private fun CircleChallengeEntity.toDomain(): CircleChallenge {
        val participants = try {
            json.decodeFromString<List<String>>(participantsJson)
        } catch (e: Exception) {
            emptyList()
        }

        return CircleChallenge(
            id = id,
            circleId = circleId,
            name = name,
            description = description,
            metricType = MetricType.valueOf(metricType),
            targetValue = targetValue,
            startDate = Instant.ofEpochMilli(startDate),
            endDate = Instant.ofEpochMilli(endDate),
            participants = participants,
            isActive = isActive
        )
    }

    private fun CircleLeaderboardCacheEntity.toDomain(): CircleLeaderboardEntry {
        return CircleLeaderboardEntry(
            rank = rank,
            member = CircleMember(
                userId = userId,
                displayName = displayName,
                avatarUrl = avatarUrl,
                role = MemberRole.MEMBER, // Role not stored in cache
                sharedMetrics = emptySet(),
                joinedAt = Instant.now()
            ),
            score = score,
            metricValue = metricValue,
            trend = TrendDirection.valueOf(trend),
            periodContribution = periodContribution
        )
    }

    private fun CircleProgressEntity.toDomain(): CircleProgress {
        return CircleProgress(
            circleId = circleId,
            totalSteps = totalSteps,
            totalCalories = totalCalories,
            averageSleepMinutes = averageSleepMinutes,
            participatingMembers = participatingMembers,
            periodStart = Instant.ofEpochMilli(periodStart),
            periodEnd = Instant.ofEpochMilli(periodEnd)
        )
    }
}
