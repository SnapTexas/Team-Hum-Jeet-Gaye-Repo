package com.healthtracker.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.NeonPurple
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.GlassSurface

/**
 * F01: User Onboarding - Minimal & Fast
 * 
 * Collects: Name, Age, Weight, Height, Goal
 * Target: <30 second completion time
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingViewModel.OnboardingEvent.NavigateToDashboard -> {
                    onOnboardingComplete()
                }
                is OnboardingViewModel.OnboardingEvent.ShowError -> {
                    // Could show snackbar here
                }
            }
        }
    }
    
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            if (uiState.currentStep != OnboardingViewModel.OnboardingStep.WELCOME) {
                OnboardingProgress(
                    currentStep = uiState.currentStep,
                    onBack = viewModel::onPreviousStep
                )
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(60.dp))
            }
            
            // Animated content for each step
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    OnboardingViewModel.OnboardingStep.WELCOME -> WelcomeStep(
                        onGetStarted = viewModel::skipToGetStarted
                    )
                    OnboardingViewModel.OnboardingStep.NAME -> NameStep(
                        name = uiState.name,
                        error = uiState.errors["name"],
                        onNameChanged = viewModel::onNameChanged,
                        onNext = viewModel::onNextStep
                    )
                    OnboardingViewModel.OnboardingStep.AGE -> AgeStep(
                        age = uiState.age,
                        error = uiState.errors["age"],
                        onAgeChanged = viewModel::onAgeChanged,
                        onNext = viewModel::onNextStep
                    )
                    OnboardingViewModel.OnboardingStep.BODY_METRICS -> BodyMetricsStep(
                        weight = uiState.weight,
                        height = uiState.height,
                        weightError = uiState.errors["weight"],
                        heightError = uiState.errors["height"],
                        onWeightChanged = viewModel::onWeightChanged,
                        onHeightChanged = viewModel::onHeightChanged,
                        onNext = viewModel::onNextStep
                    )
                    OnboardingViewModel.OnboardingStep.GOAL -> GoalStep(
                        selectedGoal = uiState.selectedGoal,
                        onGoalSelected = viewModel::onGoalSelected,
                        onComplete = viewModel::onNextStep,
                        isLoading = uiState.isLoading
                    )
                    OnboardingViewModel.OnboardingStep.COMPLETE -> {
                        // Will navigate automatically
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricBlue)
                        }
                    }
                }
            }
            
            // Next button (except for welcome and goal steps)
            if (uiState.currentStep !in listOf(
                OnboardingViewModel.OnboardingStep.WELCOME,
                OnboardingViewModel.OnboardingStep.GOAL,
                OnboardingViewModel.OnboardingStep.COMPLETE
            )) {
                NextButton(
                    enabled = uiState.canProceed,
                    onClick = viewModel::onNextStep
                )
            }
        }
    }
}

@Composable
private fun OnboardingProgress(
    currentStep: OnboardingViewModel.OnboardingStep,
    onBack: () -> Unit
) {
    val steps = listOf(
        OnboardingViewModel.OnboardingStep.NAME,
        OnboardingViewModel.OnboardingStep.AGE,
        OnboardingViewModel.OnboardingStep.BODY_METRICS,
        OnboardingViewModel.OnboardingStep.GOAL
    )
    val currentIndex = steps.indexOf(currentStep).coerceAtLeast(0)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            steps.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentIndex) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index <= currentIndex) ElectricBlue
                            else Color.White.copy(alpha = 0.3f)
                        )
                )
                if (index < steps.lastIndex) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
        
        // Placeholder for symmetry
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏃‍♂️",
            fontSize = 80.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Welcome to\nHealth Tracker",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Your personal health companion.\nLet's set up your profile in 30 seconds!",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricBlue
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun NameStep(
    name: String,
    error: String?,
    onNameChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What's your name?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "We'll use this to personalize your experience",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your name") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, color = Color(0xFFFF6B6B)) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ElectricBlue
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (name.isNotBlank()) onNext()
            }),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun AgeStep(
    age: String,
    error: String?,
    onAgeChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How old are you?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This helps us calculate your health metrics",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = age,
            onValueChange = { if (it.length <= 3) onAgeChanged(it.filter { c -> c.isDigit() }) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your age") },
            suffix = { Text("years", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, color = Color(0xFFFF6B6B)) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ElectricBlue
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (age.isNotBlank()) onNext()
            }),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun BodyMetricsStep(
    weight: String,
    height: String,
    weightError: String?,
    heightError: String?,
    onWeightChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your body metrics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Used for accurate calorie & BMI calculations",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Weight
        OutlinedTextField(
            value = weight,
            onValueChange = { onWeightChanged(it.filter { c -> c.isDigit() || c == '.' }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Weight", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("e.g., 70") },
            suffix = { Text("kg", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            isError = weightError != null,
            supportingText = weightError?.let { { Text(it, color = Color(0xFFFF6B6B)) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ElectricBlue
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Height
        OutlinedTextField(
            value = height,
            onValueChange = { onHeightChanged(it.filter { c -> c.isDigit() || c == '.' }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Height", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("e.g., 175") },
            suffix = { Text("cm", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            isError = heightError != null,
            supportingText = heightError?.let { { Text(it, color = Color(0xFFFF6B6B)) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = ElectricBlue
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
            }),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun GoalStep(
    selectedGoal: HealthGoal?,
    onGoalSelected: (HealthGoal) -> Unit,
    onComplete: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "What's your goal?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "We'll customize your experience based on this",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Goal options
        GoalOption(
            icon = Icons.Default.MonitorWeight,
            title = "Weight Loss",
            subtitle = "Lose weight & get fit",
            color = Color(0xFFFF6B6B),
            isSelected = selectedGoal == HealthGoal.WEIGHT_LOSS,
            onClick = { onGoalSelected(HealthGoal.WEIGHT_LOSS) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        GoalOption(
            icon = Icons.Default.FitnessCenter,
            title = "Fitness",
            subtitle = "Build strength & endurance",
            color = ElectricBlue,
            isSelected = selectedGoal == HealthGoal.FITNESS,
            onClick = { onGoalSelected(HealthGoal.FITNESS) }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        GoalOption(
            icon = Icons.Default.SelfImprovement,
            title = "General Wellness",
            subtitle = "Maintain a healthy lifestyle",
            color = CyberGreen,
            isSelected = selectedGoal == HealthGoal.GENERAL,
            onClick = { onGoalSelected(HealthGoal.GENERAL) }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Complete button
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = selectedGoal != null && !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberGreen,
                disabledContainerColor = CyberGreen.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Complete Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GoalOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, color, RoundedCornerShape(16.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.15f) else GlassSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = color
                )
            }
        }
    }
}

@Composable
private fun NextButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricBlue,
            disabledContainerColor = ElectricBlue.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = "Continue",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, contentDescription = null)
    }
}
