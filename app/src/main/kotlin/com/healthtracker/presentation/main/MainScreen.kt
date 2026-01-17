package com.healthtracker.presentation.main

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthtracker.presentation.dashboard.DashboardScreen
import com.healthtracker.presentation.diet.DietTrackingScreen
import com.healthtracker.presentation.gamification.GamificationScreen
import com.healthtracker.presentation.medical.MedicalScreen
import com.healthtracker.presentation.mentalhealth.MentalHealthScreen
import com.healthtracker.presentation.planning.PlanningScreen
import com.healthtracker.presentation.progress.ProgressScreen
import com.healthtracker.presentation.social.SocialScreen
import com.healthtracker.presentation.triage.TriageScreen
import com.healthtracker.presentation.avatar.FloatingAvatarOverlay
import com.healthtracker.presentation.theme.ElectricBlue
import com.healthtracker.presentation.theme.NeonPurple
import com.healthtracker.presentation.theme.CyberGreen
import com.healthtracker.presentation.theme.GlassSurface
import com.healthtracker.service.avatar.AvatarOverlayService

/**
 * Bottom navigation items for the main screen - PRD Features mapped:
 * - Home: F03 (Analytics) + F04 (Alerts) + F05 (AI Suggestions)
 * - Diet: F08 (CV Diet Tracking)
 * - Wellness: F09 (Mental Health)
 * - Social: F11 (Health Circles)
 * - Medical: F12 (Triage) + F13 (Records)
 * 
 * Additional screens accessible via navigation:
 * - Gamification: F10 (Goals & Badges)
 * - Planning: F07 (Workout/Diet/Hydration Plans)
 * - Avatar: F06 (Floating AI Avatar)
 */
sealed class BottomNavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Diet : BottomNavItem("diet", "Diet", Icons.Filled.Restaurant, Icons.Outlined.Restaurant)
    data object Mood : BottomNavItem("mood", "Mood", Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement)
    data object Progress : BottomNavItem("progress", "Progress", Icons.Filled.Insights, Icons.Outlined.Insights)
    data object Social : BottomNavItem("social", "Social", Icons.Filled.People, Icons.Outlined.People)
    data object Medical : BottomNavItem("medical", "Medical", Icons.Filled.LocalHospital, Icons.Outlined.LocalHospital)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Diet,
    BottomNavItem.Mood,
    BottomNavItem.Progress,
    BottomNavItem.Social,
    BottomNavItem.Medical
)

// Extended tabs for more features
enum class ExtendedTab {
    DASHBOARD,      // F03: Analytics
    DIET,           // F08: CV Diet Tracking
    PROGRESS,       // Progress Charts
    SOCIAL,         // F11: Health Circles
    MEDICAL,        // F12+F13: Triage + Records
    GAMIFICATION,   // F10: Goals & Badges
    PLANNING,       // F07: Personalized Plans
    TRIAGE,         // F12: Health Issue Detection
    MENTAL          // F09: Mental Health
}

/**
 * Main screen with bottom navigation containing all PRD features.
 */
@Composable
fun MainScreen(
    onNavigateToAvatar: () -> Unit = {},
    onNavigateToGamification: () -> Unit = {},
    onNavigateToPlanning: () -> Unit = {},
    onNavigateToTriage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentTab by rememberSaveable { mutableIntStateOf(0) } // For extended navigation
    
    // Avatar overlay service state
    var isAvatarServiceRunning by rememberSaveable { 
        mutableStateOf(AvatarOverlayService.hasOverlayPermission(context))
    }
    
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0D0D1A),
            Color(0xFF1A1A2E),
            Color(0xFF16213E)
        )
    )
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            GlassmorphicBottomNav(
                selectedIndex = selectedIndex,
                onItemSelected = { 
                    selectedIndex = it
                    // Map bottom nav to extended tabs
                    currentTab = when(it) {
                        0 -> 0 // Dashboard
                        1 -> 1 // Diet
                        2 -> 8 // Mood (Mental Health)
                        3 -> 2 // Progress
                        4 -> 3 // Social
                        5 -> 4 // Medical
                        else -> 0
                    }
                }
            )
        },
        floatingActionButton = {
            // AI Avatar FAB - Start/Stop overlay service
            FloatingActionButton(
                onClick = { 
                    if (AvatarOverlayService.hasOverlayPermission(context)) {
                        if (isAvatarServiceRunning) {
                            AvatarOverlayService.stop(context)
                            isAvatarServiceRunning = false
                            // Save preference
                            context.getSharedPreferences("avatar_settings", Context.MODE_PRIVATE)
                                .edit().putBoolean("avatar_enabled", false).apply()
                        } else {
                            AvatarOverlayService.start(context)
                            isAvatarServiceRunning = true
                            // Save preference
                            context.getSharedPreferences("avatar_settings", Context.MODE_PRIVATE)
                                .edit().putBoolean("avatar_enabled", true).apply()
                        }
                    } else {
                        // Request overlay permission
                        AvatarOverlayService.requestOverlayPermission(context)
                    }
                },
                containerColor = if (isAvatarServiceRunning) ElectricBlue else Color(0xFF1A1A2E),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "AI Assistant",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            // Content based on selected tab
            when (currentTab) {
                0 -> { // Home - Dashboard with quick access
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Quick Access Row for Gamification & Planning
                        QuickAccessRow(
                            onGamificationClick = { currentTab = 5 },
                            onPlanningClick = { currentTab = 6 },
                            onTriageClick = { currentTab = 7 }
                        )
                        // Main Dashboard
                        DashboardScreen(
                            onNavigateToAnalytics = {},
                            onNavigateToDiet = { selectedIndex = 1; currentTab = 1 },
                            onNavigateToPlanning = { currentTab = 6 },
                            onNavigateToProfile = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                1 -> DietTrackingScreen(onNavigateBack = { selectedIndex = 0; currentTab = 0 })
                2 -> ProgressScreen(onNavigateBack = { selectedIndex = 0; currentTab = 0 }) // Progress Charts
                3 -> SocialScreen(onNavigateToCircleDetail = {})
                4 -> MedicalScreen(onNavigateBack = { selectedIndex = 0; currentTab = 0 })
                5 -> GamificationScreen() // F10: Gamification
                6 -> PlanningScreen()     // F07: Planning
                7 -> TriageScreen(onNavigateBack = { selectedIndex = 0; currentTab = 0 }) // F12: Triage
                8 -> MentalHealthScreen(onNavigateBack = { selectedIndex = 0; currentTab = 0 }) // F09: Mental
            }
        }
    }
}

/**
 * Quick access row for features not in bottom nav
 */
@Composable
private fun QuickAccessRow(
    onGamificationClick: () -> Unit,
    onPlanningClick: () -> Unit,
    onTriageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickAccessCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.EmojiEvents,
            title = "Achievements",
            subtitle = "F10: Gamification",
            color = Color(0xFFFFD700),
            onClick = onGamificationClick
        )
        QuickAccessCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CalendarMonth,
            title = "Plans",
            subtitle = "F07: Workout/Diet",
            color = CyberGreen,
            onClick = onPlanningClick
        )
        QuickAccessCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.MedicalServices,
            title = "Symptoms",
            subtitle = "F12: Triage",
            color = Color(0xFFFF6B6B),
            onClick = onTriageClick
        )
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}


@Composable
private fun GlassmorphicBottomNav(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = GlassSurface,
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (selectedIndex == index) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricBlue,
                    selectedTextColor = ElectricBlue,
                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                    unselectedTextColor = Color.White.copy(alpha = 0.6f),
                    indicatorColor = ElectricBlue.copy(alpha = 0.2f)
                )
            )
        }
    }
}
