package com.healthtracker.presentation.medical

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.MedicalRecordsSummary
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.ReminderSchedule
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.RemindersSummary
import com.healthtracker.domain.model.RepeatType
import com.healthtracker.domain.model.Result
import com.healthtracker.domain.usecase.FileValidationResult
import com.healthtracker.domain.usecase.MedicalUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for medical records and reminders screens.
 */
@HiltViewModel
class MedicalViewModel @Inject constructor(
    private val medicalUseCase: MedicalUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MedicalUiState())
    val uiState: StateFlow<MedicalUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<MedicalEvent>()
    val events: SharedFlow<MedicalEvent> = _events.asSharedFlow()
    
    init {
        loadMedicalRecords()
        loadReminders()
        loadSummaries()
    }
    
    // ==================== Medical Records ====================
    
    private fun loadMedicalRecords() {
        viewModelScope.launch {
            medicalUseCase.getMedicalRecords()
                .catch { e ->
                    Timber.e(e, "Failed to load medical records")
                    _events.emit(MedicalEvent.Error("Failed to load medical records"))
                }
                .collect { records ->
                    _uiState.update { it.copy(records = records, isLoadingRecords = false) }
                }
        }
    }
    
    fun loadRecordsByType(type: RecordType?) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedRecordType = type, isLoadingRecords = true) }
            
            val flow = if (type != null) {
                medicalUseCase.getMedicalRecordsByType(type)
            } else {
                medicalUseCase.getMedicalRecords()
            }
            
            flow.catch { e ->
                Timber.e(e, "Failed to load records by type")
                _uiState.update { it.copy(isLoadingRecords = false) }
            }.collect { records ->
                _uiState.update { it.copy(records = records, isLoadingRecords = false) }
            }
        }
    }
    
    fun uploadRecord(
        title: String,
        description: String?,
        type: RecordType,
        fileInputStream: InputStream,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long
    ) {
        viewModelScope.launch {
            // Validate file first
            val validation = medicalUseCase.validateFileForUpload(fileName, fileSizeBytes, mimeType)
            if (validation is FileValidationResult.Invalid) {
                _events.emit(MedicalEvent.Error(validation.reason))
                return@launch
            }
            
            _uiState.update { it.copy(isUploading = true) }
            
            when (val result = medicalUseCase.uploadMedicalRecord(
                title = title,
                description = description,
                type = type,
                fileInputStream = fileInputStream,
                fileName = fileName,
                mimeType = mimeType
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isUploading = false) }
                    _events.emit(MedicalEvent.RecordUploaded(result.data))
                    loadSummaries()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isUploading = false) }
                    _events.emit(MedicalEvent.Error("Failed to upload record: ${result.exception.message}"))
                }
            }
        }
    }
    
    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingRecord = true) }
            
            when (val result = medicalUseCase.deleteMedicalRecord(recordId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeletingRecord = false) }
                    _events.emit(MedicalEvent.RecordDeleted)
                    loadSummaries()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isDeletingRecord = false) }
                    _events.emit(MedicalEvent.Error("Failed to delete record"))
                }
            }
        }
    }
    
    fun viewRecordContent(recordId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true) }
            
            when (val result = medicalUseCase.getRecordContent(recordId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoadingContent = false) }
                    _events.emit(MedicalEvent.RecordContentLoaded(recordId, result.data))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoadingContent = false) }
                    _events.emit(MedicalEvent.Error("Failed to load record content"))
                }
            }
        }
    }
    
    // ==================== Reminders ====================
    
    private fun loadReminders() {
        viewModelScope.launch {
            medicalUseCase.getReminders()
                .catch { e ->
                    Timber.e(e, "Failed to load reminders")
                    _events.emit(MedicalEvent.Error("Failed to load reminders"))
                }
                .collect { reminders ->
                    _uiState.update { it.copy(reminders = reminders, isLoadingReminders = false) }
                }
        }
    }
    
    fun createReminder(
        type: MedicalReminderType,
        title: String,
        description: String?,
        times: List<LocalTime>,
        startDate: LocalDate,
        endDate: LocalDate?,
        repeatType: RepeatType,
        daysOfWeek: Set<java.time.DayOfWeek>?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingReminder = true) }
            
            val schedule = ReminderSchedule(
                times = times,
                daysOfWeek = daysOfWeek,
                startDate = startDate,
                endDate = endDate,
                repeatType = repeatType
            )
            
            when (val result = medicalUseCase.createReminder(
                type = type,
                title = title,
                description = description,
                schedule = schedule
            )) {
                is Result.Success -> {
                    _uiState.update { it.copy(isCreatingReminder = false) }
                    _events.emit(MedicalEvent.ReminderCreated(result.data))
                    loadSummaries()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isCreatingReminder = false) }
                    _events.emit(MedicalEvent.Error("Failed to create reminder: ${result.exception.message}"))
                }
            }
        }
    }
    
    fun toggleReminderEnabled(reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            when (val result = medicalUseCase.setReminderEnabled(reminderId, enabled)) {
                is Result.Success -> {
                    _events.emit(MedicalEvent.ReminderToggled(reminderId, enabled))
                }
                is Result.Error -> {
                    _events.emit(MedicalEvent.Error("Failed to update reminder"))
                }
            }
        }
    }
    
    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingReminder = true) }
            
            when (val result = medicalUseCase.deleteReminder(reminderId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeletingReminder = false) }
                    _events.emit(MedicalEvent.ReminderDeleted)
                    loadSummaries()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isDeletingReminder = false) }
                    _events.emit(MedicalEvent.Error("Failed to delete reminder"))
                }
            }
        }
    }
    
    // ==================== Summaries ====================
    
    private fun loadSummaries() {
        viewModelScope.launch {
            // Load records summary
            when (val result = medicalUseCase.getMedicalRecordsSummary()) {
                is Result.Success -> {
                    _uiState.update { it.copy(recordsSummary = result.data) }
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Failed to load records summary")
                }
            }
            
            // Load reminders summary
            when (val result = medicalUseCase.getRemindersSummary()) {
                is Result.Success -> {
                    _uiState.update { it.copy(remindersSummary = result.data) }
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Failed to load reminders summary")
                }
            }
        }
    }
    
    fun setSelectedTab(tab: MedicalTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }
}

/**
 * UI state for medical screens.
 */
data class MedicalUiState(
    val selectedTab: MedicalTab = MedicalTab.RECORDS,
    
    // Records state
    val records: List<MedicalRecord> = emptyList(),
    val selectedRecordType: RecordType? = null,
    val isLoadingRecords: Boolean = true,
    val isUploading: Boolean = false,
    val isDeletingRecord: Boolean = false,
    val isLoadingContent: Boolean = false,
    val recordsSummary: MedicalRecordsSummary? = null,
    
    // Reminders state
    val reminders: List<HealthReminder> = emptyList(),
    val isLoadingReminders: Boolean = true,
    val isCreatingReminder: Boolean = false,
    val isDeletingReminder: Boolean = false,
    val remindersSummary: RemindersSummary? = null
)

/**
 * Tabs for medical screen.
 */
enum class MedicalTab {
    RECORDS,
    REMINDERS
}

/**
 * Events emitted by the ViewModel.
 */
sealed class MedicalEvent {
    data class Error(val message: String) : MedicalEvent()
    data class RecordUploaded(val record: MedicalRecord) : MedicalEvent()
    object RecordDeleted : MedicalEvent()
    data class RecordContentLoaded(val recordId: String, val content: ByteArray) : MedicalEvent()
    data class ReminderCreated(val reminder: HealthReminder) : MedicalEvent()
    data class ReminderToggled(val reminderId: String, val enabled: Boolean) : MedicalEvent()
    object ReminderDeleted : MedicalEvent()
}
