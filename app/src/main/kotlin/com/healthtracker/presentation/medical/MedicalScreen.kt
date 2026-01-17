package com.healthtracker.presentation.medical

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalRecord
import com.healthtracker.domain.model.RecordType
import com.healthtracker.domain.model.MedicalReminderType
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var showImageViewer by remember { mutableStateOf(false) }
    var viewingRecord by remember { mutableStateOf<MedicalRecord?>(null) }
    var imageContent by remember { mutableStateOf<ByteArray?>(null) }
    
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
                is MedicalEvent.RecordContentLoaded -> {
                    imageContent = event.content
                    showImageViewer = true
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
                },
                modifier = Modifier.padding(bottom = 80.dp) // Avoid avatar overlay
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
                    onRecordClick = { record ->
                        viewingRecord = record
                        viewModel.viewRecordContent(record.id)
                    },
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
                showReminderDialog = false // Close dialog immediately after clicking Create
            }
        )
    }
    
    // Image/Document Viewer Dialog
    if (showImageViewer && viewingRecord != null) {
        DocumentViewerDialog(
            record = viewingRecord!!,
            content = imageContent,
            isLoading = uiState.isLoadingContent,
            onDismiss = {
                showImageViewer = false
                viewingRecord = null
                imageContent = null
            }
        )
    }
}

@Composable
private fun DocumentViewerDialog(
    record: MedicalRecord,
    content: ByteArray?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Title
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .padding(end = 48.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                content != null -> {
                    val mimeType = record.mimeType.lowercase()
                    val fileName = record.fileName.lowercase()
                    
                    when {
                        // Images
                        mimeType.startsWith("image/") || 
                        fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".webp") || fileName.endsWith(".bmp") -> {
                            ImageViewer(
                                content = content,
                                title = record.title,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                onTransform = { newScale, newOffsetX, newOffsetY ->
                                    scale = newScale
                                    offsetX = newOffsetX
                                    offsetY = newOffsetY
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 60.dp, bottom = 80.dp)
                            )
                        }
                        
                        // Text files
                        mimeType.startsWith("text/") ||
                        mimeType == "application/json" ||
                        mimeType == "application/xml" ||
                        fileName.endsWith(".txt") || fileName.endsWith(".json") ||
                        fileName.endsWith(".xml") || fileName.endsWith(".csv") ||
                        fileName.endsWith(".log") || fileName.endsWith(".md") ||
                        fileName.endsWith(".html") || fileName.endsWith(".htm") -> {
                            TextFileViewer(
                                content = content,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 60.dp, bottom = 80.dp)
                            )
                        }
                        
                        // PDF files
                        mimeType == "application/pdf" || fileName.endsWith(".pdf") -> {
                            PdfViewer(
                                content = content,
                                fileName = record.fileName,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 60.dp, bottom = 80.dp)
                            )
                        }
                        
                        // Word documents
                        mimeType == "application/msword" ||
                        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                        fileName.endsWith(".doc") || fileName.endsWith(".docx") -> {
                            DocumentInfoViewer(
                                icon = Icons.Default.Description,
                                fileName = record.fileName,
                                fileType = "Word Document",
                                fileSize = record.fileSizeBytes,
                                message = "Word documents are securely stored.\nTap to export and open with external app.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        
                        // Excel files
                        mimeType == "application/vnd.ms-excel" ||
                        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                        fileName.endsWith(".xls") || fileName.endsWith(".xlsx") -> {
                            DocumentInfoViewer(
                                icon = Icons.Default.Description,
                                fileName = record.fileName,
                                fileType = "Excel Spreadsheet",
                                fileSize = record.fileSizeBytes,
                                message = "Spreadsheets are securely stored.\nTap to export and open with external app.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        
                        // Other files - show hex preview
                        else -> {
                            OtherFileViewer(
                                content = content,
                                fileName = record.fileName,
                                mimeType = record.mimeType,
                                fileSize = record.fileSizeBytes,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 60.dp, bottom = 80.dp)
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Unable to load content",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            // File info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${record.type.displayName()} • ${formatFileSize(record.fileSizeBytes)} • ${record.mimeType}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Uploaded: ${record.uploadedAt.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))}",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(
    content: ByteArray,
    title: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(content) {
        BitmapFactory.decodeByteArray(content, 0, content.size)
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = title,
            modifier = modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        onTransform(newScale, offsetX + pan.x, offsetY + pan.y)
                    }
                },
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Unable to decode image",
                color = Color.White
            )
        }
    }
}

@Composable
private fun TextFileViewer(
    content: ByteArray,
    modifier: Modifier = Modifier
) {
    val textContent = remember(content) {
        try {
            String(content, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(content, Charsets.ISO_8859_1)
            } catch (e2: Exception) {
                "Unable to decode text content"
            }
        }
    }
    
    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = textContent,
                        color = Color(0xFFD4D4D4),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewer(
    content: ByteArray,
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pdfBitmaps by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(content) {
        isLoading = true
        try {
            val bitmaps = mutableListOf<android.graphics.Bitmap>()
            val fileDescriptor = android.os.ParcelFileDescriptor.open(
                java.io.File.createTempFile("temp_pdf", ".pdf", context.cacheDir).apply {
                    writeBytes(content)
                },
                android.os.ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            val pdfRenderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount.coerceAtMost(10) // Limit to 10 pages
            
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val bitmap = android.graphics.Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
            
            pdfRenderer.close()
            fileDescriptor.close()
            pdfBitmaps = bitmaps
            
            if (pdfRenderer.pageCount > 10) {
                errorMessage = "Showing first 10 of ${pdfRenderer.pageCount} pages"
            }
        } catch (e: Exception) {
            errorMessage = "Unable to render PDF: ${e.message}"
        }
        isLoading = false
    }
    
    Box(modifier = modifier) {
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading PDF...", color = Color.White)
                }
            }
            pdfBitmaps.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (errorMessage != null) {
                        item {
                            Text(
                                text = errorMessage!!,
                                color = Color.Yellow,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    items(pdfBitmaps.size) { index ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column {
                                Image(
                                    bitmap = pdfBitmaps[index].asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.FillWidth
                                )
                                Text(
                                    text = "Page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF333333))
                                        .padding(8.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Unable to load PDF",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentInfoViewer(
    icon: ImageVector,
    fileName: String,
    fileType: String,
    fileSize: Long,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = fileName,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$fileType • ${formatFileSize(fileSize)}",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OtherFileViewer(
    content: ByteArray,
    fileName: String,
    mimeType: String,
    fileSize: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = fileName,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type: $mimeType\nSize: ${formatFileSize(fileSize)}",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Show hex preview for binary files
        Text(
            text = "File Preview (Hex)",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(8.dp)
        ) {
            val hexPreview = remember(content) {
                content.take(256).joinToString(" ") { byte ->
                    String.format("%02X", byte)
                }.chunked(48).joinToString("\n")
            }
            
            Text(
                text = hexPreview + if (content.size > 256) "\n..." else "",
                color = Color(0xFF9CDCFE),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "File is securely stored and encrypted",
            color = Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
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
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var customShortcuts by remember { mutableStateOf(listOf<RecordType>()) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips - Only "All" by default, user can add more
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // All filter (always shown)
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { onTypeSelected(null) },
                    label = { Text("All") }
                )
            }
            
            // Custom shortcuts added by user
            items(customShortcuts) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.displayName()) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { customShortcuts = customShortcuts - type }
                        )
                    }
                )
            }
            
            // Add shortcut button
            item {
                FilterChip(
                    selected = false,
                    onClick = { showAddShortcutDialog = true },
                    label = { Text("+ Add") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
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
    
    // Add shortcut dialog
    if (showAddShortcutDialog) {
        AlertDialog(
            onDismissRequest = { showAddShortcutDialog = false },
            title = { Text("Add Shortcut") },
            text = {
                Column {
                    Text("Select record type to add as shortcut:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    RecordType.entries.filter { it !in customShortcuts }.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    customShortcuts = customShortcuts + type
                                    showAddShortcutDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(type.icon(), contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(type.displayName())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddShortcutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
    val context = LocalContext.current
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Test Alarm Button at top
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Test the alarm
                            val notificationService = com.healthtracker.service.notification.MedicalReminderNotificationService(context)
                            notificationService.testAlarm()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🔔 Test Alarm Sound",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            if (reminders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Reminders",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap + to create your first health reminder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
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
