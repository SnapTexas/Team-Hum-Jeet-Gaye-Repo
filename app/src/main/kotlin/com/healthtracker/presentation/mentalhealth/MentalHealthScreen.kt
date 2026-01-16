package com.healthtracker.presentation.mentalhealth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.healthtracker.presentation.theme.*
import java.util.concurrent.Executors

// Data classes
enum class Mood { HAPPY, SAD, ANGRY, SURPRISED, NEUTRAL }

data class FaceMoodResult(
    val mood: Mood,
    val confidence: Float,
    val smilingProbability: Float,
    val leftEyeOpenProbability: Float,
    val rightEyeOpenProbability: Float
)

data class Exercise(
    val name: String,
    val description: String,
    val emoji: String,
    val duration: Int,
    val calories: Int,
    val color: Color,
    val instructions: List<String> = emptyList(),
    val benefits: List<String> = emptyList(),
    val tips: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalHealthScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isScanning by remember { mutableStateOf(true) }
    var detectedMood by remember { mutableStateOf<FaceMoodResult?>(null) }
    var scanStatus by remember { mutableStateOf("Initializing camera...") }
    var faceFoundCount by remember { mutableIntStateOf(0) }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Hidden camera - runs in background, no preview shown to user
    if (hasCameraPermission && isScanning && detectedMood == null) {
        HiddenFaceScanner(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onStatusUpdate = { status -> scanStatus = status },
            onFaceFound = { faceFoundCount++ },
            onMoodDetected = { result ->
                detectedMood = result
                isScanning = false
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mental Wellness", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (!isScanning && detectedMood != null) {
                        IconButton(onClick = { 
                            isScanning = true
                            detectedMood = null
                            faceFoundCount = 0
                            scanStatus = "Initializing camera..."
                        }) {
                            Icon(Icons.Default.Refresh, "Rescan", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(Color(0xFF0D0D1A), Color(0xFF1A1A2E), Color(0xFF16213E))))
        ) {
            when {
                !hasCameraPermission -> PermissionNeededContent { permissionLauncher.launch(Manifest.permission.CAMERA) }
                isScanning -> ScanningContent(scanStatus, faceFoundCount)
                detectedMood != null -> MoodResultContent(detectedMood!!) {
                    isScanning = true
                    detectedMood = null
                    faceFoundCount = 0
                }
            }
        }
    }
}

// Store multiple samples for averaging
private data class FaceSample(
    val smileProb: Float,
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val timestamp: Long
)

private const val REQUIRED_SAMPLES = 15  // More samples for accuracy
private const val MIN_CONFIDENCE_THRESHOLD = 0.5f

/**
 * Hidden camera scanner - NO preview shown to user
 * Collects multiple samples and averages them for accurate mood detection
 */
@Composable
private fun HiddenFaceScanner(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onStatusUpdate: (String) -> Unit,
    onFaceFound: () -> Unit,
    onMoodDetected: (FaceMoodResult) -> Unit
) {
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val faceSamples = remember { mutableListOf<FaceSample>() }
    var sampleCount by remember { mutableIntStateOf(0) }
    
    // Use ACCURATE mode for better detection
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // Accurate mode
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // All landmarks
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.2f)  // Larger min face for better accuracy
            .enableTracking()  // Enable face tracking
            .build()
        FaceDetection.getClient(options)
    }
    
    DisposableEffect(Unit) {
        onStatusUpdate("Initializing AI detection...")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Higher resolution for better accuracy
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))  // HD resolution
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageForMood(imageProxy, faceDetector, onStatusUpdate) { sample ->
                                if (sample != null) {
                                    synchronized(faceSamples) {
                                        faceSamples.add(sample)
                                        sampleCount = faceSamples.size
                                    }
                                    onFaceFound()
                                    
                                    // Calculate result when we have enough samples
                                    if (faceSamples.size >= REQUIRED_SAMPLES) {
                                        val result = calculateAveragedMood(faceSamples.toList())
                                        onMoodDetected(result)
                                    }
                                }
                            }
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalyzer)
                onStatusUpdate("Scanning your face...")
                
            } catch (e: Exception) {
                Log.e("MentalHealth", "Camera init failed", e)
                onStatusUpdate("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
        
        onDispose {
            cameraExecutor.shutdown()
            faceDetector.close()
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) { }
        }
    }
}

/**
 * Calculate mood from averaged samples - more accurate than single frame
 */
private fun calculateAveragedMood(samples: List<FaceSample>): FaceMoodResult {
    // Remove outliers (top and bottom 20%)
    val sortedBySmile = samples.sortedBy { it.smileProb }
    val trimmedSamples = sortedBySmile.drop(samples.size / 5).dropLast(samples.size / 5)
    
    // Calculate weighted average (recent samples have more weight)
    var totalWeight = 0f
    var avgSmile = 0f
    var avgLeftEye = 0f
    var avgRightEye = 0f
    
    trimmedSamples.forEachIndexed { index, sample ->
        val weight = (index + 1).toFloat()  // Later samples have more weight
        totalWeight += weight
        avgSmile += sample.smileProb * weight
        avgLeftEye += sample.leftEyeOpen * weight
        avgRightEye += sample.rightEyeOpen * weight
    }
    
    avgSmile /= totalWeight
    avgLeftEye /= totalWeight
    avgRightEye /= totalWeight
    
    // Calculate variance for confidence
    val smileVariance = trimmedSamples.map { (it.smileProb - avgSmile) * (it.smileProb - avgSmile) }.average().toFloat()
    val consistencyBonus = (1f - smileVariance.coerceAtMost(0.5f)) * 0.2f  // Low variance = higher confidence
    
    val (mood, baseConfidence) = analyzeFacialMoodAdvanced(avgSmile, avgLeftEye, avgRightEye)
    val finalConfidence = (baseConfidence + consistencyBonus).coerceAtMost(0.98f)
    
    return FaceMoodResult(
        mood = mood,
        confidence = finalConfidence,
        smilingProbability = avgSmile,
        leftEyeOpenProbability = avgLeftEye,
        rightEyeOpenProbability = avgRightEye
    )
}

/**
 * Advanced mood analysis with better thresholds
 */
private fun analyzeFacialMoodAdvanced(smileProb: Float, leftEyeOpen: Float, rightEyeOpen: Float): Pair<Mood, Float> {
    val avgEyeOpen = (leftEyeOpen + rightEyeOpen) / 2
    val eyeAsymmetry = kotlin.math.abs(leftEyeOpen - rightEyeOpen)
    
    // More nuanced analysis
    return when {
        // Genuine smile - high smile + eyes slightly squinted (Duchenne smile)
        smileProb > 0.75f && avgEyeOpen in 0.5f..0.85f -> Mood.HAPPY to 0.9f
        
        // Moderate smile
        smileProb > 0.5f -> Mood.HAPPY to (0.65f + smileProb * 0.25f)
        
        // Light smile
        smileProb > 0.3f && avgEyeOpen > 0.6f -> Mood.HAPPY to 0.6f
        
        // Surprised - eyes wide open, no smile
        avgEyeOpen > 0.9f && smileProb < 0.15f -> Mood.SURPRISED to 0.75f
        
        // Angry - eyes narrowed, no smile, possible asymmetry
        avgEyeOpen < 0.35f && smileProb < 0.1f -> Mood.ANGRY to 0.7f
        
        // Sad - low smile, droopy eyes
        smileProb < 0.1f && avgEyeOpen < 0.55f -> Mood.SAD to 0.7f
        
        // Slightly sad
        smileProb < 0.2f && avgEyeOpen < 0.65f -> Mood.SAD to 0.55f
        
        // Neutral - normal expression
        else -> Mood.NEUTRAL to 0.65f
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processImageForMood(
    imageProxy: ImageProxy,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    onStatusUpdate: (String) -> Unit,
    onResult: (FaceSample?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val smileProb = face.smilingProbability
                    val leftEyeOpen = face.leftEyeOpenProbability
                    val rightEyeOpen = face.rightEyeOpenProbability
                    
                    // Only accept if we have valid probabilities
                    if (smileProb != null && leftEyeOpen != null && rightEyeOpen != null) {
                        onStatusUpdate("Analyzing expressions...")
                        onResult(FaceSample(
                            smileProb = smileProb,
                            leftEyeOpen = leftEyeOpen,
                            rightEyeOpen = rightEyeOpen,
                            timestamp = System.currentTimeMillis()
                        ))
                    } else {
                        onStatusUpdate("Getting better angle...")
                        onResult(null)
                    }
                } else {
                    onStatusUpdate("Looking for your face...")
                    onResult(null)
                }
            }
            .addOnFailureListener { 
                onStatusUpdate("Retrying...")
                onResult(null) 
            }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun PermissionNeededContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🧠", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI Mood Detection", style = MaterialTheme.typography.headlineSmall, 
            color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("We need camera access to analyze your facial expressions and detect your mood using AI",
            color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable AI Detection")
        }
    }
}

@Composable
private fun ScanningContent(status: String, faceFoundCount: Int) {
    // Animated rotation for the scanning indicator
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated brain/scan icon
        Box(contentAlignment = Alignment.Center) {
            // Outer rotating ring
            Surface(
                modifier = Modifier.size((120 * pulse).dp).rotate(rotation),
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(3.dp, 
                    Brush.sweepGradient(listOf(NeonPurple, ElectricBlue, CyberGreen, NeonPurple)))
            ) {}
            
            // Inner icon
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = NeonPurple.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("🧠", fontSize = 40.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("AI Mood Detection", style = MaterialTheme.typography.headlineSmall,
            color = Color.White, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(status, color = NeonPurple, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress indicator
        if (faceFoundCount > 0) {
            LinearProgressIndicator(
                progress = { (faceFoundCount / REQUIRED_SAMPLES.toFloat()).coerceAtMost(1f) },
                modifier = Modifier.width(200.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = CyberGreen,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Collecting data... ${faceFoundCount}/$REQUIRED_SAMPLES samples", 
                color = CyberGreen, fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Keep looking at your phone", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
    }
}

@Composable
private fun MoodResultContent(moodResult: FaceMoodResult, onRescan: () -> Unit) {
    val exercises = getExercisesForMood(moodResult.mood)
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    
    // Exercise Detail Dialog
    if (selectedExercise != null) {
        ExerciseDetailDialog(
            exercise = selectedExercise!!,
            onDismiss = { selectedExercise = null }
        )
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getMoodEmoji(moodResult.mood), fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "You look ${moodResult.mood.name.lowercase()}!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = getMoodColor(moodResult.mood),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(getMoodMessage(moodResult.mood), color = Color.White.copy(alpha = 0.7f), 
                        textAlign = TextAlign.Center, fontSize = 14.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Confidence: ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = { moodResult.confidence },
                            modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = getMoodColor(moodResult.mood),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        Text(" ${(moodResult.confidence * 100).toInt()}%", 
                            color = getMoodColor(moodResult.mood), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Face metrics
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FaceMetric("😊", "Smile", "${(moodResult.smilingProbability * 100).toInt()}%")
                        FaceMetric("👁️", "Left Eye", "${(moodResult.leftEyeOpenProbability * 100).toInt()}%")
                        FaceMetric("👁️", "Right Eye", "${(moodResult.rightEyeOpenProbability * 100).toInt()}%")
                    }
                }
            }
        }
        
        item {
            Button(
                onClick = onRescan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Icon(Icons.Default.Psychology, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Predict Again")
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("💪 Recommended for you", style = MaterialTheme.typography.titleMedium,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("Tap any exercise for detailed instructions", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        
        items(exercises) { exercise -> 
            ExerciseCard(exercise = exercise, onClick = { selectedExercise = exercise })
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun FaceMetric(emoji: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = exercise.color.copy(alpha = 0.2f), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(exercise.emoji, fontSize = 24.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(exercise.description, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Surface(shape = RoundedCornerShape(8.dp), color = exercise.color.copy(alpha = 0.2f)) {
                        Text("${exercise.duration} min", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = exercise.color, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = CyberGreen.copy(alpha = 0.2f)) {
                        Text("~${exercise.calories} cal", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = CyberGreen, fontSize = 12.sp)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ExerciseDetailDialog(exercise: Exercise, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(exercise.color.copy(alpha = 0.2f))
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(exercise.emoji, fontSize = 48.sp)
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(exercise.name, style = MaterialTheme.typography.headlineMedium,
                            color = Color.White, fontWeight = FontWeight.Bold)
                        Text(exercise.description, color = Color.White.copy(alpha = 0.7f))
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Icon(Icons.Default.Timer, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${exercise.duration} min", color = Color.White, fontSize = 14.sp)
                                }
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Icon(Icons.Default.LocalFireDepartment, null, tint = CyberGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("~${exercise.calories} cal", color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                
                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    // Instructions
                    if (exercise.instructions.isNotEmpty()) {
                        Text("📋 How to do it", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        exercise.instructions.forEachIndexed { index, instruction ->
                            Row(modifier = Modifier.padding(vertical = 6.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = exercise.color,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(instruction, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    
                    // Benefits
                    if (exercise.benefits.isNotEmpty()) {
                        Text("✨ Benefits", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        exercise.benefits.forEach { benefit ->
                            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("•", color = CyberGreen, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(benefit, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                    
                    // Tips
                    if (exercise.tips.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFA500).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Text("💡", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Pro Tip", fontWeight = FontWeight.Bold, color = Color(0xFFFFA500), fontSize = 14.sp)
                                    Text(exercise.tips, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Start button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = exercise.color)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Got it!")
                    }
                }
            }
        }
    }
}

private fun getMoodEmoji(mood: Mood): String = when (mood) {
    Mood.HAPPY -> "😊"
    Mood.SAD -> "😢"
    Mood.ANGRY -> "😠"
    Mood.SURPRISED -> "😲"
    Mood.NEUTRAL -> "😐"
}

private fun getMoodColor(mood: Mood): Color = when (mood) {
    Mood.HAPPY -> CyberGreen
    Mood.SAD -> Color(0xFF6B7FD7)
    Mood.ANGRY -> Color(0xFFE53935)
    Mood.SURPRISED -> Color(0xFFFFA500)
    Mood.NEUTRAL -> NeonPurple
}

private fun getMoodMessage(mood: Mood): String = when (mood) {
    Mood.HAPPY -> "Great to see you smiling! Let's keep that positive energy going!"
    Mood.SAD -> "It's okay to feel down sometimes. Here are some activities to lift your spirits."
    Mood.ANGRY -> "Take a deep breath. Physical activity can help release that tension."
    Mood.SURPRISED -> "Something caught your attention! Channel that alertness into productivity."
    Mood.NEUTRAL -> "You seem calm and composed. Perfect time for some mindful activities."
}

private fun getExercisesForMood(mood: Mood): List<Exercise> = when (mood) {
    Mood.HAPPY -> listOf(
        Exercise(
            name = "HIIT Workout",
            description = "High intensity to match your energy!",
            emoji = "🔥",
            duration = 20,
            calories = 200,
            color = Color(0xFFFF6B35),
            instructions = listOf(
                "Start with 2 minutes of light jogging in place to warm up",
                "Do 30 seconds of burpees - jump up, drop to plank, push-up, jump back up",
                "Rest for 15 seconds, then 30 seconds of high knees",
                "Rest 15 seconds, then 30 seconds of jump squats",
                "Rest 15 seconds, then 30 seconds of mountain climbers",
                "Repeat this circuit 4 times with 1 minute rest between circuits",
                "Cool down with 2 minutes of stretching"
            ),
            benefits = listOf("Burns maximum calories in minimum time", "Boosts metabolism for hours", "Improves cardiovascular health", "Releases endorphins"),
            tips = "Keep your core tight throughout and breathe steadily. Modify exercises if needed - quality over speed!"
        ),
        Exercise(
            name = "Dance Workout",
            description = "Keep the good vibes going!",
            emoji = "💃",
            duration = 25,
            calories = 180,
            color = NeonPurple,
            instructions = listOf(
                "Put on your favorite upbeat playlist",
                "Start with simple side steps and arm swings for 3 minutes",
                "Add grapevines - step side, cross behind, step side, tap",
                "Try some hip movements - circles, figure 8s, shimmies",
                "Add jumps and spins when you feel comfortable",
                "Let loose! There's no wrong way to dance",
                "Cool down with slow swaying for the last 2 minutes"
            ),
            benefits = listOf("Full body workout disguised as fun", "Improves coordination and balance", "Boosts mood and confidence", "Great stress reliever"),
            tips = "Don't worry about looking perfect - just move and have fun! Close your eyes if you feel self-conscious."
        ),
        Exercise(
            name = "Running",
            description = "Channel that positive energy",
            emoji = "🏃",
            duration = 30,
            calories = 300,
            color = CyberGreen,
            instructions = listOf(
                "Start with 5 minutes of brisk walking to warm up",
                "Begin jogging at a comfortable pace you can maintain",
                "Focus on your breathing - inhale for 3 steps, exhale for 2",
                "Keep your shoulders relaxed and arms at 90 degrees",
                "Land on your midfoot, not your heels",
                "Maintain pace for 20 minutes",
                "Cool down with 5 minutes of walking and stretching"
            ),
            benefits = listOf("Excellent cardiovascular exercise", "Clears your mind", "Builds endurance", "Strengthens legs and core"),
            tips = "Run at a pace where you could hold a conversation. If you're gasping, slow down!"
        ),
        Exercise(
            name = "Strength Training",
            description = "Build on your motivation",
            emoji = "💪",
            duration = 30,
            calories = 150,
            color = ElectricBlue,
            instructions = listOf(
                "Warm up with arm circles and leg swings for 3 minutes",
                "Push-ups: 3 sets of 10-15 reps (modify on knees if needed)",
                "Squats: 3 sets of 15 reps - keep weight in heels",
                "Plank: Hold for 30-60 seconds, 3 times",
                "Lunges: 3 sets of 10 each leg",
                "Tricep dips using a chair: 3 sets of 12",
                "Rest 30-60 seconds between sets"
            ),
            benefits = listOf("Builds lean muscle mass", "Increases metabolism", "Improves bone density", "Boosts confidence"),
            tips = "Focus on form over reps. If you can't maintain good form, reduce the number of reps."
        )
    )
    Mood.SAD -> listOf(
        Exercise(
            name = "Walking",
            description = "Light walk to boost endorphins",
            emoji = "🚶",
            duration = 20,
            calories = 80,
            color = ElectricBlue,
            instructions = listOf(
                "Put on comfortable shoes and step outside",
                "Start walking at a gentle, comfortable pace",
                "Focus on your surroundings - notice colors, sounds, smells",
                "Take deep breaths as you walk",
                "Gradually increase your pace if you feel like it",
                "Try to walk in nature if possible - a park or tree-lined street",
                "End with some gentle stretches"
            ),
            benefits = listOf("Gentle way to boost mood", "Gets you outside and into nature", "Low impact on joints", "Clears your mind"),
            tips = "Leave your phone on silent. This is your time to disconnect and just be present."
        ),
        Exercise(
            name = "Yoga",
            description = "Gentle stretching for emotional release",
            emoji = "🧘",
            duration = 15,
            calories = 50,
            color = NeonPurple,
            instructions = listOf(
                "Find a quiet space and sit comfortably",
                "Child's Pose: Kneel, sit back on heels, stretch arms forward - hold 1 min",
                "Cat-Cow: On all fours, arch and round your back slowly - 10 times",
                "Seated Forward Fold: Sit with legs extended, reach for toes - hold 1 min",
                "Supine Twist: Lie on back, drop knees to one side - hold 1 min each side",
                "Legs Up Wall: Lie with legs up against wall - hold 3 minutes",
                "End in Savasana: Lie flat, close eyes, breathe deeply for 2 minutes"
            ),
            benefits = listOf("Releases tension stored in body", "Calms the nervous system", "Improves flexibility", "Creates space for emotions"),
            tips = "It's okay to cry during yoga - it's a release. Let whatever comes up, come up."
        ),
        Exercise(
            name = "Deep Breathing",
            description = "4-7-8 breathing technique",
            emoji = "🌬️",
            duration = 5,
            calories = 10,
            color = CyberGreen,
            instructions = listOf(
                "Sit or lie down in a comfortable position",
                "Place one hand on your chest, one on your belly",
                "Breathe in through your nose for 4 counts",
                "Hold your breath for 7 counts",
                "Exhale slowly through your mouth for 8 counts",
                "Repeat this cycle 4-8 times",
                "Notice how your body feels more relaxed with each breath"
            ),
            benefits = listOf("Activates parasympathetic nervous system", "Reduces anxiety immediately", "Can be done anywhere", "Helps with sleep"),
            tips = "If 4-7-8 feels too long, start with 3-5-6 and work your way up."
        ),
        Exercise(
            name = "Dance",
            description = "Put on happy music and move!",
            emoji = "💃",
            duration = 10,
            calories = 60,
            color = Color(0xFFFF6B35),
            instructions = listOf(
                "Put on a song that makes you feel good (even if you don't feel like it)",
                "Start by just swaying or nodding your head",
                "Let your body move however it wants to",
                "Don't judge your movements - there's no wrong way",
                "Try to smile, even if forced - it tricks your brain",
                "Gradually let yourself move more freely",
                "By the end, you might actually feel a bit better!"
            ),
            benefits = listOf("Music therapy combined with movement", "Releases endorphins", "Shifts your energy", "Reminds you that joy is possible"),
            tips = "Start with just one song. You don't have to commit to more. But you might want to!"
        )
    )
    Mood.ANGRY -> listOf(
        Exercise(
            name = "Boxing",
            description = "Release tension safely",
            emoji = "🥊",
            duration = 20,
            calories = 200,
            color = Color(0xFFE53935),
            instructions = listOf(
                "Stand with feet shoulder-width apart, knees slightly bent",
                "Make fists with thumbs outside, guard your face",
                "Jab: Quick straight punch with front hand - 30 seconds",
                "Cross: Powerful punch with back hand, rotate hips - 30 seconds",
                "Hook: Curved punch to the side - 30 seconds each arm",
                "Uppercut: Punch upward from below - 30 seconds each arm",
                "Combine all punches in rapid succession for 2 minutes",
                "Rest 30 seconds, repeat 4 times"
            ),
            benefits = listOf("Safe outlet for anger", "Full body workout", "Improves coordination", "Builds confidence"),
            tips = "Imagine punching away your frustrations. Grunt or yell if it helps - let it out!"
        ),
        Exercise(
            name = "Running",
            description = "Burn off stress hormones",
            emoji = "🏃",
            duration = 25,
            calories = 250,
            color = Color(0xFFFF6B35),
            instructions = listOf(
                "Start with a brisk 3-minute walk",
                "Begin running at a pace that feels challenging",
                "Push yourself harder than usual - use that anger as fuel",
                "Sprint for 30 seconds when you feel extra frustrated",
                "Return to jogging pace for 1 minute",
                "Repeat sprints 5-8 times",
                "Cool down with 5 minutes of walking"
            ),
            benefits = listOf("Burns off cortisol and adrenaline", "Provides physical outlet", "Clears your head", "Exhausts angry energy"),
            tips = "Run until you feel the anger leave your body. It's okay to push hard today."
        ),
        Exercise(
            name = "Power Yoga",
            description = "Intense but calming",
            emoji = "🧘",
            duration = 30,
            calories = 150,
            color = NeonPurple,
            instructions = listOf(
                "Start in Mountain Pose, take 5 deep breaths",
                "Sun Salutation A: Flow through 5 rounds",
                "Warrior I: Hold 5 breaths each side",
                "Warrior II: Hold 5 breaths each side",
                "Chair Pose: Hold for 10 breaths",
                "Boat Pose: Hold for 5 breaths, repeat 3 times",
                "End with 5 minutes in Savasana"
            ),
            benefits = listOf("Channels anger into strength", "Builds heat to release tension", "Requires focus, quieting angry thoughts", "Leaves you feeling powerful but calm"),
            tips = "Hold poses longer than comfortable. The discomfort gives your mind something else to focus on."
        ),
        Exercise(
            name = "Jump Rope",
            description = "Quick cardio to cool down",
            emoji = "🪢",
            duration = 10,
            calories = 120,
            color = CyberGreen,
            instructions = listOf(
                "Hold rope handles at hip height",
                "Start with basic jumps - both feet together",
                "Jump just high enough for rope to pass under",
                "Keep elbows close to body, rotate from wrists",
                "Try 30 seconds on, 15 seconds rest",
                "Increase speed when you feel stable",
                "Try alternating feet like running in place"
            ),
            benefits = listOf("Quick way to burn energy", "Requires coordination (distracts from anger)", "Portable - do it anywhere", "Childhood nostalgia can lighten mood"),
            tips = "If you don't have a rope, just pretend! The jumping motion works the same."
        )
    )
    Mood.SURPRISED -> listOf(
        Exercise(
            name = "Sprint Intervals",
            description = "Use that alertness!",
            emoji = "⚡",
            duration = 15,
            calories = 180,
            color = Color(0xFFFF6B35),
            instructions = listOf(
                "Warm up with 2 minutes of jogging",
                "Sprint at maximum effort for 20 seconds",
                "Walk or slow jog for 40 seconds to recover",
                "Repeat sprint/recovery cycle 8-10 times",
                "Focus on pumping your arms and driving your knees",
                "Cool down with 3 minutes of walking"
            ),
            benefits = listOf("Matches your heightened energy state", "Burns calories efficiently", "Improves speed and power", "Releases adrenaline productively"),
            tips = "Give 100% on sprints - they're short! The recovery periods are your reward."
        ),
        Exercise(
            name = "Bodyweight Circuit",
            description = "Quick full body workout",
            emoji = "💪",
            duration = 20,
            calories = 150,
            color = CyberGreen,
            instructions = listOf(
                "Jumping Jacks: 1 minute",
                "Push-ups: 15 reps",
                "Squats: 20 reps",
                "Plank: 45 seconds",
                "Burpees: 10 reps",
                "Mountain Climbers: 30 seconds",
                "Rest 1 minute, repeat circuit 3 times"
            ),
            benefits = listOf("No equipment needed", "Works entire body", "Keeps you engaged with variety", "Builds functional strength"),
            tips = "Move quickly between exercises to keep your heart rate up and maintain that energized feeling."
        ),
        Exercise(
            name = "Cycling",
            description = "Explore and stay active",
            emoji = "🚴",
            duration = 25,
            calories = 180,
            color = ElectricBlue,
            instructions = listOf(
                "Adjust seat so leg is slightly bent at bottom of pedal stroke",
                "Start with easy pedaling for 5 minutes",
                "Increase resistance or find a hill",
                "Maintain steady cadence of 70-90 RPM",
                "Stand up on pedals for 30 seconds every few minutes",
                "Explore a new route if possible",
                "Cool down with easy spinning for 3 minutes"
            ),
            benefits = listOf("Low impact cardio", "Explore your surroundings", "Fresh air and scenery", "Meditative rhythm"),
            tips = "Use this time to process whatever surprised you. The rhythmic motion helps thinking."
        ),
        Exercise(
            name = "Stretching",
            description = "Ground yourself",
            emoji = "🤸",
            duration = 15,
            calories = 40,
            color = NeonPurple,
            instructions = listOf(
                "Neck rolls: Slowly circle head 5 times each direction",
                "Shoulder shrugs: Lift and drop shoulders 10 times",
                "Standing side stretch: Reach arm overhead, lean to side - 30 sec each",
                "Forward fold: Bend at hips, let head hang - 1 minute",
                "Quad stretch: Pull foot to glutes - 30 sec each leg",
                "Seated butterfly: Soles together, press knees down - 1 minute",
                "Lying spinal twist: 1 minute each side"
            ),
            benefits = listOf("Calms the nervous system", "Releases physical tension", "Brings awareness to body", "Helps process emotions"),
            tips = "Breathe deeply into each stretch. Use this time to ground yourself after the surprise."
        )
    )
    Mood.NEUTRAL -> listOf(
        Exercise(
            name = "Walking",
            description = "Easy start to get moving",
            emoji = "🚶",
            duration = 20,
            calories = 80,
            color = CyberGreen,
            instructions = listOf(
                "Step outside or find a treadmill",
                "Walk at a comfortable, moderate pace",
                "Swing your arms naturally",
                "Focus on good posture - shoulders back, core engaged",
                "Try to walk somewhere pleasant if possible",
                "Gradually increase pace in the middle 10 minutes",
                "Slow down for the last 3 minutes"
            ),
            benefits = listOf("Low barrier to entry", "Gentle cardiovascular exercise", "Time to think or listen to podcasts", "Easy on joints"),
            tips = "A neutral mood is a great starting point. Walking might shift you toward happy!"
        ),
        Exercise(
            name = "Yoga Flow",
            description = "Balance body and mind",
            emoji = "🧘",
            duration = 20,
            calories = 80,
            color = NeonPurple,
            instructions = listOf(
                "Start in Easy Seated Pose, breathe deeply for 1 minute",
                "Cat-Cow stretches: 10 rounds",
                "Downward Dog: Hold 5 breaths",
                "Low Lunge: 5 breaths each side",
                "Warrior II to Reverse Warrior flow: 5 times each side",
                "Tree Pose: Hold 30 seconds each side",
                "End in Savasana for 3 minutes"
            ),
            benefits = listOf("Improves flexibility", "Builds mind-body connection", "Reduces stress", "Increases energy"),
            tips = "Let your breath guide your movement. There's no rush - enjoy the flow."
        ),
        Exercise(
            name = "Meditation",
            description = "Deepen your calm state",
            emoji = "🧘‍♀️",
            duration = 15,
            calories = 20,
            color = ElectricBlue,
            instructions = listOf(
                "Find a quiet, comfortable place to sit",
                "Set a timer for 15 minutes",
                "Close your eyes and focus on your breath",
                "Notice the sensation of air entering and leaving",
                "When thoughts arise, acknowledge them and return to breath",
                "Don't judge yourself for wandering - it's normal",
                "End by slowly opening your eyes and stretching"
            ),
            benefits = listOf("Reduces stress and anxiety", "Improves focus and clarity", "Builds emotional resilience", "Can be done anywhere"),
            tips = "Start with just 5 minutes if 15 feels too long. Consistency matters more than duration."
        ),
        Exercise(
            name = "Light Jog",
            description = "Gentle cardio",
            emoji = "🏃",
            duration = 20,
            calories = 150,
            color = Color(0xFFFF6B35),
            instructions = listOf(
                "Start with 3 minutes of walking",
                "Begin jogging at a very easy pace",
                "You should be able to hold a conversation",
                "Focus on landing softly and breathing steadily",
                "Keep your shoulders relaxed",
                "Maintain this easy pace for 15 minutes",
                "Cool down with 2 minutes of walking"
            ),
            benefits = listOf("Boosts energy levels", "Improves cardiovascular health", "Clears mental fog", "Sets positive tone for the day"),
            tips = "This isn't about speed or distance. Just enjoy moving your body at a comfortable pace."
        )
    )
}
