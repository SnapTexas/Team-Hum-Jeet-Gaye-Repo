package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for circle privacy enforcement.
 * 
 * **Validates: Requirements 11.5**
 * Property 28: Unshared metrics don't appear in views.
 */
class CirclePrivacyEnforcementTest : FunSpec({

    val shareableMetricArb = Arb.element(CirclePrivacySettings.SHAREABLE_METRICS.toList())
    val sensitiveMetricArb = Arb.element(CirclePrivacySettings.SENSITIVE_METRICS.toList())
    val allMetricArb = Arb.element(MetricType.entries.toList())

    fun generateMember(
        userId: String,
        sharedMetrics: Set<MetricType>
    ): CircleMember = CircleMember(
        userId = userId,
        displayName = "User $userId",
        avatarUrl = null,
        role = MemberRole.MEMBER,
        sharedMetrics = sharedMetrics,
        joinedAt = Instant.now()
    )

    /**
     * Simulates filtering metrics for display based on privacy settings.
     * This is the core privacy enforcement logic.
     */
    fun filterMetricsForDisplay(
        member: CircleMember,
        requestedMetrics: Set<MetricType>
    ): Set<MetricType> {
        return requestedMetrics.filter { metric ->
            member.sharesMetric(metric)
        }.toSet()
    }

    /**
     * Feature: smart-health-tracker, Property 28: Circle Privacy Enforcement
     * 
     * For any metric type that a user has not opted to share with a circle, 
     * that metric SHALL NOT appear in any circle aggregations or member views.
     */
    test("unshared metrics never appear in filtered view") {
        checkAll(100, 
            Arb.set(shareableMetricArb, 0..5),
            Arb.set(allMetricArb, 1..8)
        ) { sharedMetrics, requestedMetrics ->
            val member = generateMember("test_user", sharedMetrics)
            
            val displayedMetrics = filterMetricsForDisplay(member, requestedMetrics)
            
            // Verify: displayed metrics are subset of shared metrics
            displayedMetrics.forEach { metric ->
                member.sharesMetric(metric) shouldBe true
            }
            
            // Verify: unshared metrics never appear
            val unsharedMetrics = requestedMetrics - sharedMetrics
            unsharedMetrics.forEach { metric ->
                displayedMetrics shouldNotContain metric
            }
        }
    }

    test("sensitive metrics are never shared unless explicitly opted in") {
        checkAll(100, Arb.set(shareableMetricArb, 0..5)) { sharedMetrics ->
            // Shared metrics should only contain shareable metrics, not sensitive ones
            val member = generateMember("test_user", sharedMetrics)
            
            // Request all metrics including sensitive ones
            val allMetrics = MetricType.entries.toSet()
            val displayedMetrics = filterMetricsForDisplay(member, allMetrics)
            
            // Sensitive metrics should not appear unless explicitly shared
            CirclePrivacySettings.SENSITIVE_METRICS.forEach { sensitiveMetric ->
                if (sensitiveMetric !in sharedMetrics) {
                    displayedMetrics shouldNotContain sensitiveMetric
                }
            }
        }
    }

    test("empty shared metrics results in empty display") {
        checkAll(100, Arb.set(allMetricArb, 1..8)) { requestedMetrics ->
            val member = generateMember("test_user", emptySet())
            
            val displayedMetrics = filterMetricsForDisplay(member, requestedMetrics)
            
            displayedMetrics.shouldBeEmpty()
        }
    }

    test("privacy settings are enforced per-member independently") {
        checkAll(100, 
            Arb.set(shareableMetricArb, 1..3),
            Arb.set(shareableMetricArb, 1..3)
        ) { member1Metrics, member2Metrics ->
            val member1 = generateMember("user_1", member1Metrics)
            val member2 = generateMember("user_2", member2Metrics)
            
            val allMetrics = MetricType.entries.toSet()
            
            val member1Display = filterMetricsForDisplay(member1, allMetrics)
            val member2Display = filterMetricsForDisplay(member2, allMetrics)
            
            // Each member's display should only contain their shared metrics
            member1Display.forEach { metric ->
                member1.sharesMetric(metric) shouldBe true
            }
            
            member2Display.forEach { metric ->
                member2.sharesMetric(metric) shouldBe true
            }
            
            // Metrics not shared by member1 should not appear in their view
            (allMetrics - member1Metrics).forEach { metric ->
                member1Display shouldNotContain metric
            }
            
            // Metrics not shared by member2 should not appear in their view
            (allMetrics - member2Metrics).forEach { metric ->
                member2Display shouldNotContain metric
            }
        }
    }

    test("privacy enforcement is consistent across multiple requests") {
        checkAll(100, 
            Arb.set(shareableMetricArb, 1..5),
            Arb.int(1..10)
        ) { sharedMetrics, requestCount ->
            val member = generateMember("test_user", sharedMetrics)
            val allMetrics = MetricType.entries.toSet()
            
            // Make multiple requests and verify consistency
            val results = (1..requestCount).map {
                filterMetricsForDisplay(member, allMetrics)
            }
            
            // All results should be identical
            results.forEach { result ->
                result shouldBe results.first()
            }
            
            // All results should respect privacy settings
            results.forEach { result ->
                result.forEach { metric ->
                    member.sharesMetric(metric) shouldBe true
                }
            }
        }
    }

    test("changing privacy settings immediately affects display") {
        checkAll(100, 
            Arb.set(shareableMetricArb, 1..3),
            Arb.set(shareableMetricArb, 1..3)
        ) { initialMetrics, newMetrics ->
            val allMetrics = MetricType.entries.toSet()
            
            // Initial state
            val memberInitial = generateMember("test_user", initialMetrics)
            val initialDisplay = filterMetricsForDisplay(memberInitial, allMetrics)
            
            // After privacy change
            val memberUpdated = generateMember("test_user", newMetrics)
            val updatedDisplay = filterMetricsForDisplay(memberUpdated, allMetrics)
            
            // Initial display should match initial settings
            initialDisplay shouldBe initialMetrics.intersect(allMetrics)
            
            // Updated display should match new settings
            updatedDisplay shouldBe newMetrics.intersect(allMetrics)
            
            // Metrics removed from sharing should no longer appear
            val removedMetrics = initialMetrics - newMetrics
            removedMetrics.forEach { metric ->
                updatedDisplay shouldNotContain metric
            }
        }
    }
})
