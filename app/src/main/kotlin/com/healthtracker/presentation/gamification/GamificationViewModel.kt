package com.healthtracker.presentation.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.Achievement
import com.healthtracker.domain.model.Badge
import com.healthtracker.domain.model.LeaderboardEntry
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.UserProgress
import com.healthtracker.domain.usecase.GamificationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the gamification screen.
 */
data class GamificationUiState(
    val isLoading: Boolean = true,
    val progress: UserProgress? = null,
    val streaks: List<Streak> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    val recentAchievements: List<Achievement> = emptyList(),
    val selectedTab: GamificationTab = GamificationTab.PROGRESS,
    val error: String? = null
)

/**
 * Tabs in the gamification screen.
 */
enum class GamificationTab {
    PROGRESS,
    STREAKS,
    BADGES,
    LEADERBOARD
}

/**
 * ViewModel for the gamification screen.
 */
@HiltViewModel
class GamificationViewModel @Inject constructor(
    private val gamificationUseCase: GamificationUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(GamificationUiState())
    val uiState: StateFlow<GamificationUiState> = _uiState.asStateFlow()
    
    init {
        loadGamificationData()
    }
    
    /**
     * Load all gamification data.
     */
    fun loadGamificationData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Collect progress
                launch {
                    gamificationUseCase.getProgress().collect { progress ->
                        _uiState.update { it.copy(progress = progress) }
                    }
                }
                
                // Collect streaks
                launch {
                    gamificationUseCase.getStreaks().collect { streaks ->
                        _uiState.update { it.copy(streaks = streaks) }
                    }
                }
                
                // Collect badges
                launch {
                    gamificationUseCase.getBadges().collect { badges ->
                        _uiState.update { it.copy(badges = badges) }
                    }
                }
                
                // Collect leaderboard
                launch {
                    gamificationUseCase.getLeaderboard().collect { leaderboard ->
                        _uiState.update { it.copy(leaderboard = leaderboard) }
                    }
                }
                
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "Failed to load gamification data"
                    ) 
                }
            }
        }
    }
    
    /**
     * Select a tab.
     */
    fun selectTab(tab: GamificationTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
    
    /**
     * Sync gamification state.
     */
    fun syncState() {
        viewModelScope.launch {
            when (val result = gamificationUseCase.syncState()) {
                is Result.Success -> {
                    // Refresh data after sync
                    loadGamificationData()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = result.exception.message) }
                }
            }
        }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
