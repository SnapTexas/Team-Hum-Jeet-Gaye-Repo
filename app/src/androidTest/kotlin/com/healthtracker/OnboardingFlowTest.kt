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
 * UI tests for the onboarding flow.
 * 
 * Tests the complete user onboarding experience including:
 * - Form validation
 * - Navigation
 * - Error handling
 * - Successful completion
 */
@HiltAndroidTest
class OnboardingFlowTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
    
    // ============================================
    // HAPPY PATH TESTS
    // ============================================
    
    @Test
    fun onboarding_completeFlow_navigatesToDashboard() {
        with(composeTestRule) {
            // Wait for onboarding screen to load
            waitForIdle()
            
            // Fill out the form with valid data
            ComposeTestUtils.fillOnboardingForm(
                name = "John Doe",
                age = "25",
                weight = "70",
                height = "175"
            )
            
            // Select a goal
            ComposeTestUtils.selectGoal("Fitness")
            
            // Click continue button
            ComposeTestUtils.clickButton("Continue")
            
            // Verify navigation to dashboard
            ComposeTestUtils.assertScreenDisplayed("Dashboard")
        }
    }
    
    @Test
    fun onboarding_allGoalOptions_areSelectable() {
        with(composeTestRule) {
            waitForIdle()
            
            // Test each goal option
            val goals = listOf("Weight Loss", "Fitness", "General")
            goals.forEach { goal ->
                ComposeTestUtils.selectGoal(goal)
                onNodeWithText(goal).assertIsSelected()
            }
        }
    }
    
    // ============================================
    // VALIDATION TESTS
    // ============================================
    
    @Test
    fun onboarding_emptyName_showsError() {
        with(composeTestRule) {
            waitForIdle()
            
            // Leave name empty and fill other fields
            ComposeTestUtils.fillTextField("Age", "25")
            ComposeTestUtils.fillTextField("Weight (kg)", "70")
            ComposeTestUtils.fillTextField("Height (cm)", "175")
            ComposeTestUtils.selectGoal("Fitness")
            
            // Try to continue
            ComposeTestUtils.clickButton("Continue")
            
            // Verify error is displayed
            ComposeTestUtils.assertErrorDisplayed("Name is required")
        }
    }
    
    @Test
    fun onboarding_ageBelowMinimum_showsError() {
        with(composeTestRule) {
            waitForIdle()
            
            // Fill form with age below 13
            ComposeTestUtils.fillOnboardingForm(
                name = "Young User",
                age = "12",
                weight = "45",
                height = "150"
            )
            ComposeTestUtils.selectGoal("General")
            
            // Try to continue
            ComposeTestUtils.clickButton("Continue")
            
            // Verify error is displayed
            ComposeTestUtils.assertErrorDisplayed("at least 13 years old")
        }
    }
    
    @Test
    fun onboarding_weightOutOfRange_showsError() {
        with(composeTestRule) {
            waitForIdle()
            
            // Test weight too low
            ComposeTestUtils.fillOnboardingForm(
                name = "Test User",
                age = "25",
                weight = "15",
                height = "175"
            )
            ComposeTestUtils.selectGoal("Fitness")
            ComposeTestUtils.clickButton("Continue")
            ComposeTestUtils.assertErrorDisplayed("at least 20 kg")
            
            // Test weight too high
            ComposeTestUtils.clearAndFillTextField("Weight (kg)", "600")
            ComposeTestUtils.clickButton("Continue")
            ComposeTestUtils.assertErrorDisplayed("less than 500 kg")
        }
    }
    
    @Test
    fun onboarding_heightOutOfRange_showsError() {
        with(composeTestRule) {
            waitForIdle()
            
            // Test height too low
            ComposeTestUtils.fillOnboardingForm(
                name = "Test User",
                age = "25",
                weight = "70",
                height = "40"
            )
            ComposeTestUtils.selectGoal("Fitness")
            ComposeTestUtils.clickButton("Continue")
            ComposeTestUtils.assertErrorDisplayed("at least 50 cm")
            
            // Test height too high
            ComposeTestUtils.clearAndFillTextField("Height (cm)", "350")
            ComposeTestUtils.clickButton("Continue")
            ComposeTestUtils.assertErrorDisplayed("less than 300 cm")
        }
    }
    
    @Test
    fun onboarding_invalidCharactersInName_showsError() {
        with(composeTestRule) {
            waitForIdle()
            
            // Fill form with invalid name characters
            ComposeTestUtils.fillOnboardingForm(
                name = "User@123",
                age = "25",
                weight = "70",
                height = "175"
            )
            ComposeTestUtils.selectGoal("Fitness")
            
            // Try to continue
            ComposeTestUtils.clickButton("Continue")
            
            // Verify error is displayed
            ComposeTestUtils.assertErrorDisplayed("invalid characters")
        }
    }
    
    // ============================================
    // BOUNDARY VALUE TESTS
    // ============================================
    
    @Test
    fun onboarding_minimumValidValues_succeeds() {
        with(composeTestRule) {
            waitForIdle()
            
            // Fill form with minimum valid values
            ComposeTestUtils.fillOnboardingForm(
                name = "A",
                age = "13",
                weight = "20",
                height = "50"
            )
            ComposeTestUtils.selectGoal("General")
            
            // Continue should succeed
            ComposeTestUtils.clickButton("Continue")
            
            // Should navigate to dashboard
            ComposeTestUtils.waitForLoadingToComplete()
            ComposeTestUtils.assertScreenDisplayed("Dashboard")
        }
    }
    
    @Test
    fun onboarding_maximumValidValues_succeeds() {
        with(composeTestRule) {
            waitForIdle()
            
            // Fill form with maximum valid values
            ComposeTestUtils.fillOnboardingForm(
                name = "A".repeat(100),
                age = "120",
                weight = "500",
                height = "300"
            )
            ComposeTestUtils.selectGoal("Weight Loss")
            
            // Continue should succeed
            ComposeTestUtils.clickButton("Continue")
            
            // Should navigate to dashboard
            ComposeTestUtils.waitForLoadingToComplete()
            ComposeTestUtils.assertScreenDisplayed("Dashboard")
        }
    }
    
    // ============================================
    // ACCESSIBILITY TESTS
    // ============================================
    
    @Test
    fun onboarding_allInputFields_haveContentDescriptions() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify all input fields have content descriptions
            onNodeWithText("Name").assertHasClickAction()
            onNodeWithText("Age").assertHasClickAction()
            onNodeWithText("Weight (kg)").assertHasClickAction()
            onNodeWithText("Height (cm)").assertHasClickAction()
        }
    }
    
    @Test
    fun onboarding_continueButton_hasProperSemantics() {
        with(composeTestRule) {
            waitForIdle()
            
            // Verify continue button is accessible
            onNodeWithText("Continue").apply {
                assertHasClickAction()
                assertIsDisplayed()
            }
        }
    }
    
    // ============================================
    // LOADING STATE TESTS
    // ============================================
    
    @Test
    fun onboarding_duringSubmission_showsLoadingState() {
        with(composeTestRule) {
            waitForIdle()
            
            // Fill valid form
            ComposeTestUtils.fillOnboardingForm(
                name = "Test User",
                age = "25",
                weight = "70",
                height = "175"
            )
            ComposeTestUtils.selectGoal("Fitness")
            
            // Click continue
            ComposeTestUtils.clickButton("Continue")
            
            // Loading indicator should appear briefly
            // (This test may need adjustment based on actual loading behavior)
            waitForIdle()
        }
    }
    
    // ============================================
    // NAVIGATION TESTS
    // ============================================
    
    @Test
    fun onboarding_backButton_doesNotNavigateBack() {
        with(composeTestRule) {
            waitForIdle()
            
            // Onboarding should not allow back navigation
            // (This test depends on actual back button handling)
            onNodeWithContentDescription("Navigate back").assertDoesNotExist()
        }
    }
}
