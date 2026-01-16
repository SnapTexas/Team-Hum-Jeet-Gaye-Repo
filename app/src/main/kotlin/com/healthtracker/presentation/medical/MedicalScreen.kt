package com.healthtracker.presentation.medical

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.MedicalReminderType
import java.time.format.DateTimeFormatter

/**
 * Main medical records and reminders screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalScreen(
    viewModel: MedicalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    var showUploadDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            showUploadDialog = true
        }
    }
    
    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MedicalEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is MedicalEvent.RecordUploaded -> {
                    snackbarHostState.showSnackbar("Record uploaded successfully")
                    showUploadDialog = false
                }
                is MedicalEvent.RecordDeleted -> {
                    snackbarHostState.showSnackbar("Record deleted")
                }
                is MedicalEvent.ReminderCreated -> {
                    snackbarHostState.showSnackbar("Reminder created")
                    showReminderDialog = false
                }
                is MedicalEvent.ReminderDeleted -> {
                    snackbarHostState.showSnackbar("Reminder deleted")
                }
                else -> {}
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medical Records & Reminders") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (uiState.selectedTab) {
                        MedicalTab.RECORDS -> filePickerLauncher.launch("*/*")
                        MedicalTab.REMINDERS -> showReminderDialog = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal
            ) {
                Tab(
                    selected = uiState.selectedTab == MedicalTab.RECORDS,
                    onClick = { viewModel.setSelectedTab(MedicalTab.RECORDS) },
                    text = { Text("Records") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == MedicalTab.REMINDERS,
                    onClick = { viewModel.setSelectedTab(MedicalTab.REMINDERS) },
                    text = { Text("Reminders") },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
            }
            
            // Content based on selected tab
            when (uiState.selectedTab) {
                MedicalTab.RECORDS -> RecordsContent(
                    records = uiState.records,
                    isLoading = uiState.isLoadingRecords,
                    selectedType = uiState.selectedRecordType,
                    onTypeSelected = { viewModel.loadRecordsByType(it) },
                    onRecordClick = { viewModel.viewRecordContent(it.id) },
                    onDeleteRecord = { viewModel.deleteRecord(it.id) }
                )
                MedicalTab.REMINDERS -> RemindersContent(
                    reminders = uiState.reminders,
                    isLoading = uiState.isLoadingReminders,
                    onToggleEnabled = { id, enabled -> viewModel.toggleReminderEnabled(id, enabled) },
                    onDeleteReminder = { viewModel.deleteReminder(it.id) }
                )
            }
        }
    }
    
    // Upload dialog
    if (showUploadDialog && selectedUri != null) {
        UploadRecordDialog(
            uri = selectedUri!!,
            onDismiss = { 
                showUploadDialog = false
                selectedUri = null
            },
            onUpload = { title, description, type, inputStream, fileName, mimeType, size ->
                viewModel.uploadRecord(title, description, type, inputStream, fileName, mimeType, size)
            }
        )
    }
    
    // Create reminder dialog
    if (showReminderDialog) {
        CreateReminderDialog(
            onDismiss = { showReminderDialog = false },
            onCreate = { type, title, description, times, startDate, endDate, repeatType, daysOfWeek ->
                viewModel.createReminder(type, title, description, times, startDate, endDate, repeatType, daysOfWeek)
            }
        )
    }
}

@Composable
private fun RecordsContent(
    records: List<MedicalRecord>,
    isLoading: Boolean,
    selectedType: RecordType?,
    onTypeSelected: (RecordType?) -> Unit,
    onRecordClick: (MedicalRecord) -> Unit,
    onDeleteRecord: (MedicalRecord) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { onTypeSelected(null) },
                    label = { Text("All") }
                )
            }
            items(RecordType.entries) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.displayName()) }
                )
            }
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (records.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Folder,
                title = "No Medical Records",
                message = "Tap + to upload your first medical record"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { onRecordClick(record) },
                        onDelete = { onDeleteRecord(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: MedicalRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on type
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = record.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = record.type.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = record.uploadedAt.atZone(java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Record?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RemindersContent(
    reminders: List<HealthReminder>,
    isLoading: Boolean,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDeleteReminder: (HealthReminder) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (reminders.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Notifications,
            title = "No Reminders",
            message = "Tap + to create your first health reminder"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(reminders, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    onToggleEnabled = { onToggleEnabled(reminder.id, it) },
                    onDelete = { onDeleteReminder(reminder) }
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: HealthReminder,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon based on type
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (reminder.enabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = reminder.type.icon(),
                    contentDescription = null,
                    tint = if (reminder.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = reminder.type.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show schedule times
                val timesText = reminder.schedule.times.joinToString(", ") {
                    it.format(DateTimeFormatter.ofPattern("h:mm a"))
                }
                Text(
                    text = timesText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Switch(
                checked = reminder.enabled,
                onCheckedChange = onToggleEnabled
            )
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Reminder?") },
            text = { Text("This will cancel all scheduled notifications.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// Extension functions for display
private fun RecordType.displayName(): String = when (this) {
    RecordType.LAB_REPORT -> "Lab Report"
    RecordType.PRESCRIPTION -> "Prescription"
    RecordType.IMAGING -> "Imaging"
    RecordType.VACCINATION -> "Vaccination"
    RecordType.DISCHARGE_SUMMARY -> "Discharge Summary"
    RecordType.INSURANCE -> "Insurance"
    RecordType.OTHER -> "Other"
}

private fun RecordType.icon(): ImageVector = when (this) {
    RecordType.LAB_REPORT -> Icons.Default.Description
    RecordType.PRESCRIPTION -> Icons.Default.MedicalServices
    RecordType.IMAGING -> Icons.Default.Image
    RecordType.VACCINATION -> Icons.Default.Vaccines
    RecordType.DISCHARGE_SUMMARY -> Icons.Default.Description
    RecordType.INSURANCE -> Icons.Default.Description
    RecordType.OTHER -> Icons.Default.Folder
}

private fun MedicalReminderType.displayName(): String = when (this) {
    MedicalReminderType.MEDICINE -> "Medicine"
    MedicalReminderType.VACCINATION -> "Vaccination"
    MedicalReminderType.APPOINTMENT -> "Appointment"
    MedicalReminderType.CHECKUP -> "Checkup"
    MedicalReminderType.CUSTOM -> "Custom"
}

private fun MedicalReminderType.icon(): ImageVector = when (this) {
    MedicalReminderType.MEDICINE -> Icons.Default.MedicalServices
    MedicalReminderType.VACCINATION -> Icons.Default.Vaccines
    MedicalReminderType.APPOINTMENT -> Icons.Default.Schedule
    MedicalReminderType.CHECKUP -> Icons.Default.MedicalServices
    MedicalReminderType.CUSTOM -> Icons.Default.Notifications
}
