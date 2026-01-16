package com.healthtracker.presentation.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.CircleChallenge
import com.healthtracker.domain.model.CircleLeaderboardEntry
import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.JoinCircleResult
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.usecase.SocialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel for Health Circles social features.
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val socialUseCase: SocialUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SocialUiState())
    val uiState: StateFlow<SocialUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SocialEvent>()
    val events: SharedFlow<SocialEvent> = _events.asSharedFlow()

    init {
        loadCircles()
        loadPrivacySettings()
    }

    // ==================== Circle Management ====================

    fun loadCircles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            socialUseCase.getMyCircles().collect { circles ->
                _uiState.update { it.copy(circles = circles, isLoading = false) }
            }
        }
    }

    fun selectCircle(circleId: String) {
        viewModelScope.launch {
            socialUseCase.getCircle(circleId).collect { circle ->
                _uiState.update { it.copy(selectedCircle = circle) }
                circle?.let {
                    loadCircleLeaderboard(circleId)
                    loadCircleChallenges(circleId)
                }
            }
        }
    }

    fun createCircle(name: String, type: CircleType, description: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = socialUseCase.createCircle(name, type, description)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.CircleCreated(result.data))
                    loadCircles()
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to create circle"))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun joinCircle(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = socialUseCase.joinCircle(code)) {
                is JoinCircleResult.Success -> {
                    _events.emit(SocialEvent.CircleJoined(result.circle))
                    loadCircles()
                }
                is JoinCircleResult.InvalidCode -> {
                    _events.emit(SocialEvent.Error(result.message))
                    _uiState.update { it.copy(isLoading = false) }
                }
                is JoinCircleResult.AlreadyMember -> {
                    _events.emit(SocialEvent.Error(result.message))
                    _uiState.update { it.copy(isLoading = false) }
                }
                is JoinCircleResult.CircleFull -> {
                    _events.emit(SocialEvent.Error(result.message))
                    _uiState.update { it.copy(isLoading = false) }
                }
                is JoinCircleResult.Error -> {
                    _events.emit(SocialEvent.Error(result.message))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun leaveCircle(circleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = socialUseCase.leaveCircle(circleId)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.CircleLeft)
                    _uiState.update { it.copy(selectedCircle = null) }
                    loadCircles()
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to leave circle"))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun deleteCircle(circleId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = socialUseCase.deleteCircle(circleId)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.CircleDeleted)
                    _uiState.update { it.copy(selectedCircle = null) }
                    loadCircles()
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to delete circle"))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    // ==================== Member Management ====================

    fun loadCircleMembers(circleId: String) {
        viewModelScope.launch {
            socialUseCase.getCircleMembers(circleId).collect { members ->
                _uiState.update { it.copy(selectedCircleMembers = members) }
            }
        }
    }

    fun removeMember(circleId: String, userId: String) {
        viewModelScope.launch {
            when (val result = socialUseCase.removeMember(circleId, userId)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.MemberRemoved)
                    loadCircleMembers(circleId)
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to remove member"))
                }
            }
        }
    }

    fun updateMemberRole(circleId: String, userId: String, newRole: MemberRole) {
        viewModelScope.launch {
            when (val result = socialUseCase.updateMemberRole(circleId, userId, newRole)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.RoleUpdated)
                    loadCircleMembers(circleId)
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to update role"))
                }
            }
        }
    }

    // ==================== Leaderboards ====================

    fun loadCircleLeaderboard(circleId: String, metricType: MetricType = MetricType.STEPS) {
        viewModelScope.launch {
            socialUseCase.getCircleLeaderboard(circleId, metricType).collect { entries ->
                _uiState.update { it.copy(leaderboard = entries, selectedMetricType = metricType) }
            }
        }
    }

    fun changeLeaderboardMetric(circleId: String, metricType: MetricType) {
        loadCircleLeaderboard(circleId, metricType)
    }

    // ==================== Privacy Settings ====================

    private fun loadPrivacySettings() {
        viewModelScope.launch {
            socialUseCase.getPrivacySettings().collect { settings ->
                _uiState.update { it.copy(privacySettings = settings) }
            }
        }
    }

    fun updateSharedMetrics(circleId: String, sharedMetrics: Set<MetricType>) {
        viewModelScope.launch {
            when (val result = socialUseCase.updateSharedMetrics(circleId, sharedMetrics)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.PrivacyUpdated)
                    loadPrivacySettings()
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to update privacy"))
                }
            }
        }
    }

    fun updateDefaultSharedMetrics(sharedMetrics: Set<MetricType>) {
        viewModelScope.launch {
            when (val result = socialUseCase.updateDefaultSharedMetrics(sharedMetrics)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.PrivacyUpdated)
                    loadPrivacySettings()
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to update defaults"))
                }
            }
        }
    }

    // ==================== Challenges ====================

    fun loadCircleChallenges(circleId: String) {
        viewModelScope.launch {
            socialUseCase.getCircleChallenges(circleId).collect { challenges ->
                _uiState.update { it.copy(challenges = challenges) }
            }
        }
    }

    fun createChallenge(
        circleId: String,
        name: String,
        description: String,
        metricType: MetricType,
        targetValue: Double,
        durationDays: Int
    ) {
        viewModelScope.launch {
            val startDate = Instant.now()
            val endDate = startDate.plus(durationDays.toLong(), ChronoUnit.DAYS)
            
            when (val result = socialUseCase.createChallenge(
                circleId, name, description, metricType, targetValue, startDate, endDate
            )) {
                is Result.Success -> {
                    _events.emit(SocialEvent.ChallengeCreated(result.data))
                    loadCircleChallenges(circleId)
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to create challenge"))
                }
            }
        }
    }

    fun joinChallenge(challengeId: String, circleId: String) {
        viewModelScope.launch {
            when (val result = socialUseCase.joinChallenge(challengeId)) {
                is Result.Success -> {
                    _events.emit(SocialEvent.ChallengeJoined)
                    loadCircleChallenges(circleId)
                }
                is Result.Error -> {
                    _events.emit(SocialEvent.Error(result.exception.message ?: "Failed to join challenge"))
                }
            }
        }
    }

    // ==================== Dialog State ====================

    fun showCreateCircleDialog() {
        _uiState.update { it.copy(showCreateCircleDialog = true) }
    }

    fun hideCreateCircleDialog() {
        _uiState.update { it.copy(showCreateCircleDialog = false) }
    }

    fun showJoinCircleDialog() {
        _uiState.update { it.copy(showJoinCircleDialog = true) }
    }

    fun hideJoinCircleDialog() {
        _uiState.update { it.copy(showJoinCircleDialog = false) }
    }

    fun showPrivacySettingsDialog() {
        _uiState.update { it.copy(showPrivacySettingsDialog = true) }
    }

    fun hidePrivacySettingsDialog() {
        _uiState.update { it.copy(showPrivacySettingsDialog = false) }
    }
}

/**
 * UI state for social features.
 */
data class SocialUiState(
    val isLoading: Boolean = false,
    val circles: List<HealthCircle> = emptyList(),
    val selectedCircle: HealthCircle? = null,
    val selectedCircleMembers: List<CircleMember> = emptyList(),
    val leaderboard: List<CircleLeaderboardEntry> = emptyList(),
    val selectedMetricType: MetricType = MetricType.STEPS,
    val challenges: List<CircleChallenge> = emptyList(),
    val privacySettings: CirclePrivacySettings? = null,
    val showCreateCircleDialog: Boolean = false,
    val showJoinCircleDialog: Boolean = false,
    val showPrivacySettingsDialog: Boolean = false
)

/**
 * Events emitted by the social ViewModel.
 */
sealed class SocialEvent {
    data class CircleCreated(val circle: HealthCircle) : SocialEvent()
    data class CircleJoined(val circle: HealthCircle) : SocialEvent()
    object CircleLeft : SocialEvent()
    object CircleDeleted : SocialEvent()
    object MemberRemoved : SocialEvent()
    object RoleUpdated : SocialEvent()
    object PrivacyUpdated : SocialEvent()
    data class ChallengeCreated(val challenge: CircleChallenge) : SocialEvent()
    object ChallengeJoined : SocialEvent()
    data class Error(val message: String) : SocialEvent()
}
