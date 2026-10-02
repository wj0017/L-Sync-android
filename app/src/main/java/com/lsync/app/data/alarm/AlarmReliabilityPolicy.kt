package com.lsync.app.data.alarm

import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmLogEntity

enum class GateVerdict { NO_DATA, UNDETERMINED, REFERENCE_PASS, REFERENCE_EXCEEDED, VERIFIED_PASS, VERIFIED_EXCEEDED }

data class AlarmReliabilitySummary(
    val fired: Int,
    val missed: Int,
    val blocked: Int,
    val rate: Double?,
    val verdict: GateVerdict,
)

object AlarmReliabilityPolicy {
    const val EXACT_GRACE_MS = 15 * 60 * 1000L
    const val INEXACT_GRACE_MS = 2 * 60 * 60 * 1000L
    const val EXPECTATION_HORIZON_MS = 48 * 60 * 60 * 1000L

    fun deadlinePassed(expectedAt: Long, exact: Boolean?, now: Long): Boolean =
        now - expectedAt > if (exact == true) EXACT_GRACE_MS else INEXACT_GRACE_MS

    /** Local receiver arrival is measurable. A successful notify call does not prove visual delivery. */
    fun summarize(
        rows: List<AlarmLogEntity>,
        gaps: List<AlarmCoverageGapEntity>,
        fromAt: Long,
        toAt: Long,
        coveredThroughAt: Long?,
        calibratedOnDevice: Boolean = false,
        coveredFromAt: Long? = null,
    ): AlarmReliabilitySummary {
        require(fromAt < toAt)
        val eligible = rows.filter {
            it.userId != null && it.expectedAt >= fromAt && it.expectedAt < toAt &&
                it.missReason != "BEFORE_BOOT" && it.status in setOf("FIRED", "MISSED")
        }
        val fired = eligible.count { it.status == "FIRED" }
        val missed = eligible.count { it.status == "MISSED" }
        val blocked = eligible.count {
            it.status == "FIRED" && it.posted == false &&
                it.skipReason in setOf("NOTIFICATIONS_OFF", "CHANNEL_BLOCKED")
        }
        val denominator = fired + missed
        val rate = if (denominator == 0) null else (missed + blocked).toDouble() / denominator
        val gap = coveredFromAt == null || coveredFromAt > fromAt ||
            coveredThroughAt == null || coveredThroughAt < toAt ||
            gaps.any { it.fromAt < toAt && it.toAt > fromAt }
        val verdict = when {
            gap -> GateVerdict.UNDETERMINED
            rate == null -> GateVerdict.NO_DATA
            calibratedOnDevice && rate < 0.05 -> GateVerdict.VERIFIED_PASS
            calibratedOnDevice -> GateVerdict.VERIFIED_EXCEEDED
            rate < 0.05 -> GateVerdict.REFERENCE_PASS
            else -> GateVerdict.REFERENCE_EXCEEDED
        }
        return AlarmReliabilitySummary(fired, missed, blocked, rate, verdict)
    }
}
