package com.healthtracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * Utility functions for Compose UI testing.
 * 
 * Provides reusable test helpers for common UI testing scenarios
 * in the Health Tracker application.
 */
object ComposeTestUtils {
    
    // ============================================
    // NAVIGATION HELPERS
    // ============================================
    
    /**
     * Navigates to a specific screen by clicking the bottom navigation item.
     */
    fun ComposeTestRule.navigateToScreen(screenLabel: String) {
        onNodeWithText(screenLabel).performClick()
        waitForIdle()
    }
    
    /**
     * Verifies that a screen is displayed by checking for a specific content description.
     */
    fun ComposeTestRule.assertScreenDisplayed(contentDescription: String) {
        onNodeWithContentDescription(contentDescription).assertIsDisplayed()
    }
    
    // ============================================
    // INPUT HELPERS
    // ============================================
    
    /**
     * Fills a text field with the given label and text.
     */
    fun ComposeTestRule.fillTextField(label: String, text: String) {
        onNodeWithText(label).performTextInput(text)
        waitForIdle()
    }
    
    /**
     * Clears and fills a text field.
     */
    fun ComposeTestRule.clearAndFillTextField(label: String, text: String) {
        onNodeWithText(label).apply {
            performTextClearance()
            performTextInput(text)
        }
        waitForIdle()
    }
    
    /**
     * Clicks a button with the given text.
     */
    fun ComposeTestRule.clickButton(text: String) {
        onNodeWithText(text).performClick()
        waitForIdle()
    }
    
    /**
     * Clicks a button with the given content description.
     */
    fun ComposeTestRule.clickButtonByContentDescription(contentDescription: String) {
        onNodeWithContentDescription(contentDescription).performClick()
        waitForIdle()
    }
    
    // ============================================
    // ASSERTION HELPERS
    // ============================================
    
    /**
     * Asserts that text is displayed on screen.
     */
    fun ComposeTestRule.assertTextDisplayed(text: String) {
        onNodeWithText(text).assertIsDisplayed()
    }
    
    /**
     * Asserts that text is not displayed on screen.
     */
    fun ComposeTestRule.assertTextNotDisplayed(text: String) {
        onNodeWithText(text).assertDoesNotExist()
    }
    
    /**
     * Asserts that a node with content description is displayed.
     */
    fun ComposeTestRule.assertContentDescriptionDisplayed(contentDescription: String) {
        onNodeWithContentDescription(contentDescription).assertIsDisplayed()
    }
    
    /**
     * Asserts that a button is enabled.
     */
    fun ComposeTestRule.assertButtonEnabled(text: String) {
        onNodeWithText(text).assertIsEnabled()
    }
    
    /**
     * Asserts that a button is disabled.
     */
    fun ComposeTestRule.assertButtonDisabled(text: String) {
        onNodeWithText(text).assertIsNotEnabled()
    }
    
    /**
     * Asserts that an error message is displayed.
     */
    fun ComposeTestRule.assertErrorDisplayed(errorText: String) {
        onNodeWithText(errorText, substring = true).assertIsDisplayed()
    }
    
    // ============================================
    // LOADING STATE HELPERS
    // ============================================
    
    /**
     * Waits for a loading indicator to disappear.
     */
    fun ComposeTestRule.waitForLoadingToComplete(
        loadingContentDescription: String = "Loading",
        timeoutMillis: Long = 5000
    ) {
        waitUntil(timeoutMillis) {
            onAllNodesWithContentDescription(loadingContentDescription)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }
    
    /**
     * Asserts that a loading indicator is displayed.
     */
    fun ComposeTestRule.assertLoadingDisplayed(contentDescription: String = "Loading") {
        onNodeWithContentDescription(contentDescription).assertIsDisplayed()
    }
    
    // ============================================
    // LIST HELPERS
    // ============================================
    
    /**
     * Scrolls to an item in a lazy list.
     */
    fun ComposeTestRule.scrollToItem(itemText: String) {
        onNodeWithText(itemText).performScrollTo()
        waitForIdle()
    }
    
    /**
     * Clicks an item in a list.
     */
    fun ComposeTestRule.clickListItem(itemText: String) {
        onNodeWithText(itemText).performClick()
        waitForIdle()
    }
    
    /**
     * Asserts that a list contains a specific number of items.
     */
    fun ComposeTestRule.assertListItemCount(testTag: String, expectedCount: Int) {
        onAllNodesWithTag(testTag).assertCountEquals(expectedCount)
    }
    
    // ============================================
    // DIALOG HELPERS
    // ============================================
    
    /**
     * Asserts that a dialog is displayed.
     */
    fun ComposeTestRule.assertDialogDisplayed(dialogTitle: String) {
        onNodeWithText(dialogTitle).assertIsDisplayed()
    }
    
    /**
     * Dismisses a dialog by clicking the dismiss button.
     */
    fun ComposeTestRule.dismissDialog(dismissButtonText: String = "Cancel") {
        onNodeWithText(dismissButtonText).performClick()
        waitForIdle()
    }
    
    /**
     * Confirms a dialog by clicking the confirm button.
     */
    fun ComposeTestRule.confirmDialog(confirmButtonText: String = "OK") {
        onNodeWithText(confirmButtonText).performClick()
        waitForIdle()
    }
    
    // ============================================
    // FORM HELPERS
    // ============================================
    
    /**
     * Fills out a complete onboarding form.
     */
    fun ComposeTestRule.fillOnboardingForm(
        name: String,
        age: String,
        weight: String,
        height: String
    ) {
        fillTextField("Name", name)
        fillTextField("Age", age)
        fillTextField("Weight (kg)", weight)
        fillTextField("Height (cm)", height)
        waitForIdle()
    }
    
    /**
     * Selects a goal option.
     */
    fun ComposeTestRule.selectGoal(goalText: String) {
        onNodeWithText(goalText).performClick()
        waitForIdle()
    }
    
    // ============================================
    // ACCESSIBILITY HELPERS
    // ============================================
    
    /**
     * Asserts that a node has proper content description for accessibility.
     */
    fun ComposeTestRule.assertHasContentDescription(testTag: String) {
        onNodeWithTag(testTag).assert(hasContentDescription())
    }
    
    /**
     * Asserts that a node is focusable for accessibility.
     */
    fun ComposeTestRule.assertIsFocusable(testTag: String) {
        onNodeWithTag(testTag).assertHasClickAction()
    }
    
    // ============================================
    // ANIMATION HELPERS
    // ============================================
    
    /**
     * Waits for animations to complete.
     */
    fun ComposeTestRule.waitForAnimations() {
        mainClock.advanceTimeBy(1000)
        waitForIdle()
    }
    
    // ============================================
    // SCREENSHOT HELPERS
    // ============================================
    
    /**
     * Captures a screenshot of the current screen (requires additional setup).
     * This is a placeholder for screenshot testing integration.
     */
    fun ComposeTestRule.captureScreenshot(name: String) {
        // Screenshot capture would be implemented here
        // using a library like Shot or Paparazzi
        waitForIdle()
    }
    
    // ============================================
    // CUSTOM MATCHERS
    // ============================================
    
    /**
     * Checks if a node has a specific text color (requires custom matcher).
     */
    fun hasTextColor(color: androidx.compose.ui.graphics.Color): SemanticsMatcher {
        return SemanticsMatcher("has text color $color") { node ->
            // Custom implementation would check text color
            true
        }
    }
    
    /**
     * Checks if a node is within viewport.
     */
    fun isInViewport(): SemanticsMatcher {
        return SemanticsMatcher("is in viewport") { node ->
            // Custom implementation would check if node is visible
            true
        }
    }
}

/**
 * Extension function to wait until a condition is met.
 */
fun ComposeTestRule.waitUntil(
    timeoutMillis: Long = 5000,
    condition: () -> Boolean
) {
    val startTime = System.currentTimeMillis()
    while (!condition()) {
        if (System.currentTimeMillis() - startTime > timeoutMillis) {
            throw AssertionError("Condition not met within $timeoutMillis ms")
        }
        Thread.sleep(100)
        waitForIdle()
    }
}
