package com.healthtracker.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.healthtracker.presentation.avatar.AvatarScreen
import com.healthtracker.presentation.gamification.GamificationScreen
import com.healthtracker.presentation.main.MainScreen
import com.healthtracker.presentation.onboarding.OnboardingScreen
import com.healthtracker.presentation.planning.PlanningScreen
import com.healthtracker.presentation.social.CircleDetailScreen
import com.healthtracker.presentation.splash.SplashScreen
import com.healthtracker.presentation.triage.TriageScreen

/**
 * Navigation routes for the Health Tracker app.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val AVATAR = "avatar"
    const val GAMIFICATION = "gamification"
    const val PLANNING = "planning"
    const val TRIAGE = "triage"
    const val CIRCLE_DETAIL = "circle_detail/{circleId}"
    
    fun circleDetail(circleId: String) = "circle_detail/$circleId"
}

/**
 * Main navigation host for the Health Tracker app.
 * 
 * Navigation flow:
 * 1. Splash -> checks if user completed onboarding
 * 2. Onboarding (F01) -> collects user info
 * 3. Main -> bottom nav with all features:
 *    - Home/Dashboard (F03: Analytics)
 *    - Diet (F08: CV Diet Tracking)
 *    - Mental Health (F09: Stress Management)
 *    - Social (F11: Health Circles)
 *    - Medical (F12: Triage + F13: Records)
 * 4. Additional screens accessible from Main:
 *    - Avatar (F06: Floating AI Avatar)
 *    - Gamification (F10: Goals & Badges)
 *    - Planning (F07: Personalized Plans)
 */
@Composable
fun HealthTrackerNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.SPLASH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Splash screen - safe startup
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        
        // Onboarding flow (F01: User Onboarding)
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        
        // Main screen with bottom navigation
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToAvatar = {
                    navController.navigate(Routes.AVATAR)
                },
                onNavigateToGamification = {
                    navController.navigate(Routes.GAMIFICATION)
                },
                onNavigateToPlanning = {
                    navController.navigate(Routes.PLANNING)
                },
                onNavigateToTriage = {
                    navController.navigate(Routes.TRIAGE)
                }
            )
        }
        
        // AI Avatar screen (F06: Floating AI Avatar)
        composable(Routes.AVATAR) {
            AvatarScreen(
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }
        
        // Gamification screen (F10: Gamification & Goals)
        composable(Routes.GAMIFICATION) {
            GamificationScreen()
        }
        
        // Planning screen (F07: Personalized Planning)
        composable(Routes.PLANNING) {
            PlanningScreen()
        }
        
        // Triage screen (F12: Health Issue Detection)
        composable(Routes.TRIAGE) {
            TriageScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // Circle detail screen (F11: Social Challenges)
        composable(
            route = Routes.CIRCLE_DETAIL,
            arguments = listOf(navArgument("circleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val circleId = backStackEntry.arguments?.getString("circleId") ?: return@composable
            CircleDetailScreen(
                circleId = circleId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
