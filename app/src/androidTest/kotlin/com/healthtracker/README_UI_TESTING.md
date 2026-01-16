# Compose UI Testing Guide

## Overview

This directory contains UI tests for the Smart Health Tracker application using Jetpack Compose Testing framework. These tests verify user interactions, navigation flows, and UI behavior across the application.

## Test Setup

### Dependencies

The following dependencies are configured in `app/build.gradle.kts`:

```kotlin
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.mockk.android)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

### Hilt Integration

All UI tests use `@HiltAndroidTest` annotation for dependency injection:

```kotlin
@HiltAndroidTest
class MyScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
    }
}
```

## Running Tests

### Command Line

```bash
# Run all UI tests
./gradlew connectedAndroidTest

# Run tests for a specific variant
./gradlew connectedDebugAndroidTest

# Run a specific test class
./gradlew connectedAndroidTest --tests "com.healthtracker.OnboardingFlowTest"

# Run a specific test method
./gradlew connectedAndroidTest --tests "com.healthtracker.OnboardingFlowTest.onboarding_completeFlow_navigatesToDashboard"
```

### Android Studio

1. Right-click on test file or method
2. Select "Run 'TestName'"
3. Choose device/emulator
4. View results in Run window

### Firebase Test Lab

```bash
# Upload APK to Firebase Test Lab
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel2,version=28,locale=en,orientation=portrait
```

## Test Utilities

### ComposeTestUtils

The `ComposeTestUtils` object provides reusable helper functions for common UI testing scenarios:

#### Navigation
```kotlin
ComposeTestUtils.navigateToScreen("Dashboard")
ComposeTestUtils.assertScreenDisplayed("Dashboard")
```

#### Input
```kotlin
ComposeTestUtils.fillTextField("Name", "John Doe")
ComposeTestUtils.clearAndFillTextField("Age", "25")
ComposeTestUtils.clickButton("Continue")
ComposeTestUtils.clickButtonByContentDescription("Submit")
```

#### Assertions
```kotlin
ComposeTestUtils.assertTextDisplayed("Welcome")
ComposeTestUtils.assertTextNotDisplayed("Error")
ComposeTestUtils.assertButtonEnabled("Submit")
ComposeTestUtils.assertButtonDisabled("Continue")
ComposeTestUtils.assertErrorDisplayed("Invalid input")
```

#### Loading States
```kotlin
ComposeTestUtils.waitForLoadingToComplete()
ComposeTestUtils.assertLoadingDisplayed()
```

#### Lists
```kotlin
ComposeTestUtils.scrollToItem("Item 5")
ComposeTestUtils.clickListItem("Item 1")
ComposeTestUtils.assertListItemCount("list-tag", 10)
```

#### Dialogs
```kotlin
ComposeTestUtils.assertDialogDisplayed("Confirm Action")
ComposeTestUtils.confirmDialog("OK")
ComposeTestUtils.dismissDialog("Cancel")
```

#### Forms
```kotlin
ComposeTestUtils.fillOnboardingForm(
    name = "John Doe",
    age = "25",
    weight = "70",
    height = "175"
)
ComposeTestUtils.selectGoal("Fitness")
```

## Writing UI Tests

### Test Structure

```kotlin
@HiltAndroidTest
class MyFeatureTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    fun setup() {
        hiltRule.inject()
        // Additional setup
    }
    
    @Test
    fun featureName_scenario_expectedBehavior() {
        with(composeTestRule) {
            // Arrange
            waitForIdle()
            
            // Act
            ComposeTestUtils.clickButton("Action")
            
            // Assert
            ComposeTestUtils.assertTextDisplayed("Result")
        }
    }
}
```

### Test Naming Convention

Use the pattern: `featureName_scenario_expectedBehavior`

Examples:
- `onboarding_completeFlow_navigatesToDashboard`
- `dashboard_pullToRefresh_updatesData`
- `login_invalidCredentials_showsError`

### Test Categories

Organize tests into logical groups:

```kotlin
// ============================================
// HAPPY PATH TESTS
// ============================================

@Test
fun feature_validInput_succeeds() { }

// ============================================
// VALIDATION TESTS
// ============================================

@Test
fun feature_invalidInput_showsError() { }

// ============================================
// EDGE CASE TESTS
// ============================================

@Test
fun feature_boundaryValue_handlesCorrectly() { }

// ============================================
// ACCESSIBILITY TESTS
// ============================================

@Test
fun feature_allElements_haveContentDescriptions() { }
```

## Common Test Patterns

### Testing Navigation

```kotlin
@Test
fun navigation_betweenScreens_works() {
    with(composeTestRule) {
        // Navigate to screen
        ComposeTestUtils.navigateToScreen("Profile")
        
        // Verify screen is displayed
        ComposeTestUtils.assertScreenDisplayed("Profile")
        
        // Navigate back
        ComposeTestUtils.navigateToScreen("Dashboard")
        ComposeTestUtils.assertScreenDisplayed("Dashboard")
    }
}
```

### Testing Form Validation

```kotlin
@Test
fun form_invalidInput_showsError() {
    with(composeTestRule) {
        // Fill form with invalid data
        ComposeTestUtils.fillTextField("Email", "invalid-email")
        
        // Submit form
        ComposeTestUtils.clickButton("Submit")
        
        // Verify error is displayed
        ComposeTestUtils.assertErrorDisplayed("Invalid email")
    }
}
```

### Testing Loading States

```kotlin
@Test
fun feature_duringLoad_showsLoadingIndicator() {
    with(composeTestRule) {
        // Trigger action that loads data
        ComposeTestUtils.clickButton("Refresh")
        
        // Verify loading indicator appears
        ComposeTestUtils.assertLoadingDisplayed()
        
        // Wait for loading to complete
        ComposeTestUtils.waitForLoadingToComplete()
        
        // Verify content is displayed
        ComposeTestUtils.assertTextDisplayed("Data loaded")
    }
}
```

### Testing Lists

```kotlin
@Test
fun list_scrollAndClick_works() {
    with(composeTestRule) {
        // Scroll to item
        ComposeTestUtils.scrollToItem("Item 50")
        
        // Click item
        ComposeTestUtils.clickListItem("Item 50")
        
        // Verify detail screen
        ComposeTestUtils.assertTextDisplayed("Item 50 Details")
    }
}
```

### Testing Dialogs

```kotlin
@Test
fun dialog_confirmAction_executesAction() {
    with(composeTestRule) {
        // Trigger dialog
        ComposeTestUtils.clickButton("Delete")
        
        // Verify dialog is displayed
        ComposeTestUtils.assertDialogDisplayed("Confirm Delete")
        
        // Confirm action
        ComposeTestUtils.confirmDialog("Delete")
        
        // Verify action was executed
        ComposeTestUtils.assertTextDisplayed("Deleted successfully")
    }
}
```

## Compose Test Finders

### Finding Nodes

```kotlin
// By text
onNodeWithText("Button Text")

// By content description
onNodeWithContentDescription("Icon description")

// By test tag
onNodeWithTag("my-test-tag")

// By semantic property
onNode(hasText("Text") and isEnabled())

// Multiple nodes
onAllNodesWithText("Item")
```

### Semantic Matchers

```kotlin
hasText("Text")
hasContentDescription("Description")
hasClickAction()
isEnabled()
isSelected()
isFocused()
isDisplayed()
```

## Compose Test Actions

```kotlin
// Click
performClick()

// Text input
performTextInput("Text")
performTextClearance()
performTextReplacement("New text")

// Gestures
performTouchInput {
    swipeUp()
    swipeDown()
    swipeLeft()
    swipeRight()
}

// Scroll
performScrollTo()
performScrollToIndex(5)
```

## Compose Test Assertions

```kotlin
// Existence
assertExists()
assertDoesNotExist()

// Display
assertIsDisplayed()
assertIsNotDisplayed()

// State
assertIsEnabled()
assertIsNotEnabled()
assertIsSelected()
assertIsNotSelected()

// Text
assertTextEquals("Expected")
assertTextContains("Partial")

// Count
assertCountEquals(5)
```

## Accessibility Testing

### Content Descriptions

```kotlin
@Test
fun allInteractiveElements_haveContentDescriptions() {
    with(composeTestRule) {
        // Verify buttons have content descriptions
        onNodeWithText("Submit").assertHasClickAction()
        
        // Verify images have content descriptions
        onNodeWithContentDescription("Profile picture").assertExists()
    }
}
```

### Minimum Touch Target Size

```kotlin
@Test
fun allButtons_meetMinimumTouchTargetSize() {
    with(composeTestRule) {
        // Verify touch targets are at least 48dp
        onNodeWithText("Button").assertHeightIsAtLeast(48.dp)
    }
}
```

## Mocking Dependencies

### Using MockK

```kotlin
@HiltAndroidTest
class MyScreenTest {
    
    @MockK
    lateinit var repository: MyRepository
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        
        // Setup mock behavior
        coEvery { repository.getData() } returns flowOf(testData)
    }
}
```

## Screenshot Testing

### Capturing Screenshots

```kotlin
@Test
fun screen_appearance_matchesDesign() {
    with(composeTestRule) {
        // Navigate to screen
        ComposeTestUtils.navigateToScreen("Profile")
        
        // Capture screenshot
        ComposeTestUtils.captureScreenshot("profile_screen")
        
        // Screenshot comparison would be done by external tool
    }
}
```

## Performance Testing

### Measuring Render Time

```kotlin
@Test
fun screen_rendersQuickly() {
    with(composeTestRule) {
        val startTime = System.currentTimeMillis()
        
        // Trigger screen render
        ComposeTestUtils.navigateToScreen("Dashboard")
        waitForIdle()
        
        val renderTime = System.currentTimeMillis() - startTime
        
        // Assert render time is acceptable
        assert(renderTime < 1000) { "Screen took too long to render: $renderTime ms" }
    }
}
```

## Troubleshooting

### Test Flakiness

1. **Add explicit waits**: Use `waitForIdle()` after actions
2. **Use semantic matchers**: Prefer semantic properties over pixel-based checks
3. **Disable animations**: Set animation scale to 0 in developer options
4. **Increase timeouts**: Adjust timeout values for slow operations

### Node Not Found

```kotlin
// Use substring matching
onNodeWithText("Text", substring = true)

// Use unmerged tree
onNodeWithText("Text", useUnmergedTree = true)

// Print semantic tree for debugging
onRoot().printToLog("TAG")
```

### Timing Issues

```kotlin
// Wait for specific condition
ComposeTestUtils.waitUntil(timeoutMillis = 5000) {
    onAllNodesWithText("Item").fetchSemanticsNodes().isNotEmpty()
}

// Advance clock for animations
mainClock.advanceTimeBy(1000)
```

## Best Practices

1. **Test user flows, not implementation details**
2. **Use semantic properties over test tags when possible**
3. **Keep tests independent and isolated**
4. **Use descriptive test names**
5. **Group related tests with context blocks**
6. **Test accessibility alongside functionality**
7. **Mock external dependencies**
8. **Use ComposeTestUtils for common operations**
9. **Add comments for complex test scenarios**
10. **Run tests on multiple device configurations**

## Resources

- [Compose Testing Documentation](https://developer.android.com/jetpack/compose/testing)
- [Compose Test Cheat Sheet](https://developer.android.com/jetpack/compose/testing-cheatsheet)
- [Hilt Testing Guide](https://developer.android.com/training/dependency-injection/hilt-testing)
- [Firebase Test Lab](https://firebase.google.com/docs/test-lab)

## Contact

For questions about UI testing strategy, consult the development team or refer to the project's testing guidelines.
