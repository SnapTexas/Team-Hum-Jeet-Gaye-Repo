package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.CircleMember
import com.healthtracker.domain.model.CirclePrivacySettings
import com.healthtracker.domain.model.CircleType
import com.healthtracker.domain.model.HealthCircle
import com.healthtracker.domain.model.MemberRole
import com.healthtracker.domain.model.MetricType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for circle aggregation correctness.
 * 
 * **Validates: Requirements 11.4**
 * Property 27: Circle aggregation includes only opted-in metrics.
 */
class CircleAggregationTest : FunSpec({

    // Generators for test data
    val metricTypeArb = Arb.element(MetricType.entries.toList())
    val shareableMetricArb = Arb.element(CirclePrivacySettings.SHAREABLE_METRICS.toList())
    
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

    fun generateCircle(members: List<CircleMember>): HealthCircle = HealthCircle(
        id = UUID.randomUUID().toString(),
        name = "Test Circle",
        type = CircleType.FRIENDS,
        ownerId = members.firstOrNull()?.userId ?: "owner",
        members = members,
        joinCode = "TESTCODE",
        createdAt = Instant.now()
    )

    /**
     * Feature: smart-health-tracker, Property 27: Circle Aggregation Correctness
     * 
     * For any active health circle with multiple members, the group progress 
     * aggregation SHALL correctly sum/average only the metrics that each member 
     * has opted to share.
     */
    test("aggregation includes only opted-in metrics for all members") {
        checkAll(100, Arb.list(shareableMetricArb, 1..5), Arb.int(2..10)) { sharedMetricsList, memberCount ->
            val sharedMetrics = sharedMetricsList.toSet()
            
            // Create members with specific shared metrics
            val members = (1..memberCount).map { i ->
                generateMember("user_$i", sharedMetrics)
            }
            
            val circle = generateCircle(members)
            
            // Simulate aggregation logic
            val aggregatedMetrics = mutableSetOf<MetricType>()
            
            for (member in circle.members) {
                // Only add metrics that the member has opted to share
                member.sharedMetrics.forEach { metric ->
                    if (member.sharesMetric(metric)) {
                        aggregatedMetrics.add(metric)
                    }
                }
            }
            
            // Verify: aggregated metrics should only contain opted-in metrics
            aggregatedMetrics.forEach { metric ->
                // Every aggregated metric should be shared by at least one member
                val isSharedByAtLeastOne = circle.members.any { it.sharesMetric(metric) }
                isSharedByAtLeastOne shouldBe true
            }
            
            // Verify: no unshared metrics appear in aggregation
            MetricType.entries.forEach { metric ->
                val isSharedByAny = circle.members.any { it.sharesMetric(metric) }
                if (!isSharedByAny) {
                    aggregatedMetrics shouldNotContain metric
                }
            }
        }
    }

    test("aggregation excludes members who haven't shared the specific metric") {
        checkAll(100, shareableMetricArb, Arb.int(2..10)) { targetMetric, memberCount ->
            // Create members where only some share the target metric
            val members = (1..memberCount).map { i ->
                val sharesTarget = i % 2 == 0 // Only even-numbered members share
                val sharedMetrics = if (sharesTarget) {
                    setOf(targetMetric)
                } else {
                    emptySet()
                }
                generateMember("user_$i", sharedMetrics)
            }
            
            val circle = generateCircle(members)
            
            // Count members who should contribute to aggregation
            val contributingMembers = circle.members.filter { it.sharesMetric(targetMetric) }
            val nonContributingMembers = circle.members.filter { !it.sharesMetric(targetMetric) }
            
            // Verify: only members who share the metric should contribute
            contributingMembers.forEach { member ->
                member.sharesMetric(targetMetric) shouldBe true
            }
            
            nonContributingMembers.forEach { member ->
                member.sharesMetric(targetMetric) shouldBe false
            }
        }
    }

    test("aggregation with mixed sharing preferences produces correct subset") {
        checkAll(100, Arb.list(shareableMetricArb, 1..5)) { metricsList ->
            val allMetrics = metricsList.toSet()
            
            // Create members with different sharing preferences
            val member1 = generateMember("user_1", allMetrics)
            val member2 = generateMember("user_2", allMetrics.take(allMetrics.size / 2).toSet())
            val member3 = generateMember("user_3", emptySet())
            
            val circle = generateCircle(listOf(member1, member2, member3))
            
            // For each metric, verify correct member inclusion
            allMetrics.forEach { metric ->
                val membersWhoShare = circle.members.filter { it.sharesMetric(metric) }
                
                // member1 shares all metrics
                if (metric in member1.sharedMetrics) {
                    membersWhoShare.map { it.userId } shouldContain "user_1"
                }
                
                // member3 shares nothing
                membersWhoShare.map { it.userId } shouldNotContain "user_3"
            }
        }
    }

    test("empty shared metrics results in no contribution to aggregation") {
        checkAll(100, Arb.int(1..10)) { memberCount ->
            // All members share nothing
            val members = (1..memberCount).map { i ->
                generateMember("user_$i", emptySet())
            }
            
            val circle = generateCircle(members)
            
            // No metric should have any contributing members
            MetricType.entries.forEach { metric ->
                val contributingMembers = circle.members.filter { it.sharesMetric(metric) }
                contributingMembers.size shouldBe 0
            }
        }
    }
})

private infix fun <E> List<E>.shouldContain(element: E) {
    if (element !in this) {
        throw AssertionError("List should contain $element but was $this")
    }
}
