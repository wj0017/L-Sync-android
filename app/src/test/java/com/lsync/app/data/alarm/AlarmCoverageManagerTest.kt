package com.lsync.app.data.alarm

import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmCoverageStateEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCoverageManagerTest {
    private val now = 10_000_000L
    private val coverage = AlarmCoverageStateEntity(
        coveredFromAt = 1_000_000L,
        coveredThroughAt = now + AlarmReliabilityPolicy.EXPECTATION_HORIZON_MS,
        refreshedAt = now,
    )

    private fun pending(at: Long, exact: Boolean? = true) = AlarmLogEntity(
        alarmKey = "event:e", expectedAt = at, userId = "u", exact = exact,
    )

    @Test fun onlyCoveredOverdueOccurrencesBecomeMissed() {
        val overdue = pending(now - AlarmReliabilityPolicy.EXACT_GRACE_MS - 1)
        assertTrue(AlarmCoverageManager.shouldMarkMissed(overdue, coverage, emptyList(), now))
        assertFalse(AlarmCoverageManager.shouldMarkMissed(
            pending(coverage.coveredFromAt - 1), coverage, emptyList(), now))
        assertFalse(AlarmCoverageManager.shouldMarkMissed(
            pending(now - AlarmReliabilityPolicy.EXACT_GRACE_MS), coverage, emptyList(), now))
    }

    @Test fun uncoveredGapAndInexactGraceDoNotCreateFalseMisses() {
        val overdue = pending(now - AlarmReliabilityPolicy.EXACT_GRACE_MS - 1)
        val gap = AlarmCoverageGapEntity(fromAt = overdue.expectedAt, toAt = now)
        assertFalse(AlarmCoverageManager.shouldMarkMissed(overdue, coverage, listOf(gap), now))
        assertFalse(AlarmCoverageManager.shouldMarkMissed(
            pending(now - AlarmReliabilityPolicy.INEXACT_GRACE_MS, exact = false),
            coverage, emptyList(), now,
        ))
        assertTrue(AlarmCoverageManager.shouldMarkMissed(
            pending(now - AlarmReliabilityPolicy.INEXACT_GRACE_MS - 1, exact = false),
            coverage, emptyList(), now,
        ))
    }
}
