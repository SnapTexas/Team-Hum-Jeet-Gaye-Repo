package com.healthtracker.presentation.diet

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.FoodAnalysisResult
import com.healthtracker.domain.model.LoggedMeal
import com.healthtracker.domain.model.LoggedMealType
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.GlassWhite
import com.healthtracker.presentation.theme.GlowPrimary
import com.healthtracker.presentation.theme.NeonPurple
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor


/**
 * Main diet tracking screen with camera viewfinder and meal logging.
 * 
 * Features:
 * - CameraX integration for food capture
 * - ML Kit classification with graceful fallback
 * - Premium glassmorphism UI
 * - Manual entry fallback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietTrackingScreen(
    onNavigateBack: () -> Unit,
    viewModel: DietTrackingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Camera permission
    var hasCameraPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.onShowCamera()
        }
    }
    
    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DietTrackingEvent.MealLogged -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("${event.meal.foodName} logged!")
                    }
                }
                is DietTrackingEvent.MealDeleted -> {
                    scope.launch {
                        snackbarHostState.showSnackbar("Meal deleted")
                    }
                }
                is DietTrackingEvent.ShowManualEntry -> {
                    viewModel.onShowManualEntry()
                }
                else -> {}
            }
        }
    }
    
    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onClearError()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DietTopBar(onNavigateBack = onNavigateBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0D1A),
                            Color(0xFF1A1A2E),
                            Color(0xFF16213E)
                        )
                    )
                )
        ) {
            // Main content
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.showCamera) {
                    CameraViewfinder(
                        onImageCaptured = { uri -> viewModel.onImageCaptured(uri) },
                        onClose = { viewModel.onHideCamera() },
                        isAnalyzing = uiState.isAnalyzing
                    )
                } else {
                    DietDashboard(
                        uiState = uiState,
                        onSelectMealType = viewModel::onSelectMealType,
                        onDeleteMeal = viewModel::onDeleteMeal,
                        onManualEntry = { viewModel.onShowManualEntry() }
                    )
                }
            }
            
            // Camera FAB - positioned above AI bot FAB
            if (!uiState.showCamera) {
                FloatingActionButton(
                    onClick = {
                        if (hasCameraPermission) {
                            viewModel.onShowCamera()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    containerColor = ElectricBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 96.dp)  // 96dp from bottom to avoid AI bot FAB
                        .shadow(8.dp, CircleShape)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Capture Food")
                }
            }
            
            // Result dialog
            if (uiState.showResultDialog && uiState.analysisResult != null) {
                ClassificationResultDialog(
                    result = uiState.analysisResult!!,
                    onConfirm = { viewModel.onConfirmMeal() },
                    onManualEntry = { viewModel.onShowManualEntry() },
                    onDismiss = { viewModel.onDismissResultDialog() },
                    isLogging = uiState.isLogging,
                    viewModel = viewModel
                )
            }
            
            // Manual entry bottom sheet
            if (uiState.showManualEntry) {
                ManualEntryBottomSheet(
                    uiState = uiState,
                    onSearch = viewModel::onSearchFood,
                    onSelectFood = viewModel::onSelectFood,
                    onLogMeal = viewModel::onLogManualMeal,
                    onDismiss = { viewModel.onHideManualEntry() }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DietTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Diet Tracking",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

/**
 * CameraX viewfinder for food capture.
 */
@Composable
private fun CameraViewfinder(
    onImageCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    isAnalyzing: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(
                            if (flashEnabled) ImageCapture.FLASH_MODE_ON 
                            else ImageCapture.FLASH_MODE_OFF
                        )
                        .build()
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        // Handle camera binding error
                    }
                }, executor)
            }
        )
        
        // Viewfinder overlay
        CameraOverlay(
            onCapture = {
                val photoFile = File(
                    context.cacheDir,
                    "food_${System.currentTimeMillis()}.jpg"
                )
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                
                imageCapture?.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onImageCaptured(Uri.fromFile(photoFile))
                        }
                        override fun onError(exception: ImageCaptureException) {
                            // Handle error - will trigger manual entry fallback
                        }
                    }
                )
            },
            onClose = onClose,
            onToggleFlash = { flashEnabled = !flashEnabled },
            flashEnabled = flashEnabled,
            isAnalyzing = isAnalyzing
        )
    }
}


@Composable
private fun CameraOverlay(
    onCapture: () -> Unit,
    onClose: () -> Unit,
    onToggleFlash: () -> Unit,
    flashEnabled: Boolean,
    isAnalyzing: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Flash toggle
        IconButton(
            onClick = onToggleFlash,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Toggle Flash",
                tint = if (flashEnabled) ElectricBlue else Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Viewfinder frame
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(ElectricBlue, NeonPurple, CyberGreen)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        )
        
        // Instructions
        Text(
            text = "Position food in frame",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 320.dp)
        )
        
        // Capture button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    color = ElectricBlue,
                    modifier = Modifier.size(72.dp)
                )
            } else {
                Button(
                    onClick = onCapture,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(8.dp, CircleShape),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Diet dashboard showing nutrition summary and meals.
 */
@Composable
private fun DietDashboard(
    uiState: DietTrackingUiState,
    onSelectMealType: (LoggedMealType) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onManualEntry: () -> Unit
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Nutrition summary card
        item {
            NutritionSummaryCard(summary = uiState.nutritionSummary)
        }
        
        // Today's meals header with camera hint
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today's Meals",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "📷 Tap camera to add",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        
        if (uiState.todayMeals.isEmpty()) {
            item {
                EmptyMealsCard()
            }
        } else {
            items(uiState.todayMeals) { meal ->
                MealCardWithPhoto(
                    meal = meal,
                    onDelete = { onDeleteMeal(meal.id) }
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}


@Composable
private fun NutritionSummaryCard(
    summary: com.healthtracker.domain.model.DailyNutritionSummary?
) {
    val totalCalories = summary?.totalCalories ?: 0
    val calorieTarget = summary?.calorieTarget ?: 2000
    val caloriesRemaining = calorieTarget - totalCalories
    val isOverGoal = totalCalories > calorieTarget
    val isNearGoal = totalCalories >= (calorieTarget * 0.9) && !isOverGoal
    
    // Color based on status
    val progressColor = when {
        isOverGoal -> Color(0xFFEF4444) // Red - over goal
        isNearGoal -> Color(0xFFFFA500) // Orange - near goal
        else -> CyberGreen // Green - on track
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today's Nutrition",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                // Status badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = progressColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = when {
                            isOverGoal -> "⚠️ Over Goal!"
                            isNearGoal -> "🔥 Almost There"
                            totalCalories > 0 -> "✅ On Track"
                            else -> "📷 Start Logging"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = progressColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Calories progress
            val caloriesProgress = (summary?.caloriesPercentage?.div(100f) ?: 0f).coerceAtMost(1f)
            val animatedProgress by animateFloatAsState(
                targetValue = caloriesProgress,
                animationSpec = tween(1000),
                label = "calories"
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$totalCalories",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                Text(
                    "/ $calorieTarget kcal",
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            // Remaining/Over calories message
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isOverGoal) {
                    "🔴 ${-caloriesRemaining} kcal over - Consider light workout!"
                } else if (caloriesRemaining > 0) {
                    "🟢 $caloriesRemaining kcal remaining"
                } else {
                    "🎯 Goal reached!"
                },
                color = if (isOverGoal) Color(0xFFEF4444) else Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = if (isOverGoal) FontWeight.Bold else FontWeight.Normal
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Macros row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroItem("Protein", summary?.totalProtein ?: 0f, "g", NeonPurple)
                MacroItem("Carbs", summary?.totalCarbs ?: 0f, "g", CyberGreen)
                MacroItem("Fat", summary?.totalFat ?: 0f, "g", ElectricBlue)
            }
            
            // Workout suggestion if over goal
            if (isOverGoal) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💪", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Workout Suggestion",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            val extraCalories = -caloriesRemaining
                            val walkMinutes = (extraCalories / 5).coerceAtLeast(10) // ~5 cal/min walking
                            val runMinutes = (extraCalories / 10).coerceAtLeast(5) // ~10 cal/min running
                            Text(
                                "🚶 ${walkMinutes} min walk OR 🏃 ${runMinutes} min run",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroItem(
    label: String,
    value: Float,
    unit: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${value.toInt()}$unit",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

/**
 * Meal card with photo display - shows captured food image with calories
 */
@Composable
private fun MealCardWithPhoto(
    meal: LoggedMeal,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Show meal photo if available
            if (meal.imageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0D1A))
                ) {
                    val bitmap = remember(meal.imageUri) {
                        try {
                            val uri = Uri.parse(meal.imageUri)
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                android.graphics.BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = meal.foodName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // Fallback icon if image can't be loaded
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Restaurant,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    
                    // Calories overlay on image
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${meal.calories} kcal",
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue,
                            fontSize = 16.sp
                        )
                    }
                    
                    // AI badge if auto-classified
                    if (meal.wasAutoClassified) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = CyberGreen.copy(alpha = 0.9f)
                        ) {
                            Text(
                                "🤖 AI Detected",
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Meal info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meal.foodName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        "${meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() }} • P: ${meal.protein.toInt()}g • C: ${meal.carbs.toInt()}g • F: ${meal.fat.toInt()}g",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                // Show calories text only if no image (otherwise shown on image)
                if (meal.imageUri == null) {
                    Text(
                        "${meal.calories} kcal",
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMealsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Restaurant,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No meals logged yet",
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                "Tap the camera button to get started",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Classification result dialog - shows AUTO-DETECTED food
 * 
 * POWERFUL AUTO-DETECTION using Hugging Face Food-101 Model!
 * Shows: Food Name + Calories - All AUTOMATIC!
 */
@Composable
private fun ClassificationResultDialog(
    result: FoodAnalysisResult,
    onConfirm: () -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit,
    isLogging: Boolean,
    viewModel: DietTrackingViewModel = hiltViewModel()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B)
            )
        ) {
            Box {
                // X Close button at top right
                IconButton(
                    onClick = { 
                        viewModel.onDismissResultDialog()
                        viewModel.onHideCamera()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (result) {
                        is FoodAnalysisResult.Success -> {
                            val foodName = result.classification.foodName
                            val confidence = result.classification.confidence
                            val calories = result.nutrition.calories
                            val protein = result.nutrition.protein
                            val carbs = result.nutrition.carbs
                            val fat = result.nutrition.fat
                            val servingSize = result.nutrition.servingSize
                            
                            // Check if food detected
                            if (foodName == "No Food Detected" || confidence < 0.1f) {
                                // NOT FOOD - Show error
                                Spacer(modifier = Modifier.height(16.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "❌ Food Detect Nahi Hua",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Clear photo click karo ya manual add karo",
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Two clear buttons
                                Button(
                                    onClick = { 
                                        viewModel.onDismissResultDialog()
                                        viewModel.onHideCamera()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("🔄 Retry Photo", fontSize = 16.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { 
                                        viewModel.onDismissResultDialog()
                                        viewModel.onShowManualEntry()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("✏️ Manual Add", fontSize = 16.sp)
                                }
                            } else {
                                // FOOD DETECTED! Show auto-detected result
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Success badge
                                val isMLKitFallback = foodName.contains("Food") || foodName.contains("Auto")
                                
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isMLKitFallback) Color(0xFFFFA500).copy(alpha = 0.2f) else CyberGreen.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (isMLKitFallback) "📷" else "🤖", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (isMLKitFallback) "Food Detected" else "AI Detected",
                                            color = if (isMLKitFallback) Color(0xFFFFA500) else CyberGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (!isMLKitFallback && confidence > 0.3f) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "${(confidence * 100).toInt()}%",
                                                color = CyberGreen.copy(alpha = 0.8f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Food name - BIG and BOLD
                                Text(
                                    if (isMLKitFallback) "Food Detected" else foodName,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                
                                // Serving size
                                Text(
                                    servingSize,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Calories - HUGE display
                                Text(
                                    "$calories",
                                    fontSize = 52.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                                Text(
                                    "kcal",
                                    fontSize = 16.sp,
                                    color = ElectricBlue.copy(alpha = 0.7f)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Macros row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    MacroChip("Protein", "${protein.toInt()}g", NeonPurple)
                                    MacroChip("Carbs", "${carbs.toInt()}g", CyberGreen)
                                    MacroChip("Fat", "${fat.toInt()}g", ElectricBlue)
                                }
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // ✅ YES - Add to list button (GREEN)
                                Button(
                                    onClick = {
                                        viewModel.onConfirmMeal(1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isLogging,
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 16.dp)
                                ) {
                                    if (isLogging) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Text("✅ Yes, Add to List", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // ❌ NO - Manual entry button (GRAY)
                                Button(
                                    onClick = { 
                                        viewModel.onDismissResultDialog()
                                        viewModel.onShowManualEntry()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Text("❌ No, Add Manually", fontSize = 16.sp)
                                }
                            }
                        }
                    
                        is FoodAnalysisResult.LowConfidence -> {
                            // Low confidence - show detected food with clear options
                            val foodName = result.classification.foodName
                            val confidence = result.classification.confidence
                            val nutrition = result.suggestedNutrition
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Warning badge
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFFFA500).copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚠️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Low Confidence",
                                        color = Color(0xFFFFA500),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Detected: $foodName",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            if (nutrition != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "${nutrition.calories} kcal",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // ✅ YES button
                            Button(
                                onClick = {
                                    viewModel.onConfirmMeal(1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLogging,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                if (isLogging) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text("✅ Yes, Add", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // ❌ NO button
                            Button(
                                onClick = { 
                                    viewModel.onDismissResultDialog()
                                    viewModel.onShowManualEntry()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("❌ No, Add Manually")
                            }
                        }
                        
                        is FoodAnalysisResult.ManualEntryRequired -> {
                            // Manual entry required
                            Spacer(modifier = Modifier.height(16.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFFFFA500),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Manual Entry Required",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                result.reason,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { 
                                    viewModel.onDismissResultDialog()
                                    viewModel.onShowManualEntry()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("✏️ Add Manually")
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = { 
                                    viewModel.onDismissResultDialog()
                                    viewModel.onHideCamera()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("🔄 Retry Photo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}


/**
 * Manual entry bottom sheet for food search and logging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntryBottomSheet(
    uiState: DietTrackingUiState,
    onSearch: (String) -> Unit,
    onSelectFood: (com.healthtracker.domain.model.FoodItem) -> Unit,
    onLogMeal: (String, com.healthtracker.domain.model.NutritionInfo, Float, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        ManualEntryContent(
            uiState = uiState,
            onSearch = onSearch,
            onSelectFood = onSelectFood,
            onLogMeal = onLogMeal
        )
    }
}

@Composable
private fun ManualEntryContent(
    uiState: DietTrackingUiState,
    onSearch: (String) -> Unit,
    onSelectFood: (com.healthtracker.domain.model.FoodItem) -> Unit,
    onLogMeal: (String, com.healthtracker.domain.model.NutritionInfo, Float, String?) -> Unit
) {
    var servings by remember { mutableStateOf(1f) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            "Add Food",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search field
        androidx.compose.material3.OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text("Search food...", color = Color.White.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = ElectricBlue
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Search results or selected food
        if (uiState.selectedFood != null) {
            // Show selected food details
            SelectedFoodCard(
                food = uiState.selectedFood,
                servings = servings,
                onServingsChange = { servings = it },
                onLog = {
                    onLogMeal(
                        uiState.selectedFood.name,
                        uiState.selectedFood.nutrition,
                        servings,
                        uiState.capturedImageUri
                    )
                },
                isLogging = uiState.isLogging
            )
        } else if (uiState.searchResults.isNotEmpty()) {
            // Show search results
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp)
            ) {
                items(uiState.searchResults) { food ->
                    FoodSearchResultItem(
                        food = food,
                        onClick = { onSelectFood(food) }
                    )
                }
            }
        } else if (uiState.searchQuery.isNotEmpty()) {
            // No results
            Text(
                "No foods found",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            // Show recent foods
            Text(
                "Recent Foods",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(uiState.recentFoods) { food ->
                    FoodSearchResultItem(
                        food = food,
                        onClick = { onSelectFood(food) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FoodSearchResultItem(
    food: com.healthtracker.domain.model.FoodItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    food.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    food.nutrition.servingSize,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Text(
                "${food.nutrition.calories} kcal",
                color = ElectricBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SelectedFoodCard(
    food: com.healthtracker.domain.model.FoodItem,
    servings: Float,
    onServingsChange: (Float) -> Unit,
    onLog: () -> Unit,
    isLogging: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                food.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Nutrition info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NutritionValue("Calories", "${(food.nutrition.calories * servings).toInt()}", "kcal")
                NutritionValue("Protein", "${(food.nutrition.protein * servings).toInt()}", "g")
                NutritionValue("Carbs", "${(food.nutrition.carbs * servings).toInt()}", "g")
                NutritionValue("Fat", "${(food.nutrition.fat * servings).toInt()}", "g")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Servings selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Servings", color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (servings > 0.5f) onServingsChange(servings - 0.5f) }
                    ) {
                        Text("-", color = ElectricBlue, fontSize = 24.sp)
                    }
                    Text(
                        "$servings",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = { onServingsChange(servings + 0.5f) }
                    ) {
                        Text("+", color = ElectricBlue, fontSize = 24.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onLog,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLogging,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberGreen
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLogging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Log Meal")
                }
            }
        }
    }
}

@Composable
private fun NutritionValue(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = ElectricBlue
        )
        Text(
            "$label ($unit)",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
