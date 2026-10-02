package com.lsync.app.data.alarm

import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmReliabilityPolicyTest {
    private fun row(at: Long, status: String, posted: Boolean? = null, skip: String? = null) =
        AlarmLogEntity(alarmKey = "event:e", expectedAt = at, userId = "u", status = status,
            posted = posted, skipReason = skip)

    @Test fun coverageGapPreventsPassEvenWhenObservedRateIsZero() {
        val result = AlarmReliabilityPolicy.summarize(
            listOf(row(50, "FIRED")), listOf(AlarmCoverageGapEntity(fromAt = 60, toAt = 70)),
            fromAt = 0, toAt = 100, coveredThroughAt = 150, coveredFromAt = 0)
        assertEquals(0.0, result.rate!!, 0.0)
        assertEquals(GateVerdict.UNDETERMINED, result.verdict)
    }

    @Test fun blockingIsCountedOnlyOnceAndVerdictRemainsReferenceUntilCalibration() {
        val rows = listOf(row(10, "FIRED", false, "CHANNEL_BLOCKED"), row(20, "MISSED"),
            row(30, "FIRED", false, "UNKNOWN"), row(40, "PENDING"))
        val result = AlarmReliabilityPolicy.summarize(rows, emptyList(), 0, 100, 100, coveredFromAt = 0)
        assertEquals(2, result.fired)
        assertEquals(1, result.missed)
        assertEquals(1, result.blocked)
        assertEquals(2.0 / 3, result.rate!!, 0.0)
        assertEquals(GateVerdict.REFERENCE_EXCEEDED, result.verdict)
    }

    @Test fun uncoveredTailAndNoDataAreDistinct() {
        assertEquals(GateVerdict.UNDETERMINED,
            AlarmReliabilityPolicy.summarize(emptyList(), emptyList(), 0, 100, 99).verdict)
        assertEquals(GateVerdict.NO_DATA,
            AlarmReliabilityPolicy.summarize(emptyList(), emptyList(), 0, 100, 100, coveredFromAt = 0).verdict)
    }

    @Test fun exactlyFivePercentDoesNotPassAndPreInstallWindowIsUndetermined() {
        val rows = (0L until 20L).map { row(it, if (it == 0L) "MISSED" else "FIRED") }
        assertEquals(GateVerdict.REFERENCE_EXCEEDED,
            AlarmReliabilityPolicy.summarize(rows, emptyList(), 0, 20, 20, coveredFromAt = 0).verdict)
        assertEquals(GateVerdict.UNDETERMINED,
            AlarmReliabilityPolicy.summarize(rows, emptyList(), 0, 20, 20, coveredFromAt = 10).verdict)
    }
}
