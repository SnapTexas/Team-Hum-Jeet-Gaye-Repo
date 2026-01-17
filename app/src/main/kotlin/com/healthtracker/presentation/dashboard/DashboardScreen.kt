package com.healthtracker.presentation.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import com.healthtracker.domain.model.Achievement
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
import com.healthtracker.presentation.theme.NeonPurple
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
    // Clean solid dark background - premium formal look
    val backgroundColor = Color(0xFF0D0D1A)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        
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
                    // TODAY'S ACHIEVEMENTS - TOP SECTION WITH REAL DATA
                    item {
                        TodayAchievementsOverview(
                            steps = uiState.liveStepCount,
                            calories = uiState.liveCalories,
                            distance = uiState.liveDistance,
                            stepGoal = uiState.dailyStepGoal,
                            calorieGoal = uiState.dailyCalorieGoal,
                            weeklyTotalSteps = uiState.weeklyTotalSteps,
                            monthlyTotalSteps = uiState.monthlyTotalSteps,
                            weeklyDaysWithData = uiState.weeklyDaysWithData,
                            monthlyDaysWithData = uiState.monthlyDaysWithData
                        )
                    }
                    
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
                    
                    // Gamification Summary Card - Temporarily disabled for build
                    // uiState.userProgress?.let { progress ->
                    //     item {
                    //         GamificationSummaryCard(
                    //             userProgress = progress,
                    //             streaks = uiState.streaks,
                    //             todayUnlockedCount = uiState.todayAchievements.count { it.badge.isUnlocked }
                    //         )
                    //     }
                    // }
                    
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
                    
                    // Heart Rate Card (from smartwatch or dummy data)
                    item {
                        HeartRateCard(
                            heartRate = if (uiState.liveHeartRate > 0) uiState.liveHeartRate else 72,
                            isLive = uiState.liveHeartRate > 0
                        )
                    }
                    
                    // Achievements Section - Temporarily disabled for build
                    // item {
                    //     AchievementsSection(
                    //         todayAchievements = uiState.todayAchievements,
                    //         userProgress = uiState.userProgress
                    //     )
                    // }
                    
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Weekly Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
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
                    IconStatItem(Icons.Default.DirectionsWalk, "%,d".format(totalSteps), "Total Steps", ElectricBlue)
                    IconStatItem(Icons.Default.TrendingUp, "%,d".format(avgSteps), "Avg/Day", CyberGreen)
                    IconStatItem(Icons.Default.Speed, "${(avgSteps * 100 / stepGoal.coerceAtLeast(1))}%", "Goal", NeonPurple)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconStatItem(Icons.Default.LocalFireDepartment, "%,d".format(totalCalories), "Calories", Color(0xFFFF6B6B))
                    IconStatItem(Icons.Default.LocationOn, String.format("%.1f km", totalDistance / 1000), "Distance", Color(0xFF06B6D4))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monthly Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
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
                    IconStatItem(Icons.Default.DirectionsWalk, "%,d".format(totalSteps), "Total Steps", ElectricBlue)
                    IconStatItem(Icons.Default.TrendingUp, "%,d".format(avgSteps), "Avg/Day", CyberGreen)
                    IconStatItem(Icons.Default.EmojiEvents, "%,d".format(bestDaySteps), "Best Day", Color(0xFFF59E0B))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconStatItem(Icons.Default.LocalFireDepartment, "%,d".format(totalCalories), "Calories", Color(0xFFFF6B6B))
                    IconStatItem(Icons.Default.LocationOn, String.format("%.1f km", totalDistance / 1000), "Distance", Color(0xFF06B6D4))
                }
            }
        }
    }
}

@Composable
private fun IconStatItem(icon: ImageVector, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
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
    
    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "progress"
    )
    
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Steps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "From device sensor • $sensorStatus",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
                Icon(
                    imageVector = Icons.Default.DirectionsWalk,
                    contentDescription = null,
                    tint = progressColor,
                    modifier = Modifier.size(40.dp)
                )
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
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(progressColor)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Calories, distance, and heart rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "$calories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(text = "kcal", style = MaterialTheme.typography.labelSmall, color = GlowWhite)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(24.dp)
                    )
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
 * Heart Rate Card showing live heart rate from smartwatch or dummy data.
 */
@Composable
private fun HeartRateCard(
    heartRate: Int,
    isLive: Boolean
) {
    val heartColor = when {
        heartRate < 60 -> Color(0xFF06B6D4) // Low - Cyan
        heartRate < 100 -> CyberGreen // Normal - Green
        heartRate < 120 -> Color(0xFFF59E0B) // Elevated - Orange
        else -> Color(0xFFFF6B6B) // High - Red
    }
    
    // Animated heart beat effect
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartbeat"
    )
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = heartColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = heartColor,
                            modifier = Modifier
                                .size(24.dp)
                                .scale(heartScale)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Heart Rate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = if (isLive) "From smartwatch • Live" else "Dummy data",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = heartColor,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(heartScale)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Big heart rate display
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$heartRate",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = heartColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.titleLarge,
                    color = GlowWhite,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Heart rate status
            val statusText = when {
                heartRate < 60 -> "Resting"
                heartRate < 100 -> "Normal"
                heartRate < 120 -> "Elevated"
                else -> "High"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = heartColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!isLive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect a smartwatch for live heart rate monitoring",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlowWhite.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Achievements Section - Shows real achievements based on actual step/calorie data
 * Now integrated with gamification system!
 * TEMPORARILY DISABLED FOR BUILD
 */
/*
@Composable
private fun AchievementsSection(
    todayAchievements: List<Achievement>,
    userProgress: UserProgress?
) {
    val unlockedCount = todayAchievements.count { it.badge.unlockedAt != null }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Today's Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$unlockedCount/${todayAchievements.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold
                )
                userProgress?.let { progress ->
                    Text(
                        text = "Level ${progress.level} • ${progress.totalPoints} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(todayAchievements) { index, achievement ->
                val uiAchievement = achievement.toUIAchievement()
                AchievementCard(
                    achievement = uiAchievement,
                    animationDelay = index * 100
                )
            }
        }
    }
}

/**
 * UI Achievement data class for display purposes
 */
private data class UIAchievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val isUnlocked: Boolean,
    val progress: Float, // 0-1
    val pointsAwarded: Int
)

/**
 * Converts domain Achievement to UI Achievement for display
 */
private fun Achievement.toUIAchievement(): UIAchievement {
    val (icon, color) = when {
        badge.name.contains("Steps", ignoreCase = true) -> Icons.Default.DirectionsWalk to Color(0xFF10B981)
        badge.name.contains("1K", ignoreCase = true) -> Icons.Default.DirectionsWalk to ElectricBlue
        badge.name.contains("5K", ignoreCase = true) -> Icons.Default.DirectionsRun to Color(0xFFF59E0B)
        badge.name.contains("10K", ignoreCase = true) -> Icons.Default.EmojiEvents to Color(0xFFFFD700)
        badge.name.contains("Goal", ignoreCase = true) -> Icons.Default.Star to NeonPurple
        badge.name.contains("Calorie", ignoreCase = true) -> Icons.Default.LocalFireDepartment to Color(0xFFFF6B6B)
        badge.name.contains("Fat", ignoreCase = true) -> Icons.Default.LocalFireDepartment to Color(0xFFFF4444)
        badge.name.contains("Inferno", ignoreCase = true) -> Icons.Default.LocalFireDepartment to Color(0xFFFF0000)
        badge.name.contains("KM", ignoreCase = true) -> Icons.Default.LocationOn to Color(0xFF06B6D4)
        else -> Icons.Default.EmojiEvents to Color(0xFFFFD700)
    }
    
    // Calculate progress based on badge requirement
    val progress = if (badge.isUnlocked) 1f else {
        // For now, we'll show 1f for unlocked, 0.5f for in progress
        // In a real implementation, you'd track current values vs thresholds
        0.5f
    }
    
    return UIAchievement(
        id = id,
        title = badge.name,
        description = badge.description,
        icon = icon,
        color = color,
        isUnlocked = badge.isUnlocked,
        progress = progress,
        pointsAwarded = pointsAwarded
    )
}

/**
 * Achievement Card composable
 */
@Composable
private fun AchievementCard(
    achievement: UIAchievement,
    animationDelay: Int = 0
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        isVisible = true
    }
    
    // Pulse animation for unlocked achievements
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (achievement.isUnlocked) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (achievement.isUnlocked) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + scaleIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .scale(if (achievement.isUnlocked) pulseScale else 1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (achievement.isUnlocked) 
                    achievement.color.copy(alpha = 0.15f) 
                else 
                    Color(0xFF1A1A2E)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (achievement.isUnlocked) 8.dp else 2.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon with glow effect
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (achievement.isUnlocked)
                                Brush.radialGradient(
                                    colors = listOf(
                                        achievement.color.copy(alpha = glowAlpha),
                                        achievement.color.copy(alpha = 0.1f)
                                    )
                                )
                            else
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Gray.copy(alpha = 0.3f),
                                        Color.Gray.copy(alpha = 0.1f)
                                    )
                                )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (achievement.isUnlocked) achievement.icon else Icons.Default.Lock,
                        contentDescription = achievement.title,
                        tint = if (achievement.isUnlocked) achievement.color else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (achievement.isUnlocked) Color.White else Color.Gray,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (achievement.isUnlocked) GlowWhite else Color.Gray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = achievement.progress,
                        animationSpec = tween(1000, delayMillis = animationDelay),
                        label = "progress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (achievement.isUnlocked) achievement.color
                                else Color.Gray
                            )
                    )
                }
                
                // Progress text with points
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (achievement.isUnlocked) "✓ Unlocked" else "${(achievement.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (achievement.isUnlocked) achievement.color else Color.Gray,
                        fontWeight = if (achievement.isUnlocked) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp
                    )
                    if (achievement.isUnlocked && achievement.pointsAwarded > 0) {
                        Text(
                            text = "+${achievement.pointsAwarded} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}
*/

/**
 * Gamification Summary Card showing level, points, and active streaks
 * TEMPORARILY DISABLED FOR BUILD
 */
/*
@Composable
private fun GamificationSummaryCard(
    userProgress: UserProgress,
    streaks: List<Streak>,
    todayUnlockedCount: Int
) {
    val activeStreaks = streaks.filter { it.isActive }
    val longestStreak = streaks.maxByOrNull { it.currentCount }
    
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Level badge
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NeonPurple.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "Level ${userProgress.level}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Total Points
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${userProgress.totalPoints}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Total Points",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
                
                // Active Streaks
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${activeStreaks.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                    Text(
                        text = "Active Streaks",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
                
                // Today's Achievements
                Column(horizontalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$todayUnlockedCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                    Text(
                        text = "Today's Wins",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                }
            }
            
            // Level progress bar
            if (userProgress.level < 50) { // Show progress bar until max level
                Spacer(modifier = Modifier.height(12.dp))
                
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Level Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlowWhite
                        )
                        Text(
                            text = "${userProgress.pointsToNextLevel} pts to next level",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { userProgress.levelProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonPurple,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
            
            // Longest streak highlight
            longestStreak?.let { streak ->
                if (streak.longestCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Best streak: ${streak.longestCount} days",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
*/

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hello, ${userName.split(" ").first()}!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                IconGoalChip(Icons.Default.LocalFireDepartment, "$calorieGoal", "kcal", Color(0xFFFF6B6B))
                IconGoalChip(Icons.Default.DirectionsWalk, "${stepGoal / 1000}k", "steps", ElectricBlue)
                IconGoalChip(Icons.Default.WaterDrop, String.format("%.1fL", waterGoal / 1000f), "water", Color(0xFF06B6D4))
            }
        }
    }
}

@Composable
private fun IconGoalChip(icon: ImageVector, value: String, label: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
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
    // Removed - clean formal look
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
    // Premium formal card - clean solid look
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

/**
 * TODAY'S ACHIEVEMENTS OVERVIEW - Shows real-time progress, streaks, badges, leaderboard
 */
@Composable
private fun TodayAchievementsOverview(
    steps: Int,
    calories: Int,
    distance: Double,
    stepGoal: Int,
    calorieGoal: Int,
    weeklyTotalSteps: Int,
    monthlyTotalSteps: Int,
    weeklyDaysWithData: Int,
    monthlyDaysWithData: Int
) {
    val stepsProgress = (steps.toFloat() / stepGoal).coerceIn(0f, 1f)
    val caloriesProgress = (calories.toFloat() / calorieGoal).coerceIn(0f, 1f)
    
    // Calculate streaks
    val currentStreak = weeklyDaysWithData
    val bestStreak = monthlyDaysWithData
    
    // Calculate badges unlocked
    val badgesUnlocked = remember(steps, calories, distance) {
        var count = 0
        if (steps >= 100) count++
        if (steps >= 1000) count++
        if (steps >= 5000) count++
        if (steps >= 10000) count++
        if (calories >= 50) count++
        if (calories >= 100) count++
        if (distance >= 1000) count++
        count
    }
    
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonPurple
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Achievements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 4 Main Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Progress
                AchievementStatBox(
                    icon = Icons.Default.TrendingUp,
                    label = "Progress",
                    value = "${(stepsProgress * 100).toInt()}%",
                    color = ElectricBlue,
                    modifier = Modifier.weight(1f)
                )
                
                // Streaks
                AchievementStatBox(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Streak",
                    value = "$currentStreak days",
                    color = Color(0xFFFF6B6B),
                    modifier = Modifier.weight(1f)
                )
                
                // Badges
                AchievementStatBox(
                    icon = Icons.Default.Star,
                    label = "Badges",
                    value = "$badgesUnlocked",
                    color = Color(0xFFFFD700),
                    modifier = Modifier.weight(1f)
                )
                
                // Leaderboard
                AchievementStatBox(
                    icon = Icons.Default.EmojiEvents,
                    label = "Rank",
                    value = "#1",
                    color = CyberGreen,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Detailed Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Steps Progress
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "%,d".format(steps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                    Text(
                        text = "/ %,d steps".format(stepGoal),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(stepsProgress)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ElectricBlue)
                        )
                    }
                }
                
                // Calories Progress
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "%,d".format(calories),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B)
                    )
                    Text(
                        text = "/ %,d kcal".format(calorieGoal),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(caloriesProgress)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFFF6B6B))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Weekly & Monthly Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weekly
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Weekly",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%,d".format(weeklyTotalSteps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberGreen
                    )
                    Text(
                        text = "steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite.copy(alpha = 0.7f)
                    )
                }
                
                // Monthly
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Monthly",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%,d".format(monthlyTotalSteps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple
                    )
                    Text(
                        text = "steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlowWhite.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Individual Achievement Stat Box
 */
@Composable
private fun AchievementStatBox(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = GlowWhite,
            textAlign = TextAlign.Center
        )
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
