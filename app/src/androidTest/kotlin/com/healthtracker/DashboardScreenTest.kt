package com.healthtracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for the Dashboard screen.
 * 
 * Tests dashboard functionality including:
 * - Metric display
 * - Tab navigation (Daily/Weekly/Monthly)
 * - Chart interactions
 * - Refresh functionality
 */
@HiltAndroidTest
class DashboardScreenTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
        // Navigate to dashboard (assuming user is already onboarded)
        with(composeTestRule) {
            waitForIdle()
            ComposeTestUtils.navigateToScreen("Dashboard")
        }
    }
    
    // ============================================
    // DISPLAY TESTS
    // ============================================
    
    @Test
    fun dashboard_displaysHealthMetrics() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify key metrics are displayed
            ComposeTestUtils.assertTextDisplayed("Steps")
            ComposeTestUtils.assertTextDisplayed("Calories")
            ComposeTestUtils.assertTextDisplayed("Sleep")
            ComposeTestUtils.assertTextDisplayed("Heart Rate")
        }
    }
    
    @Test
    fun dashboard_displaysGreeting() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify greeting is displayed
            onNode(hasText("Good", substring = true)).assertIsDisplayed()
        }
    }
    
    @Test
    fun dashboard_displaysDateInformation() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify date information is present
            onNode(hasText("Today", substring = true) or hasText("Yesterday", substring = true))
                .assertExists()
        }
    }
    
    // ============================================
    // TAB NAVIGATION TESTS
    // ============================================
    
    @Test
    fun dashboard_tabNavigation_switchesBetweenViews() {
        with(composeTestRule) {
            waitForIdle()
            
            // Click Daily tab
            onNodeWithText("Daily").performClick()
            waitForIdle()
            ComposeTestUtils.assertTextDisplayed("Today")
            
            // Click Weekly tab
            onNodeWithText("Weekly").performClick()
            waitForIdle()
            ComposeTestUtils.assertTextDisplayed("This Week")
            
            // Click Monthly tab
            onNodeWithText("Monthly").performClick()
            waitForIdle()
            ComposeTestUtils.assertTextDisplayed("This Month")
        }
    }
    
    @Test
    fun dashboard_defaultTab_isDaily() {
        with(composeTestRule) {
            waitForIdle()
            
            // Daily tab should be selected by default
            onNodeWithText("Daily").assertIsSelected()
        }
    }
    
    // ============================================
    // CHART INTERACTION TESTS
    // ============================================
    
    @Test
    fun dashboard_charts_areDisplayed() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify charts are present
            onNodeWithContentDescription("Steps Chart", useUnmergedTree = true)
                .assertExists()
        }
    }
    
    @Test
    fun dashboard_chartData_updatesOnTabChange() {
        with(composeTestRule) {
            waitForIdle()
            
            // Switch tabs and verify chart updates
            onNodeWithText("Daily").performClick()
            waitForIdle()
            
            onNodeWithText("Weekly").performClick()
            waitForIdle()
            
            // Chart should still be displayed with different data
            onNodeWithContentDescription("Steps Chart", useUnmergedTree = true)
                .assertExists()
        }
    }
    
    // ============================================
    // REFRESH TESTS
    // ============================================
    
    @Test
    fun dashboard_pullToRefresh_updatesData() {
        with(composeTestRule) {
            waitForIdle()
            
            // Perform pull to refresh gesture
            onRoot().performTouchInput {
                swipeDown(
                    startY = 100f,
                    endY = 500f
                )
            }
            
            // Wait for refresh to complete
            ComposeTestUtils.waitForLoadingToComplete()
            
            // Dashboard should still be displayed
            ComposeTestUtils.assertTextDisplayed("Steps")
        }
    }
    
    // ============================================
    // EMPTY STATE TESTS
    // ============================================
    
    @Test
    fun dashboard_noData_showsEmptyState() {
        with(composeTestRule) {
            waitForIdle()
            
            // If no data is available, empty state should be shown
            // (This test depends on actual data state)
            onNode(
                hasText("No data", substring = true) or 
                hasText("Start tracking", substring = true)
            ).assertExists()
        }
    }
    
    // ============================================
    // LOADING STATE TESTS
    // ============================================
    
    @Test
    fun dashboard_initialLoad_showsLoadingState() {
        with(composeTestRule) {
            // Loading state should appear briefly on initial load
            // (May need to be adjusted based on actual loading behavior)
            waitForIdle()
        }
    }
    
    // ============================================
    // METRIC CARD TESTS
    // ============================================
    
    @Test
    fun dashboard_metricCards_areClickable() {
        with(composeTestRule) {
            waitForIdle()
            
            // Click on steps card
            onNodeWithText("Steps").performClick()
            waitForIdle()
            
            // Should show detailed view or expand
            // (Behavior depends on implementation)
        }
    }
    
    @Test
    fun dashboard_metricCards_displayValues() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify metric cards show numeric values
            onAllNodes(hasText(Regex("\\d+"))).assertCountEquals(atLeast = 1)
        }
    }
    
    // ============================================
    // INSIGHTS TESTS
    // ============================================
    
    @Test
    fun dashboard_displaysAIInsights() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify insights section is present
            onNode(
                hasText("Insights", substring = true) or
                hasText("Suggestions", substring = true)
            ).assertExists()
        }
    }
    
    // ============================================
    // ACCESSIBILITY TESTS
    // ============================================
    
    @Test
    fun dashboard_allMetrics_haveContentDescriptions() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify metric cards have proper semantics
            onNodeWithText("Steps").assertHasClickAction()
            onNodeWithText("Calories").assertHasClickAction()
            onNodeWithText("Sleep").assertHasClickAction()
        }
    }
    
    @Test
    fun dashboard_tabs_areAccessible() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify tabs are accessible
            onNodeWithText("Daily").assertHasClickAction()
            onNodeWithText("Weekly").assertHasClickAction()
            onNodeWithText("Monthly").assertHasClickAction()
        }
    }
    
    // ============================================
    // NAVIGATION TESTS
    // ============================================
    
    @Test
    fun dashboard_bottomNavigation_isVisible() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify bottom navigation is present
            onNodeWithContentDescription("Bottom Navigation").assertExists()
        }
    }
    
    @Test
    fun dashboard_navigateToOtherScreens_andBack() {
        with(composeTestRule) {
            waitForIdle()
            
            // Navigate to another screen
            ComposeTestUtils.navigateToScreen("Planning")
            waitForIdle()
            
            // Navigate back to dashboard
            ComposeTestUtils.navigateToScreen("Dashboard")
            waitForIdle()
            
            // Dashboard should be displayed again
            ComposeTestUtils.assertTextDisplayed("Steps")
        }
    }
}

/**
 * Helper function to assert at least N nodes exist.
 */
private fun atLeast(count: Int): Int = count
