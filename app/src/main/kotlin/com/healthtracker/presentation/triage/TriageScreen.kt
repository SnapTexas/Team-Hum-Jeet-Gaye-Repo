package com.healthtracker.presentation.triage

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    viewModel: TriageViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            viewModel.setLocationPermissionGranted(true)
            // Get current location
            getCurrentLocation(context) { lat, lng ->
                viewModel.setUserLocation(lat, lng)
            }
        } else {
            viewModel.setLocationPermissionGranted(false)
        }
    }
    
    // Request location permission when entering results screen
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == TriageStep.RESULTS && !uiState.locationPermissionGranted) {
            locationPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Issue Detection") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == TriageStep.DISCLAIMER) {
                            onNavigateBack()
                        } else {
                            viewModel.navigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState.currentStep) {
                TriageStep.DISCLAIMER -> DisclaimerScreen(
                    disclaimer = viewModel.getDisclaimer(),
                    onAccept = { viewModel.acceptDisclaimer() }
                )
                TriageStep.SYMPTOM_SELECTION -> SymptomSelectionScreen(
                    uiState = uiState,
                    onAddSymptom = viewModel::addSymptom,
                    onRemoveSymptom = viewModel::removeSymptom,
                    onCustomSymptomNameChange = viewModel::updateCustomSymptomName,
                    onCustomSymptomSeverityChange = viewModel::updateCustomSymptomSeverity,
                    onAddCustomSymptom = viewModel::addCustomSymptom,
                    onPerformTriage = viewModel::performTriage
                )
                TriageStep.RESULTS -> TriageResultsScreen(
                    uiState = uiState,
                    onFindClinics = viewModel::findClinicsForSpecialty,
                    onFindAllClinics = viewModel::findAllNearbyClinics,
                    onReset = viewModel::resetTriage,
                    onRequestLocation = {
                        locationPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                )
                TriageStep.CLINIC_MAP -> ClinicMapScreen(
                    uiState = uiState,
                    onBack = viewModel::navigateBack
                )
            }
            
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun DisclaimerScreen(
    disclaimer: String,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Medical Disclaimer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = disclaimer,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I Understand and Accept")
        }
    }
}

@Composable
private fun SymptomSelectionScreen(
    uiState: TriageUiState,
    onAddSymptom: (SymptomTemplate, SymptomSeverity) -> Unit,
    onRemoveSymptom: (Symptom) -> Unit,
    onCustomSymptomNameChange: (String) -> Unit,
    onCustomSymptomSeverityChange: (SymptomSeverity) -> Unit,
    onAddCustomSymptom: () -> Unit,
    onPerformTriage: () -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<SymptomTemplate?>(null) }
    var selectedSeverity by remember { mutableStateOf(SymptomSeverity.MILD) }
    var showCustomInput by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Your Symptoms",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Choose from common symptoms or add your own",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Selected symptoms
        if (uiState.selectedSymptoms.isNotEmpty()) {
            Text(
                text = "Selected Symptoms:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            uiState.selectedSymptoms.forEach { symptom ->
                SelectedSymptomChip(
                    symptom = symptom,
                    onRemove = { onRemoveSymptom(symptom) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Common symptoms grid
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Common Symptoms:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            items(uiState.availableSymptoms) { template ->
                SymptomCard(
                    template = template,
                    isSelected = selectedTemplate == template,
                    onClick = { selectedTemplate = template }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCustomInput = !showCustomInput },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Symptom")
                }
            }
            
            if (showCustomInput) {
                item {
                    CustomSymptomInput(
                        name = uiState.customSymptomName,
                        severity = uiState.customSymptomSeverity,
                        onNameChange = onCustomSymptomNameChange,
                        onSeverityChange = onCustomSymptomSeverityChange,
                        onAdd = {
                            onAddCustomSymptom()
                            showCustomInput = false
                        }
                    )
                }
            }
        }
        
        // Severity selector for selected template
        if (selectedTemplate != null) {
            SeveritySelector(
                selectedSeverity = selectedSeverity,
                onSeveritySelected = { selectedSeverity = it },
                onConfirm = {
                    selectedTemplate?.let { template ->
                        onAddSymptom(template, selectedSeverity)
                        selectedTemplate = null
                        selectedSeverity = SymptomSeverity.MILD
                    }
                },
                onCancel = { selectedTemplate = null }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onPerformTriage,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedSymptoms.isNotEmpty()
        ) {
            Text("Analyze Symptoms")
        }
    }
}

@Composable
private fun SelectedSymptomChip(
    symptom: Symptom,
    onRemove: () -> Unit
) {
    val severityColor = when (symptom.severity) {
        SymptomSeverity.MILD -> Color(0xFF4CAF50)
        SymptomSeverity.MODERATE -> Color(0xFFFF9800)
        SymptomSeverity.SEVERE -> Color(0xFFF44336)
    }
    
    AssistChip(
        onClick = onRemove,
        label = { Text("${symptom.name} (${symptom.severity.name.lowercase()})") },
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = severityColor.copy(alpha = 0.2f)
        ),
        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SymptomCard(
    template: SymptomTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                template.bodyPart?.let { bodyPart ->
                    Text(
                        text = "Area: $bodyPart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SeveritySelector(
    selectedSeverity: SymptomSeverity,
    onSeveritySelected: (SymptomSeverity) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select Severity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SymptomSeverity.values().forEach { severity ->
                    FilterChip(
                        selected = selectedSeverity == severity,
                        onClick = { onSeveritySelected(severity) },
                        label = { Text(severity.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (severity) {
                                SymptomSeverity.MILD -> Color(0xFF4CAF50)
                                SymptomSeverity.MODERATE -> Color(0xFFFF9800)
                                SymptomSeverity.SEVERE -> Color(0xFFF44336)
                            }
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onConfirm) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun CustomSymptomInput(
    name: String,
    severity: SymptomSeverity,
    onNameChange: (String) -> Unit,
    onSeverityChange: (SymptomSeverity) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Symptom Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Severity",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SymptomSeverity.values().forEach { sev ->
                    FilterChip(
                        selected = severity == sev,
                        onClick = { onSeverityChange(sev) },
                        label = { Text(sev.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text("Add Custom Symptom")
            }
        }
    }
}

@Composable
private fun TriageResultsScreen(
    uiState: TriageUiState,
    onFindClinics: (SpecialistRecommendation) -> Unit,
    onFindAllClinics: () -> Unit,
    onReset: () -> Unit,
    onRequestLocation: () -> Unit = {}
) {
    val result = uiState.triageResult ?: return
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Urgency indicator
        UrgencyCard(urgencyLevel = result.urgencyLevel)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Symptoms summary
        Text(
            text = "Reported Symptoms",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        result.symptoms.forEach { symptom ->
            Text(
                text = "• ${symptom.name} (${symptom.severity.name.lowercase()})",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Specialist recommendations
        Text(
            text = "Recommended Specialists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (result.recommendations.isEmpty()) {
            Text(
                text = "No specific specialist recommendations. Consider consulting a general practitioner.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            result.recommendations.forEach { recommendation ->
                SpecialistCard(
                    recommendation = recommendation,
                    onFindClinics = { 
                        if (uiState.userLocation != null) {
                            onFindClinics(recommendation)
                        } else {
                            onRequestLocation()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Location status card
        if (uiState.userLocation == null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📍 Enable Location",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Allow location access to find nearby clinics",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Location")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        OutlinedButton(
            onClick = {
                if (uiState.userLocation != null) {
                    onFindAllClinics()
                } else {
                    onRequestLocation()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Find All Nearby Clinics")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start New Assessment")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Disclaimer reminder
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "Remember: This is not a medical diagnosis. Please consult a healthcare professional for proper evaluation.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun UrgencyCard(urgencyLevel: UrgencyLevel) {
    val (color, icon, title, description) = when (urgencyLevel) {
        UrgencyLevel.LOW -> Quadruple(
            Color(0xFF4CAF50),
            Icons.Default.CheckCircle,
            "Low Urgency",
            "You can schedule a regular appointment"
        )
        UrgencyLevel.MODERATE -> Quadruple(
            Color(0xFFFF9800),
            Icons.Default.Info,
            "Moderate Urgency",
            "Consider seeing a doctor within a few days"
        )
        UrgencyLevel.HIGH -> Quadruple(
            Color(0xFFF44336),
            Icons.Default.Warning,
            "High Urgency",
            "You should see a doctor today"
        )
        UrgencyLevel.EMERGENCY -> Quadruple(
            Color(0xFFB71C1C),
            Icons.Default.Error,
            "EMERGENCY",
            "Seek emergency care immediately or call emergency services"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun SpecialistCard(
    recommendation: SpecialistRecommendation,
    onFindClinics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recommendation.specialtyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                RelevanceIndicator(score = recommendation.relevanceScore)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = recommendation.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = onFindClinics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find Nearby ${recommendation.specialtyName}s")
            }
        }
    }
}

@Composable
private fun RelevanceIndicator(score: Float) {
    val percentage = (score * 100).toInt()
    val color = when {
        score >= 0.8f -> Color(0xFF4CAF50)
        score >= 0.6f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = "$percentage% match",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ClinicMapScreen(
    uiState: TriageUiState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Nearby Clinics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                uiState.selectedSpecialty?.let { specialty ->
                    Text(
                        text = "Showing ${specialty.specialtyName}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Map placeholder - In production, integrate Google Maps SDK
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Map View",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Google Maps integration required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "${uiState.nearbyClinics.size} clinics found",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Clinic list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.nearbyClinics) { clinic ->
                ClinicCard(clinic = clinic)
            }
            
            if (uiState.nearbyClinics.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Clinics Found Nearby",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Try increasing the search radius or check your internet connection.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ClinicCard(clinic: Clinic) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clinic.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = clinic.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Distance badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = formatDistance(clinic.distanceMeters),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                clinic.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFFFB300)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // Open status
                clinic.openNow?.let { isOpen ->
                    Text(
                        text = if (isOpen) "Open Now" else "Closed",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOpen) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
            
            // Phone number
            clinic.phoneNumber?.let { phone ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Specialties
            if (clinic.specialties.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    clinic.specialties.take(3).forEach { specialty ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = specialty.replace("_", " ").replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (clinic.specialties.size > 3) {
                        Text(
                            text = "+${clinic.specialties.size - 3} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return when {
        meters < 1000 -> "${meters.toInt()}m"
        else -> String.format("%.1f km", meters / 1000)
    }
}

/**
 * Gets current location using Android LocationManager.
 */
@SuppressLint("MissingPermission")
private fun getCurrentLocation(context: Context, onLocationReceived: (Double, Double) -> Unit) {
    try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        // Try GPS first, then Network
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        
        if (location != null) {
            onLocationReceived(location.latitude, location.longitude)
        } else {
            // Use default location (Delhi) if no location available
            onLocationReceived(28.6139, 77.2090)
        }
    } catch (e: Exception) {
        // Use default location on error
        onLocationReceived(28.6139, 77.2090)
    }
}
