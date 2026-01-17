package com.healthtracker.presentation.medical

import android.content.ContentResolver
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.RepeatType
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Dialog for uploading a medical record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadRecordDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onUpload: (
        title: String,
        description: String?,
        type: RecordType,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long
    ) -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    // Get file info
    val fileInfo = remember(uri) {
        getFileInfo(contentResolver, uri)
    }
    
    var title by remember { mutableStateOf(fileInfo.name.substringBeforeLast('.')) }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RecordType.OTHER) }
    var typeExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Medical Record") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // File info
                Text(
                    text = "File: ${fileInfo.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Size: ${formatFileSize(fileInfo.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Record type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Record Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        RecordType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        onUpload(
                            title,
                            description.ifBlank { null },
                            selectedType,
                            inputStream,
                            fileInfo.name,
                            fileInfo.mimeType,
                            fileInfo.size
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for creating a health reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onCreate: (
        type: MedicalReminderType,
        title: String,
        description: String?,
        times: List<LocalTime>,
        startDate: LocalDate,
        endDate: LocalDate?,
        repeatType: RepeatType,
        daysOfWeek: Set<DayOfWeek>?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MedicalReminderType.MEDICINE) }
    var typeExpanded by remember { mutableStateOf(false) }
    var selectedRepeatType by remember { mutableStateOf(RepeatType.DAILY) }
    var repeatExpanded by remember { mutableStateOf(false) }
    var selectedRingtoneUri by remember { 
        mutableStateOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ) 
    }
    var ringtoneName by remember { mutableStateOf("Default Alarm") }
    
    val times = remember { mutableStateListOf(LocalTime.of(9, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableStateOf(-1) }
    
    val timePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0
    )
    
    // Ringtone picker launcher
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            selectedRingtoneUri = uri
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtoneName = ringtone?.getTitle(context) ?: "Selected Ringtone"
        }
    }
    
    // Get initial ringtone name
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val ringtone = RingtoneManager.getRingtone(context, selectedRingtoneUri)
            ringtoneName = ringtone?.getTitle(context) ?: "Default Alarm"
        } catch (e: Exception) {
            ringtoneName = "Default Alarm"
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Reminder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Reminder type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reminder Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        MedicalReminderType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    placeholder = { 
                        Text(
                            when (selectedType) {
                                MedicalReminderType.MEDICINE -> "e.g., Take Vitamin D"
                                MedicalReminderType.VACCINATION -> "e.g., Flu Shot"
                                MedicalReminderType.APPOINTMENT -> "e.g., Dr. Smith Checkup"
                                else -> "Enter reminder title"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Repeat type dropdown
                ExposedDropdownMenuBox(
                    expanded = repeatExpanded,
                    onExpandedChange = { repeatExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRepeatType.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = repeatExpanded,
                        onDismissRequest = { repeatExpanded = false }
                    ) {
                        RepeatType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedRepeatType = type
                                    repeatExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Times
                Text(
                    text = "Reminder Times",
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                times.forEachIndexed { index, time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                editingTimeIndex = index
                                showTimePicker = true
                            }
                        ) {
                            Text(time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")))
                        }
                        
                        if (times.size > 1) {
                            TextButton(
                                onClick = { times.removeAt(index) }
                            ) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                
                if (times.size < 5) {
                    TextButton(
                        onClick = {
                            editingTimeIndex = -1
                            showTimePicker = true
                        }
                    ) {
                        Text("+ Add Time")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Ringtone Picker
                Text(
                    text = "Alarm Sound",
                    style = MaterialTheme.typography.labelMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            }
                            ringtonePicker.launch(intent)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ringtoneName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Tap to change",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Save ringtone preference
                    val prefs = context.getSharedPreferences("reminder_settings", android.content.Context.MODE_PRIVATE)
                    prefs.edit().putString("ringtone_uri", selectedRingtoneUri.toString()).apply()
                    
                    onCreate(
                        selectedType,
                        title,
                        description.ifBlank { null },
                        times.toList(),
                        LocalDate.now(),
                        null,
                        selectedRepeatType,
                        null
                    )
                },
                enabled = title.isNotBlank() && times.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        if (editingTimeIndex >= 0) {
                            times[editingTimeIndex] = newTime
                        } else {
                            times.add(newTime)
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper data class for file info
private data class FileInfo(
    val name: String,
    val size: Long,
    val mimeType: String
)

private fun getFileInfo(contentResolver: ContentResolver, uri: Uri): FileInfo {
    var name = "Unknown"
    var size = 0L
    
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: "Unknown"
            }
            if (sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
    }
    
    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
    
    return FileInfo(name, size, mimeType)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

// Extension functions
private fun RecordType.displayName(): String = when (this) {
    RecordType.LAB_REPORT -> "Lab Report"
    RecordType.PRESCRIPTION -> "Prescription"
    RecordType.IMAGING -> "Imaging"
    RecordType.VACCINATION -> "Vaccination"
    RecordType.DISCHARGE_SUMMARY -> "Discharge Summary"
    RecordType.INSURANCE -> "Insurance"
    RecordType.OTHER -> "Other"
}

private fun MedicalReminderType.displayName(): String = when (this) {
    MedicalReminderType.MEDICINE -> "Medicine"
    MedicalReminderType.VACCINATION -> "Vaccination"
    MedicalReminderType.APPOINTMENT -> "Appointment"
    MedicalReminderType.CHECKUP -> "Checkup"
    MedicalReminderType.CUSTOM -> "Custom"
}

private fun RepeatType.displayName(): String = when (this) {
    RepeatType.ONCE -> "Once"
    RepeatType.DAILY -> "Daily"
    RepeatType.WEEKLY -> "Weekly"
    RepeatType.MONTHLY -> "Monthly"
    RepeatType.CUSTOM -> "Custom"
}
