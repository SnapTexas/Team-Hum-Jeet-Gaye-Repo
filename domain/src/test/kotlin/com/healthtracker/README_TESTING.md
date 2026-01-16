# Property-Based Testing Guide

## Overview

This project uses **Kotest** for property-based testing (PBT) to ensure correctness of the Smart Health Tracker application. Property-based testing validates that properties (invariants) hold true across a wide range of generated inputs, providing stronger guarantees than example-based tests.

## Configuration

### Kotest Setup

The project is configured with:
- **Minimum 100 iterations per property test** (as per Task 18.1)
- **Parallel test execution** for faster test runs
- **InstancePerLeaf isolation** for test independence
- **30-second timeout** per test

Configuration is defined in `KotestConfig.kt`.

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :domain:test

# Run tests with detailed output
./gradlew test --info

# Run tests in continuous mode
./gradlew test --continuous
```

## Test Structure

### Test Spec Styles

We use **FunSpec** style for all property-based tests:

```kotlin
class MyFeatureTest : FunSpec({
    
    test("Property: Description of the property") {
        checkAll(100, arbGenerator1, arbGenerator2) { value1, value2 ->
            // Test assertions
        }
    }
    
    context("Group of related tests") {
        test("Specific property") {
            // Test implementation
        }
    }
})
```

### Property Test Annotations

All property tests must include:
- **Validates: Requirements X.Y** - Links to requirements document
- **Property N** - Property number from design document
- Clear description of the property being tested

Example:
```kotlin
/**
 * Property-based tests for user profile validation.
 * 
 * **Validates: Requirements 1.5**
 * 
 * Property 1: Invalid inputs return errors
 * Property 2: Valid inputs pass validation
 */
```

## Test Generators

### Using TestGenerators

The `TestGenerators` object provides reusable arbitrary value generators:

```kotlin
import com.healthtracker.TestGenerators

test("Example property test") {
    checkAll(
        TestGenerators.validUserProfileArb(),
        TestGenerators.stepsArb()
    ) { profile, steps ->
        // Test logic
    }
}
```

### Available Generators

#### User Profile
- `validNameArb()` - Valid user names
- `validAgeArb()` - Ages 13-120
- `validWeightArb()` - Weights 20-500 kg
- `validHeightArb()` - Heights 50-300 cm
- `healthGoalArb()` - Health goal enums
- `validUserProfileArb()` - Complete valid profiles

#### Health Metrics
- `stepsArb()` - Step counts 0-30000
- `distanceArb()` - Distances in meters
- `caloriesArb()` - Calorie burns 0-5000
- `sleepMinutesArb()` - Sleep duration 0-960 min
- `screenTimeMinutesArb()` - Screen time 0-1440 min
- `heartRateArb()` - Heart rate 40-200 bpm
- `hrvArb()` - HRV 10-150 ms
- `waterIntakeArb()` - Water intake 0-5000 ml
- `moodScoreArb()` - Mood scores 1-10

#### Nutrition
- `mealCaloriesArb()` - Meal calories 0-2000
- `proteinArb()` - Protein 0-200g
- `carbsArb()` - Carbs 0-300g
- `fatArb()` - Fat 0-150g
- `confidenceArb()` - ML confidence 0.0-1.0

#### Location
- `latitudeArb()` - Latitude -90 to 90
- `longitudeArb()` - Longitude -180 to 180
- `distanceKmArb()` - Distance 0-100 km

#### Gamification
- `streakArb()` - Streak counts 0-365
- `pointsArb()` - Points 0-100000
- `badgeTierArb()` - Badge tiers 1-5

#### Dates and Times
- `localDateArb()` - Random dates 2020-2030
- `instantArb()` - Random instants

### Creating Custom Generators

Use Kotest's `Arb` combinators:

```kotlin
// Simple value generator
val myArb = Arb.int(1..100)

// Filtered generator
val evenArb = Arb.int(1..100).filter { it % 2 == 0 }

// Mapped generator
val doubledArb = Arb.int(1..100).map { it * 2 }

// Combined generator
val personArb = Arb.bind(
    Arb.string(1..50),
    Arb.int(18..100)
) { name, age ->
    Person(name, age)
}
```

## Writing Property Tests

### Best Practices

1. **Test Properties, Not Examples**
   - ❌ Bad: "When steps = 5000, distance = 3750"
   - ✅ Good: "For any steps count, distance = steps × step_length"

2. **Use Descriptive Test Names**
   ```kotlin
   test("Property 7.1: Values >2 std dev below baseline flag LOW_ACTIVITY anomaly")
   ```

3. **Set Appropriate Iteration Counts**
   ```kotlin
   // Default: 100 iterations (from config)
   checkAll(arbGen) { value -> /* test */ }
   
   // Custom: 1000 iterations for critical properties
   checkAll(1000, arbGen) { value -> /* test */ }
   ```

4. **Handle Edge Cases**
   ```kotlin
   checkAll(arbGen) { value ->
       // Guard against invalid states
       if (value >= 0) {
           // Test logic
       }
   }
   ```

5. **Use Smart Generators**
   ```kotlin
   // Generate only valid inputs
   val validEmailArb = Arb.string(5..20).map { "$it@example.com" }
   ```

### Common Patterns

#### Testing Invariants
```kotlin
test("Property: Sum is always non-negative") {
    checkAll(Arb.int(), Arb.int()) { a, b ->
        val sum = abs(a) + abs(b)
        sum shouldBeGreaterThanOrEqual 0
    }
}
```

#### Testing Round-Trip Properties
```kotlin
test("Property: Serialize/deserialize returns equivalent object") {
    checkAll(validUserProfileArb()) { profile ->
        val serialized = serialize(profile)
        val deserialized = deserialize(serialized)
        deserialized shouldBe profile
    }
}
```

#### Testing Threshold Compliance
```kotlin
test("Property: Values outside threshold are flagged") {
    checkAll(Arb.double(), Arb.double()) { value, threshold ->
        val isFlagged = value > threshold
        isFlagged shouldBe (value > threshold)
    }
}
```

#### Testing Aggregations
```kotlin
test("Property: Average equals sum divided by count") {
    checkAll(Arb.list(Arb.int(1..100), 1..100)) { values ->
        val average = values.average()
        val expected = values.sum().toDouble() / values.size
        average shouldBe expected
    }
}
```

## Kotest Matchers

### Common Matchers

```kotlin
// Equality
value shouldBe expected
value shouldNotBe unexpected

// Booleans
condition.shouldBeTrue()
condition.shouldBeFalse()

// Numbers
value shouldBeGreaterThan 10
value shouldBeLessThan 100
value shouldBeInRange 10..100

// Collections
list.shouldBeEmpty()
list.shouldNotBeEmpty()
list shouldContain element
list shouldContainAll listOf(1, 2, 3)
map shouldContainKey "key"

// Strings
string shouldStartWith "prefix"
string shouldEndWith "suffix"
string shouldMatch Regex("pattern")

// Exceptions
shouldThrow<IllegalArgumentException> {
    // Code that should throw
}
```

## Test Organization

### File Structure
```
domain/src/test/kotlin/com/healthtracker/
├── KotestConfig.kt                    # Global configuration
├── TestGenerators.kt                  # Reusable generators
├── README_TESTING.md                  # This file
├── domain/
│   ├── model/
│   │   ├── UserProfileValidationTest.kt
│   │   └── UserProfilePersistenceTest.kt
│   └── usecase/
│       ├── AnomalyDetectionTest.kt
│       ├── AnalyticsAggregationTest.kt
│       └── ...
```

### Naming Conventions
- Test files: `<Feature>Test.kt`
- Test classes: `class <Feature>Test : FunSpec`
- Test names: `"Property X.Y: Description"`

## Debugging Failed Properties

When a property test fails, Kotest provides:
1. **The failing input** that caused the failure
2. **Shrinking** - attempts to find the minimal failing case
3. **Seed** - for reproducing the failure

Example failure output:
```
Property failed after 42 iterations
Arg 0: 12345
Arg 1: "invalid-input"
Seed: 1234567890
```

To reproduce:
```kotlin
test("Property") {
    checkAll(
        seed = 1234567890,  // Use seed from failure
        arbGen1, arbGen2
    ) { value1, value2 ->
        // Test logic
    }
}
```

## Coverage Goals

- **Unit tests**: 80% code coverage minimum
- **Property tests**: All correctness properties from design document
- **Integration tests**: Critical user flows

## Resources

- [Kotest Documentation](https://kotest.io/)
- [Property-Based Testing Guide](https://kotest.io/docs/proptest/property-based-testing.html)
- [Kotest Matchers](https://kotest.io/docs/assertions/matchers.html)
- [Design Document](../../../../../../.kiro/specs/smart-health-tracker/design.md) - See Correctness Properties section

## Troubleshooting

### Tests Running Slowly
- Reduce iteration count for non-critical properties
- Use more specific generators to avoid invalid inputs
- Enable parallel execution in `KotestConfig`

### Flaky Tests
- Check for non-deterministic behavior
- Ensure proper test isolation
- Use fixed seeds for debugging

### Generator Issues
- Validate generator output ranges
- Add filters to exclude invalid values
- Use `Arb.bind` for complex object construction

## Contact

For questions about testing strategy, see the design document or consult the development team.
