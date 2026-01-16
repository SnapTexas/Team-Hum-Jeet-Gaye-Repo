package com.healthtracker.presentation.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.*

/**
 * Main screen for Health Circles social features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    viewModel: SocialViewModel = hiltViewModel(),
    onNavigateToCircleDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SocialEvent.CircleCreated -> {
                    viewModel.hideCreateCircleDialog()
                }
                is SocialEvent.CircleJoined -> {
                    viewModel.hideJoinCircleDialog()
                }
                is SocialEvent.Error -> {
                    // Show snackbar or toast
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Circles") },
                actions = {
                    IconButton(onClick = { viewModel.showPrivacySettingsDialog() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Privacy Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = { viewModel.showJoinCircleDialog() },
                    modifier = Modifier.padding(bottom = 8.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Join Circle")
                }
                FloatingActionButton(
                    onClick = { viewModel.showCreateCircleDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Circle")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.circles.isEmpty()) {
                EmptyCirclesView(
                    onCreateClick = { viewModel.showCreateCircleDialog() },
                    onJoinClick = { viewModel.showJoinCircleDialog() }
                )
            } else {
                CirclesList(
                    circles = uiState.circles,
                    onCircleClick = onNavigateToCircleDetail
                )
            }
        }
    }

    // Dialogs
    if (uiState.showCreateCircleDialog) {
        CreateCircleDialog(
            onDismiss = { viewModel.hideCreateCircleDialog() },
            onCreate = { name, type, description ->
                viewModel.createCircle(name, type, description)
            }
        )
    }

    if (uiState.showJoinCircleDialog) {
        JoinCircleDialog(
            onDismiss = { viewModel.hideJoinCircleDialog() },
            onJoin = { code -> viewModel.joinCircle(code) }
        )
    }

    if (uiState.showPrivacySettingsDialog) {
        PrivacySettingsDialog(
            settings = uiState.privacySettings,
            onDismiss = { viewModel.hidePrivacySettingsDialog() },
            onSave = { metrics -> viewModel.updateDefaultSharedMetrics(metrics) }
        )
    }
}

@Composable
private fun EmptyCirclesView(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Health Circles Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a circle to challenge friends and family, or join an existing one!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onJoinClick) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Join Circle")
            }
            Button(onClick = onCreateClick) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Circle")
            }
        }
    }
}

@Composable
private fun CirclesList(
    circles: List<HealthCircle>,
    onCircleClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(circles) { circle ->
            CircleCard(circle = circle, onClick = { onCircleClick(circle.id) })
        }
    }
}

@Composable
private fun CircleCard(
    circle: HealthCircle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getCircleTypeColor(circle.type).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCircleTypeIcon(circle.type),
                    contentDescription = null,
                    tint = getCircleTypeColor(circle.type)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = circle.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${circle.memberCount} members • ${circle.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getCircleTypeIcon(type: CircleType) = when (type) {
    CircleType.FRIENDS -> Icons.Default.People
    CircleType.CORPORATE -> Icons.Default.Business
    CircleType.FAMILY -> Icons.Default.FamilyRestroom
}

private fun getCircleTypeColor(type: CircleType) = when (type) {
    CircleType.FRIENDS -> Color(0xFF4CAF50)
    CircleType.CORPORATE -> Color(0xFF2196F3)
    CircleType.FAMILY -> Color(0xFFE91E63)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCircleDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: CircleType, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CircleType.FRIENDS) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Health Circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Circle Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Circle Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        CircleType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedType, description.ifBlank { null }) },
                enabled = name.isNotBlank()
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
}

@Composable
private fun JoinCircleDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Health Circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the 8-character join code shared by the circle owner.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 8) code = it.uppercase() },
                    label = { Text("Join Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.length == 8
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PrivacySettingsDialog(
    settings: CirclePrivacySettings?,
    onDismiss: () -> Unit,
    onSave: (Set<MetricType>) -> Unit
) {
    val shareableMetrics = CirclePrivacySettings.SHAREABLE_METRICS.toList()
    var selectedMetrics by remember(settings) {
        mutableStateOf(settings?.defaultSharedMetrics ?: CirclePrivacySettings.DEFAULT_SHARED_METRICS)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose which metrics to share with circles by default:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                shareableMetrics.forEach { metric ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = metric in selectedMetrics,
                            onCheckedChange = { checked ->
                                selectedMetrics = if (checked) {
                                    selectedMetrics + metric
                                } else {
                                    selectedMetrics - metric
                                }
                            }
                        )
                        Text(
                            text = metric.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedMetrics) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
