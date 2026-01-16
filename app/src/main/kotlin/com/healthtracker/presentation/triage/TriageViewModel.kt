package com.healthtracker.presentation.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.Clinic
import com.healthtracker.domain.model.CommonSymptoms
import com.healthtracker.domain.model.MEDICAL_DISCLAIMER
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.model.SpecialistRecommendation
import com.healthtracker.domain.model.Symptom
import com.healthtracker.domain.model.SymptomSeverity
import com.healthtracker.domain.model.SymptomTemplate
import com.healthtracker.domain.model.TriageResult
import com.healthtracker.domain.model.UrgencyLevel
import com.healthtracker.domain.model.UserLocation
import com.healthtracker.domain.usecase.TriageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * UI State for the Triage screen.
 */
data class TriageUiState(
    val currentStep: TriageStep = TriageStep.DISCLAIMER,
    val disclaimerAccepted: Boolean = false,
    val availableSymptoms: List<SymptomTemplate> = CommonSymptoms.ALL,
    val selectedSymptoms: List<Symptom> = emptyList(),
    val customSymptomName: String = "",
    val customSymptomSeverity: SymptomSeverity = SymptomSeverity.MILD,
    val triageResult: TriageResult? = null,
    val nearbyClinics: List<Clinic> = emptyList(),
    val selectedSpecialty: SpecialistRecommendation? = null,
    val userLocation: UserLocation? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val locationPermissionGranted: Boolean = false
)

/**
 * Steps in the triage flow.
 */
enum class TriageStep {
    DISCLAIMER,
    SYMPTOM_SELECTION,
    RESULTS,
    CLINIC_MAP
}

@HiltViewModel
class TriageViewModel @Inject constructor(
    private val triageUseCase: TriageUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()
    
    /**
     * Accept the medical disclaimer and proceed to symptom selection.
     */
    fun acceptDisclaimer() {
        _uiState.update { it.copy(
            disclaimerAccepted = true,
            currentStep = TriageStep.SYMPTOM_SELECTION
        )}
    }
    
    /**
     * Add a symptom from the predefined list.
     */
    fun addSymptom(template: SymptomTemplate, severity: SymptomSeverity) {
        val symptom = Symptom(
            id = UUID.randomUUID().toString(),
            name = template.displayName,
            severity = severity,
            bodyPart = template.bodyPart
        )
        
        _uiState.update { state ->
            // Avoid duplicates
            if (state.selectedSymptoms.any { it.name == symptom.name }) {
                state
            } else {
                state.copy(selectedSymptoms = state.selectedSymptoms + symptom)
            }
        }
    }
    
    /**
     * Add a custom symptom.
     */
    fun addCustomSymptom() {
        val name = _uiState.value.customSymptomName.trim()
        if (name.isBlank()) return
        
        val symptom = Symptom(
            id = UUID.randomUUID().toString(),
            name = name,
            severity = _uiState.value.customSymptomSeverity
        )
        
        _uiState.update { state ->
            state.copy(
                selectedSymptoms = state.selectedSymptoms + symptom,
                customSymptomName = "",
                customSymptomSeverity = SymptomSeverity.MILD
            )
        }
    }
    
    /**
     * Update custom symptom name.
     */
    fun updateCustomSymptomName(name: String) {
        _uiState.update { it.copy(customSymptomName = name) }
    }
    
    /**
     * Update custom symptom severity.
     */
    fun updateCustomSymptomSeverity(severity: SymptomSeverity) {
        _uiState.update { it.copy(customSymptomSeverity = severity) }
    }
    
    /**
     * Remove a symptom from the selection.
     */
    fun removeSymptom(symptom: Symptom) {
        _uiState.update { state ->
            state.copy(selectedSymptoms = state.selectedSymptoms.filter { it.id != symptom.id })
        }
    }
    
    /**
     * Perform triage analysis on selected symptoms.
     */
    fun performTriage() {
        val symptoms = _uiState.value.selectedSymptoms
        if (symptoms.isEmpty()) {
            _uiState.update { it.copy(error = "Please select at least one symptom") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            when (val result = triageUseCase.performTriage(symptoms)) {
                is Result.Success -> {
                    _uiState.update { it.copy(
                        triageResult = result.data,
                        currentStep = TriageStep.RESULTS,
                        isLoading = false
                    )}
                }
                is Result.Error -> {
                    _uiState.update { it.copy(
                        error = result.exception.message ?: "Failed to perform triage",
                        isLoading = false
                    )}
                }
            }
        }
    }
    
    /**
     * Set user location for clinic search.
     */
    fun setUserLocation(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(
            userLocation = UserLocation(latitude, longitude),
            locationPermissionGranted = true
        )}
    }
    
    /**
     * Set location permission status.
     */
    fun setLocationPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(locationPermissionGranted = granted) }
    }
    
    /**
     * Find nearby clinics for a selected specialty.
     */
    fun findClinicsForSpecialty(recommendation: SpecialistRecommendation) {
        val location = _uiState.value.userLocation
        if (location == null) {
            _uiState.update { it.copy(error = "Location not available. Please enable location services.") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true,
                error = null,
                selectedSpecialty = recommendation
            )}
            
            when (val result = triageUseCase.findNearbyClinics(location, recommendation.specialtyId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(
                        nearbyClinics = result.data,
                        currentStep = TriageStep.CLINIC_MAP,
                        isLoading = false
                    )}
                }
                is Result.Error -> {
                    _uiState.update { it.copy(
                        error = result.exception.message ?: "Failed to find clinics",
                        isLoading = false
                    )}
                }
            }
        }
    }
    
    /**
     * Find all nearby clinics without specialty filter.
     */
    fun findAllNearbyClinics() {
        val location = _uiState.value.userLocation
        if (location == null) {
            _uiState.update { it.copy(error = "Location not available. Please enable location services.") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, selectedSpecialty = null) }
            
            when (val result = triageUseCase.findNearbyClinics(location, null)) {
                is Result.Success -> {
                    _uiState.update { it.copy(
                        nearbyClinics = result.data,
                        currentStep = TriageStep.CLINIC_MAP,
                        isLoading = false
                    )}
                }
                is Result.Error -> {
                    _uiState.update { it.copy(
                        error = result.exception.message ?: "Failed to find clinics",
                        isLoading = false
                    )}
                }
            }
        }
    }
    
    /**
     * Navigate back to previous step.
     */
    fun navigateBack() {
        _uiState.update { state ->
            val previousStep = when (state.currentStep) {
                TriageStep.DISCLAIMER -> TriageStep.DISCLAIMER
                TriageStep.SYMPTOM_SELECTION -> TriageStep.DISCLAIMER
                TriageStep.RESULTS -> TriageStep.SYMPTOM_SELECTION
                TriageStep.CLINIC_MAP -> TriageStep.RESULTS
            }
            state.copy(currentStep = previousStep, error = null)
        }
    }
    
    /**
     * Reset the triage flow.
     */
    fun resetTriage() {
        _uiState.update { TriageUiState(
            locationPermissionGranted = it.locationPermissionGranted,
            userLocation = it.userLocation
        )}
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Get the medical disclaimer text.
     */
    fun getDisclaimer(): String = MEDICAL_DISCLAIMER
}
