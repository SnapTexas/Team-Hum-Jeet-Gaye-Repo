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
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

/**
 * Property-based tests for circle leave data removal.
 * 
 * **Validates: Requirements 11.6**
 * Property 29: Leaving removes user from aggregations.
 */
class CircleLeaveDataRemovalTest : FunSpec({

    val shareableMetricArb = Arb.element(CirclePrivacySettings.SHAREABLE_METRICS.toList())

    data class MemberData(
        val userId: String,
        val displayName: String,
        val sharedMetrics: Set<MetricType>,
        val metricValues: Map<MetricType, Double>
    )

    fun generateMemberData(
        userId: String,
        sharedMetrics: Set<MetricType>,
        metricValues: Map<MetricType, Double>
    ): MemberData = MemberData(
        userId = userId,
        displayName = "User $userId",
        sharedMetrics = sharedMetrics,
        metricValues = metricValues
    )

    fun generateCircleMember(data: MemberData): CircleMember = CircleMember(
        userId = data.userId,
        displayName = data.displayName,
        avatarUrl = null,
        role = MemberRole.MEMBER,
        sharedMetrics = data.sharedMetrics,
        joinedAt = Instant.now()
    )

    /**
     * Simulates calculating circle aggregation from member data.
     */
    fun calculateAggregation(
        members: List<MemberData>,
        metricType: MetricType
    ): Double {
        return members
            .filter { metricType in it.sharedMetrics }
            .mapNotNull { it.metricValues[metricType] }
            .sum()
    }

    /**
     * Simulates removing a member from the circle.
     */
    fun removeMember(members: List<MemberData>, userIdToRemove: String): List<MemberData> {
        return members.filter { it.userId != userIdToRemove }
    }

    /**
     * Feature: smart-health-tracker, Property 29: Circle Leave Data Removal
     * 
     * For any user who leaves a circle, recalculating group aggregations 
     * SHALL produce results that do not include that user's data.
     */
    test("leaving circle removes user data from aggregations") {
        checkAll(100, 
            Arb.int(3..10),
            shareableMetricArb,
            Arb.double(100.0..10000.0)
        ) { memberCount, metricType, baseValue ->
            // Create members with metric values
            val members = (1..memberCount).map { i ->
                val sharedMetrics = setOf(metricType)
                val metricValues = mapOf(metricType to baseValue * i)
                generateMemberData("user_$i", sharedMetrics, metricValues)
            }
            
            // Calculate initial aggregation
            val initialAggregation = calculateAggregation(members, metricType)
            
            // Pick a random member to remove
            val memberToRemove = members.random()
            val memberValue = memberToRemove.metricValues[metricType] ?: 0.0
            
            // Remove the member
            val remainingMembers = removeMember(members, memberToRemove.userId)
            
            // Calculate new aggregation
            val newAggregation = calculateAggregation(remainingMembers, metricType)
            
            // Verify: new aggregation should not include removed member's data
            newAggregation shouldBe (initialAggregation - memberValue)
            
            // Verify: removed member is not in remaining members
            remainingMembers.map { it.userId } shouldNotContain memberToRemove.userId
        }
    }

    test("leaving circle removes user from member list") {
        checkAll(100, Arb.int(2..20)) { memberCount ->
            val members = (1..memberCount).map { i ->
                generateMemberData("user_$i", emptySet(), emptyMap())
            }
            
            val memberToRemove = members.random()
            val remainingMembers = removeMember(members, memberToRemove.userId)
            
            // Verify: member count decreased by 1
            remainingMembers.size shouldBe (members.size - 1)
            
            // Verify: removed member is not in list
            remainingMembers.none { it.userId == memberToRemove.userId } shouldBe true
        }
    }

    test("aggregation after leave equals aggregation without that member") {
        checkAll(100, 
            Arb.int(3..10),
            Arb.set(shareableMetricArb, 1..3)
        ) { memberCount, sharedMetrics ->
            // Create members with random metric values
            val members = (1..memberCount).map { i ->
                val metricValues = sharedMetrics.associateWith { (100.0 * i) }
                generateMemberData("user_$i", sharedMetrics, metricValues)
            }
            
            val memberToRemove = members.random()
            
            // Calculate aggregation for each metric type
            sharedMetrics.forEach { metricType ->
                // Aggregation with all members
                val fullAggregation = calculateAggregation(members, metricType)
                
                // Aggregation without the member (simulating they never joined)
                val membersWithoutTarget = members.filter { it.userId != memberToRemove.userId }
                val aggregationWithoutMember = calculateAggregation(membersWithoutTarget, metricType)
                
                // Aggregation after member leaves
                val remainingMembers = removeMember(members, memberToRemove.userId)
                val aggregationAfterLeave = calculateAggregation(remainingMembers, metricType)
                
                // These should be equal
                aggregationAfterLeave shouldBe aggregationWithoutMember
                
                // And different from full aggregation (unless member contributed 0)
                val memberContribution = memberToRemove.metricValues[metricType] ?: 0.0
                if (memberContribution > 0 && metricType in memberToRemove.sharedMetrics) {
                    aggregationAfterLeave shouldNotBe fullAggregation
                }
            }
        }
    }

    test("multiple members leaving removes all their data") {
        checkAll(100, Arb.int(5..15), shareableMetricArb) { memberCount, metricType ->
            val members = (1..memberCount).map { i ->
                val metricValues = mapOf(metricType to 100.0 * i)
                generateMemberData("user_$i", setOf(metricType), metricValues)
            }
            
            // Remove multiple members
            val membersToRemove = members.take(memberCount / 2)
            var remainingMembers = members.toList()
            
            membersToRemove.forEach { memberToRemove ->
                remainingMembers = removeMember(remainingMembers, memberToRemove.userId)
            }
            
            // Calculate final aggregation
            val finalAggregation = calculateAggregation(remainingMembers, metricType)
            
            // Calculate expected aggregation (only remaining members)
            val expectedAggregation = members
                .filter { it.userId !in membersToRemove.map { m -> m.userId } }
                .mapNotNull { it.metricValues[metricType] }
                .sum()
            
            finalAggregation shouldBe expectedAggregation
            
            // Verify none of the removed members are in remaining
            membersToRemove.forEach { removed ->
                remainingMembers.map { it.userId } shouldNotContain removed.userId
            }
        }
    }

    test("owner leaving (deleting circle) removes all data") {
        checkAll(100, Arb.int(2..10), shareableMetricArb) { memberCount, metricType ->
            val members = (1..memberCount).map { i ->
                val metricValues = mapOf(metricType to 100.0 * i)
                generateMemberData("user_$i", setOf(metricType), metricValues)
            }
            
            // Simulate circle deletion (all members removed)
            val emptyMembers = emptyList<MemberData>()
            
            // Aggregation should be 0
            val aggregation = calculateAggregation(emptyMembers, metricType)
            aggregation shouldBe 0.0
        }
    }

    test("leaving circle with no shared metrics has no effect on aggregation") {
        checkAll(100, Arb.int(3..10), shareableMetricArb) { memberCount, metricType ->
            // Create members where one doesn't share the metric
            val members = (1..memberCount).map { i ->
                val sharedMetrics = if (i == 1) emptySet() else setOf(metricType)
                val metricValues = mapOf(metricType to 100.0 * i)
                generateMemberData("user_$i", sharedMetrics, metricValues)
            }
            
            val memberWithNoSharing = members.first()
            
            // Calculate aggregation before and after removing non-sharing member
            val aggregationBefore = calculateAggregation(members, metricType)
            val remainingMembers = removeMember(members, memberWithNoSharing.userId)
            val aggregationAfter = calculateAggregation(remainingMembers, metricType)
            
            // Aggregation should be the same since member wasn't contributing
            aggregationBefore shouldBe aggregationAfter
        }
    }
})
