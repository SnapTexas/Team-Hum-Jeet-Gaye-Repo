package com.healthtracker.presentation.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthtracker.domain.model.DailyAnalytics
import com.healthtracker.domain.model.GoalProgress
import com.healthtracker.domain.model.HealthGoal
import com.healthtracker.domain.model.HealthMetrics
import com.healthtracker.domain.model.Insight
import com.healthtracker.domain.model.InsightPriority
import com.healthtracker.domain.model.InsightType
import com.healthtracker.domain.model.MetricType
import com.healthtracker.domain.model.Suggestion
import com.healthtracker.domain.model.SuggestionAction
import com.healthtracker.domain.model.SuggestionType
import com.healthtracker.domain.model.WeeklyAnalytics
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.GlassSurface
import com.healthtracker.presentation.theme.GlowWhite
import com.healthtracker.presentation.theme.HealthTrackerTheme
import com.healthtracker.presentation.theme.NeonPurple
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Premium Dashboard screen with glassmorphism and 3D futuristic design.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToDiet: () -> Unit = {},
    onNavigateToPlanning: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Request ACTIVITY_RECOGNITION permission for step counter (Android 10+)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refresh() // Restart step counter after permission granted
        }
    }
    
    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
    
    DashboardContent(
        uiState = uiState,
        onTabSelected = viewModel::selectTab,
        onNavigatePrevious = viewModel::navigatePrevious,
        onNavigateNext = viewModel::navigateNext,
        onRefresh = viewModel::refresh,
        onDismissSuggestion = viewModel::dismissSuggestion,
        onCompleteSuggestion = viewModel::completeSuggestion,
        modifier = modifier
    )
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onTabSelected: (AnalyticsTab) -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onRefresh: () -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onCompleteSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
        // Floating orb effects
        FloatingOrbs()
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with date navigation
            item {
                DashboardHeader(
                    uiState = uiState,
                    onNavigatePrevious = onNavigatePrevious,
                    onNavigateNext = onNavigateNext,
                    onRefresh = onRefresh
                )
            }
            
            // Tab selector
            item {
                GlassmorphicTabRow(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = onTabSelected
                )
            }
            
            // Loading indicator
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = ElectricBlue,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
            
            // Error message
            uiState.error?.let { error ->
                item {
                    GlassmorphicCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
            // Content based on selected tab
            when (uiState.selectedTab) {
                AnalyticsTab.DAILY -> {
                    // User Profile Summary Card (shows personalized data)
                    if (uiState.userName.isNotEmpty()) {
                        item {
                            UserProfileCard(
                                userName = uiState.userName,
                                goal = uiState.userGoal.name.replace("_", " "),
                                bmi = uiState.bmi,
                                bmiCategory = uiState.bmiCategory,
                                calorieGoal = uiState.dailyCalorieGoal,
                                stepGoal = uiState.dailyStepGoal,
                                waterGoal = uiState.dailyWaterGoal
                            )
                        }
                    }
                    
                    // Live Steps Card (from device sensor)
                    item {
                        LiveStepsCard(
                            steps = uiState.liveStepCount,
                            calories = uiState.liveCalories,
                            distance = uiState.liveDistance,
                            stepGoal = uiState.dailyStepGoal,
                            sensorStatus = uiState.sensorStatus
                        )
                    }
                    
                    // AI Suggestions section
                    if (uiState.suggestions.isNotEmpty()) {
                        item {
                            SuggestionsSection(
                                suggestions = uiState.suggestions,
                                onDismiss = onDismissSuggestion,
                                onComplete = onCompleteSuggestion
                            )
                        }
                    }
                }
                AnalyticsTab.WEEKLY -> {
                    // Real weekly stats from step counter history
                    item {
                        WeeklyStatsCard(
                            totalSteps = uiState.weeklyTotalSteps,
                            avgSteps = uiState.weeklyAvgSteps,
                            stepGoal = uiState.dailyStepGoal,
                            totalCalories = uiState.weeklyTotalCalories,
                            totalDistance = uiState.weeklyTotalDistance,
                            daysWithData = uiState.weeklyDaysWithData
                        )
                    }
                }
                AnalyticsTab.MONTHLY -> {
                    // Real monthly stats from step counter history
                    item {
                        MonthlyStatsCard(
                            totalSteps = uiState.monthlyTotalSteps,
                            avgSteps = uiState.monthlyAvgSteps,
                            stepGoal = uiState.dailyStepGoal,
                            totalCalories = uiState.monthlyTotalCalories,
                            totalDistance = uiState.monthlyTotalDistance,
                            daysWithData = uiState.monthlyDaysWithData,
                            bestDaySteps = uiState.monthlyBestDaySteps
                        )
                    }
                }
            }
        }
    }
}

/**
 * Weekly Stats Card showing REAL historical data.
 */
@Composable
private fun WeeklyStatsCard(
    totalSteps: Int,
    avgSteps: Int,
    stepGoal: Int,
    totalCalories: Int,
    totalDistance: Double,
    daysWithData: Int
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = ElectricBlue
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Weekly Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$daysWithData days tracked",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowWhite
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (daysWithData == 0) {
                Text(
                    text = "No data yet. Start walking to see your weekly stats!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlowWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("👟", "%,d".format(totalSteps), "Total Steps", ElectricBlue)
                    StatItem("📈", "%,d".format(avgSteps), "Avg/Day", CyberGreen)
                    StatItem("🎯", "${(avgSteps * 100 / stepGoal.coerceAtLeast(1))}%", "Goal", NeonPurple)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("🔥", "%,d".format(totalCalories), "Calories", Color(0xFFFF6B6B))
                    StatItem("📍", String.format("%.1f km", totalDistance / 1000), "Distance", Color(0xFF06B6D4))
                }
            }
        }
    }
}

/**
 * Monthly Stats Card showing REAL historical data.
 */
@Composable
private fun MonthlyStatsCard(
    totalSteps: Int,
    avgSteps: Int,
    stepGoal: Int,
    totalCalories: Int,
    totalDistance: Double,
    daysWithData: Int,
    bestDaySteps: Int
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonPurple
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 Monthly Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$daysWithData days tracked",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowWhite
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (daysWithData == 0) {
                Text(
                    text = "No data yet. Start walking to see your monthly stats!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlowWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("👟", "%,d".format(totalSteps), "Total Steps", ElectricBlue)
                    StatItem("📈", "%,d".format(avgSteps), "Avg/Day", CyberGreen)
                    StatItem("🏆", "%,d".format(bestDaySteps), "Best Day", Color(0xFFF59E0B))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("🔥", "%,d".format(totalCalories), "Calories", Color(0xFFFF6B6B))
                    StatItem("📍", String.format("%.1f km", totalDistance / 1000), "Distance", Color(0xFF06B6D4))
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 24.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GlowWhite
        )
    }
}

/**
 * Live Steps Card showing real-time step count from device sensor.
 * Uses the SAME sensor as Xiaomi App Vault / Digital Wellbeing!
 */
@Composable
private fun LiveStepsCard(
    steps: Int,
    calories: Int,
    distance: Double,
    stepGoal: Int,
    sensorStatus: String
) {
    val progress = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 1f -> CyberGreen
        progress >= 0.7f -> Color(0xFFF59E0B)
        else -> ElectricBlue
    }
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = progressColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📱 Live Steps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "From device sensor • $sensorStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
                Text(text = "👟", fontSize = 32.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Big step count
            Text(
                text = "%,d".format(steps),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
            Text(
                text = "/ %,d steps".format(stepGoal),
                style = MaterialTheme.typography.bodyMedium,
                color = GlowWhite
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(progressColor)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Calories and distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "🔥", fontSize = 20.sp)
                    Text(
                        text = "$calories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(text = "kcal", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📍", fontSize = 20.sp)
                    Text(
                        text = String.format("%.1f", distance / 1000),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                    Text(text = "km", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                }
            }
        }
    }
}

/**
 * User Profile Card showing personalized goals based on onboarding data.
 */
@Composable
private fun UserProfileCard(
    userName: String,
    goal: String,
    bmi: Float,
    bmiCategory: String,
    calorieGoal: Int,
    stepGoal: Int,
    waterGoal: Int
) {
    val bmiColor = when {
        bmi < 18.5f -> Color(0xFFF59E0B)
        bmi < 25f -> CyberGreen
        bmi < 30f -> Color(0xFFF59E0B)
        else -> Color(0xFFFF6B6B)
    }
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = ElectricBlue
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${userName.split(" ").first()}! 👋",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Goal: $goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricBlue
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.1f", bmi),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = bmiColor
                    )
                    Text(
                        text = bmiCategory,
                        style = MaterialTheme.typography.labelSmall,
                        color = bmiColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Your Personalized Goals",
                style = MaterialTheme.typography.labelMedium,
                color = GlowWhite,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GoalChip("🔥", "$calorieGoal", "kcal", Color(0xFFFF6B6B))
                GoalChip("👟", "${stepGoal / 1000}k", "steps", ElectricBlue)
                GoalChip("💧", String.format("%.1fL", waterGoal / 1000f), "water", Color(0xFF06B6D4))
            }
        }
    }
}

@Composable
private fun GoalChip(icon: String, value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GlowWhite
        )
    }
}


@Composable
private fun FloatingOrbs() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Electric blue orb
        Box(
            modifier = Modifier
                .size(200.dp)
                .blur(80.dp)
                .background(
                    ElectricBlue.copy(alpha = 0.15f),
                    CircleShape
                )
                .align(Alignment.TopEnd)
        )
        // Neon purple orb
        Box(
            modifier = Modifier
                .size(150.dp)
                .blur(60.dp)
                .background(
                    NeonPurple.copy(alpha = 0.12f),
                    CircleShape
                )
                .align(Alignment.BottomStart)
        )
    }
}

@Composable
private fun DashboardHeader(
    uiState: DashboardUiState,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onRefresh: () -> Unit
) {
    val dateText = when (uiState.selectedTab) {
        AnalyticsTab.DAILY -> {
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
            uiState.selectedDate.format(formatter)
        }
        AnalyticsTab.WEEKLY -> {
            val startFormatter = DateTimeFormatter.ofPattern("MMM d")
            val endFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
            val weekEnd = uiState.selectedWeekStart.plusDays(6)
            "${uiState.selectedWeekStart.format(startFormatter)} - ${weekEnd.format(endFormatter)}"
        }
        AnalyticsTab.MONTHLY -> {
            uiState.selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                " ${uiState.selectedMonth.year}"
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigatePrevious) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous",
                tint = GlowWhite
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Health Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = GlowWhite
            )
        }
        
        Row {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = GlowWhite
                )
            }
            IconButton(onClick = onNavigateNext) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next",
                    tint = GlowWhite
                )
            }
        }
    }
}

@Composable
private fun GlassmorphicTabRow(
    selectedTab: AnalyticsTab,
    onTabSelected: (AnalyticsTab) -> Unit
) {
    val tabs = AnalyticsTab.entries
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = ElectricBlue,
            indicator = {},
            divider = {}
        ) {
            tabs.forEach { tab ->
                val selected = tab == selectedTab
                Tab(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) ElectricBlue.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                ) {
                    Text(
                        text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (selected) ElectricBlue else GlowWhite,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    glowColor: Color = ElectricBlue,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = glowColor.copy(alpha = 0.3f),
                spotColor = glowColor.copy(alpha = 0.3f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.5f),
                        glowColor.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassSurface
        )
    ) {
        content()
    }
}


@Composable
private fun HealthRingsSection(goalProgress: List<GoalProgress>) {
    Column {
        Text(
            text = "Today's Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(goalProgress) { progress ->
                HealthRingCard(progress)
            }
        }
    }
}

@Composable
private fun HealthRingCard(progress: GoalProgress) {
    val (icon, color, label) = when (progress.goalType) {
        MetricType.STEPS -> Triple(Icons.Default.DirectionsWalk, ElectricBlue, "Steps")
        MetricType.CALORIES -> Triple(Icons.Default.LocalFireDepartment, Color(0xFFFF6B6B), "Calories")
        MetricType.SLEEP -> Triple(Icons.Default.Nightlight, NeonPurple, "Sleep")
        MetricType.HEART_RATE -> Triple(Icons.Default.Favorite, CyberGreen, "Heart Rate")
        else -> Triple(Icons.Default.DirectionsWalk, ElectricBlue, progress.goalType.name)
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = (progress.percentComplete / 100f).coerceIn(0f, 1f),
        animationSpec = tween(1000),
        label = "progress"
    )
    
    GlassmorphicCard(
        modifier = Modifier.width(140.dp),
        glowColor = color
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(80.dp)
            ) {
                // Background ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(80.dp),
                    color = color.copy(alpha = 0.2f),
                    strokeWidth = 8.dp
                )
                // Progress ring
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(80.dp),
                    color = color,
                    strokeWidth = 8.dp
                )
                // Icon
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite
            )
            
            Text(
                text = "${progress.percentComplete.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            
            Text(
                text = formatMetricValue(progress.current, progress.goalType),
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatMetricValue(value: Double, type: MetricType): String {
    return when (type) {
        MetricType.STEPS -> "${value.toInt()} steps"
        MetricType.CALORIES -> "${value.toInt()} kcal"
        MetricType.SLEEP -> "${(value / 60).toInt()}h ${(value % 60).toInt()}m"
        MetricType.SCREEN_TIME -> "${(value / 60).toInt()}h ${(value % 60).toInt()}m"
        MetricType.DISTANCE -> "${String.format("%.1f", value / 1000)} km"
        MetricType.HEART_RATE -> "${value.toInt()} bpm"
        MetricType.HRV -> "${value.toInt()} ms"
        MetricType.MOOD -> value.toInt().toString()
    }
}

@Composable
private fun MetricCardsSection(metrics: HealthMetrics?) {
    if (metrics == null) return
    
    Column {
        Text(
            text = "Health Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.DirectionsWalk,
                label = "Steps",
                value = "${metrics.steps}",
                subValue = "${String.format("%.1f", metrics.distanceMeters / 1000)} km",
                color = ElectricBlue
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalFireDepartment,
                label = "Calories",
                value = "${metrics.caloriesBurned.toInt()}",
                subValue = "kcal burned",
                color = Color(0xFFFF6B6B)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Nightlight,
                label = "Sleep",
                value = "${metrics.sleepDurationMinutes / 60}h ${metrics.sleepDurationMinutes % 60}m",
                subValue = metrics.sleepQuality?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "N/A",
                color = NeonPurple
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                label = "Heart Rate",
                value = if (metrics.heartRateSamples.isNotEmpty()) 
                    "${metrics.heartRateSamples.map { it.bpm }.average().toInt()}" else "N/A",
                subValue = "avg bpm",
                color = CyberGreen
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String,
    color: Color
) {
    GlassmorphicCard(
        modifier = modifier,
        glowColor = color
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlowWhite
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = GlowWhite.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
private fun InsightsSection(insights: List<Insight>) {
    Column {
        Text(
            text = "AI Insights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        insights.forEach { insight ->
            InsightCard(insight)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val (borderColor, bgColor) = when (insight.type) {
        InsightType.ACHIEVEMENT -> Pair(CyberGreen, CyberGreen.copy(alpha = 0.1f))
        InsightType.WARNING -> Pair(Color(0xFFFF6B6B), Color(0xFFFF6B6B).copy(alpha = 0.1f))
        InsightType.SUGGESTION -> Pair(ElectricBlue, ElectricBlue.copy(alpha = 0.1f))
        InsightType.TREND -> Pair(NeonPurple, NeonPurple.copy(alpha = 0.1f))
    }
    
    val priorityIndicator = when (insight.priority) {
        InsightPriority.HIGH -> "🔴"
        InsightPriority.MEDIUM -> "🟡"
        InsightPriority.LOW -> "🟢"
    }
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = borderColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = priorityIndicator,
                fontSize = 20.sp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = borderColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSection(
    suggestions: List<Suggestion>,
    onDismiss: (String) -> Unit,
    onComplete: (String) -> Unit
) {
    Column {
        Text(
            text = "AI Suggestions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        suggestions.forEach { suggestion ->
            SuggestionCard(
                suggestion = suggestion,
                onDismiss = { onDismiss(suggestion.id) },
                onComplete = { onComplete(suggestion.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val (icon, color) = when (suggestion.type) {
        SuggestionType.ACTIVITY -> "🏃" to ElectricBlue
        SuggestionType.SLEEP -> "😴" to NeonPurple
        SuggestionType.NUTRITION -> "🥗" to CyberGreen
        SuggestionType.HYDRATION -> "💧" to Color(0xFF00B4D8)
        SuggestionType.MENTAL_HEALTH -> "🧘" to Color(0xFFE0AAFF)
        SuggestionType.GENERAL -> "💡" to GlowWhite
    }
    
    val priorityColor = when (suggestion.priority) {
        1 -> Color(0xFFFF6B6B)
        2 -> Color(0xFFFFB347)
        3 -> ElectricBlue
        else -> GlowWhite
    }
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = color
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = icon,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = suggestion.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // Priority indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(priorityColor, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodyMedium,
                color = GlowWhite
            )
            
            if (suggestion.actionable) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.3f))
                            .clickable { onComplete() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getActionButtonText(suggestion.action),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                    
                    // Dismiss button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlowWhite.copy(alpha = 0.1f))
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlowWhite.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun getActionButtonText(action: SuggestionAction?): String {
    return when (action) {
        is SuggestionAction.StartWorkout -> "Start Workout"
        is SuggestionAction.LogWater -> "Log Water"
        is SuggestionAction.StartMeditation -> "Start Meditation"
        is SuggestionAction.OpenDietTracker -> "Track Diet"
        is SuggestionAction.SetSleepReminder -> "Set Reminder"
        is SuggestionAction.OpenStepTracker -> "Track Steps"
        null -> "Take Action"
    }
}

@Composable
private fun WeeklyOverviewSection(analytics: WeeklyAnalytics) {
    Column {
        Text(
            text = "Weekly Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                WeeklyStatRow(
                    label = "Avg Steps",
                    value = "${analytics.averages.averageSteps.toInt()}",
                    icon = Icons.Default.DirectionsWalk,
                    color = ElectricBlue
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyStatRow(
                    label = "Avg Calories",
                    value = "${analytics.averages.averageCalories.toInt()} kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyStatRow(
                    label = "Avg Sleep",
                    value = "${(analytics.averages.averageSleepMinutes / 60).toInt()}h ${(analytics.averages.averageSleepMinutes % 60).toInt()}m",
                    icon = Icons.Default.Nightlight,
                    color = NeonPurple
                )
                
                analytics.averages.averageHeartRate?.let { hr ->
                    Spacer(modifier = Modifier.height(12.dp))
                    WeeklyStatRow(
                        label = "Avg Heart Rate",
                        value = "${hr.toInt()} bpm",
                        icon = Icons.Default.Favorite,
                        color = CyberGreen
                    )
                }
            }
        }
        
        // Trend Charts
        if (analytics.trends.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Trends",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            analytics.trends.forEach { trend ->
                val chartColor = when (trend.metricType) {
                    MetricType.STEPS -> ElectricBlue
                    MetricType.CALORIES -> Color(0xFFFF6B6B)
                    MetricType.SLEEP -> NeonPurple
                    MetricType.HEART_RATE -> CyberGreen
                    else -> ElectricBlue
                }
                TrendChart(
                    trendAnalysis = trend,
                    chartColor = chartColor,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        // Daily breakdown
        if (analytics.dailyMetrics.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Daily Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(analytics.dailyMetrics) { metrics ->
                    DailyBreakdownCard(metrics)
                }
            }
        }
        
        // Insights
        if (analytics.insights.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            InsightsSection(analytics.insights)
        }
    }
}

@Composable
private fun WeeklyStatRow(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = GlowWhite
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun DailyBreakdownCard(metrics: HealthMetrics) {
    val dayName = metrics.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    
    GlassmorphicCard(
        modifier = Modifier.width(80.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricBlue,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${metrics.steps / 1000}k",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = "steps",
                style = MaterialTheme.typography.labelSmall,
                color = GlowWhite.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MonthlyOverviewSection(
    averageSteps: Double,
    averageCalories: Double,
    averageSleep: Double
) {
    Column {
        Text(
            text = "Monthly Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                WeeklyStatRow(
                    label = "Monthly Avg Steps",
                    value = "${averageSteps.toInt()}",
                    icon = Icons.Default.DirectionsWalk,
                    color = ElectricBlue
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyStatRow(
                    label = "Monthly Avg Calories",
                    value = "${averageCalories.toInt()} kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    color = Color(0xFFFF6B6B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyStatRow(
                    label = "Monthly Avg Sleep",
                    value = "${(averageSleep / 60).toInt()}h ${(averageSleep % 60).toInt()}m",
                    icon = Icons.Default.Nightlight,
                    color = NeonPurple
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D1A)
@Composable
private fun DashboardScreenPreview() {
    HealthTrackerTheme {
        DashboardContent(
            uiState = DashboardUiState(),
            onTabSelected = {},
            onNavigatePrevious = {},
            onNavigateNext = {},
            onRefresh = {},
            onDismissSuggestion = {},
            onCompleteSuggestion = {}
        )
    }
}
