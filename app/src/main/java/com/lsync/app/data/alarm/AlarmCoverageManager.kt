package com.lsync.app.data.alarm

import androidx.room.withTransaction
import com.lsync.app.data.local.AppDatabase
import com.lsync.app.data.local.dao.AlarmLogDao
import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmCoverageStateEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Reconciles the durable expectation window before restoring OS registrations. */
@Singleton
class AlarmCoverageManager @Inject constructor(
    private val database: AppDatabase,
    private val dao: AlarmLogDao,
) {
    suspend fun beforeRestore(now: Long) = database.withTransaction {
        val coverage = dao.getCoverageState()
        if (coverage == null) {
            // Historical rows cannot establish coverage before the first successful restore.
            dao.markUnobserved(Long.MIN_VALUE, now)
            return@withTransaction
        }

        if (coverage.coveredThroughAt < now) {
            val gapStart = coverage.coveredThroughAt
            if (dao.getCoverageGapStartingAt(gapStart) == null) {
                dao.insertCoverageGap(AlarmCoverageGapEntity(fromAt = gapStart, toAt = now))
            } else {
                dao.extendCoverageGap(gapStart, now)
            }
            dao.markUnobserved(gapStart, now)
        }

        val coveredEnd = minOf(now, coverage.coveredThroughAt)
        if (coveredEnd <= coverage.coveredFromAt) return@withTransaction
        val gaps = dao.getCoverageGaps(coverage.coveredFromAt, coveredEnd)
        dao.getOverduePending(now).forEach { row ->
            if (shouldMarkMissed(row, coverage, gaps, now)) {
                dao.markMissed(row.alarmKey, row.expectedAt, "NO_DELIVERY")
            }
        }
    }

    /** Called only after every scheduler command completed successfully. */
    suspend fun afterRestore(startedAt: Long, completedAt: Long) = database.withTransaction {
        val old = dao.getCoverageState()
        // Schedulers populated expectations from their own clocks after startedAt.
        // Their shared guaranteed window is therefore bounded by startedAt + 48h.
        val through = startedAt + AlarmReliabilityPolicy.EXPECTATION_HORIZON_MS

        dao.getCoverageGaps(completedAt - RETENTION_MS, completedAt).forEach { gap ->
            dao.markUnobserved(gap.fromAt, minOf(gap.toAt, completedAt))
        }

        dao.putCoverageState(AlarmCoverageStateEntity(
            coveredFromAt = old?.coveredFromAt ?: startedAt,
            coveredThroughAt = maxOf(old?.coveredThroughAt ?: through, through),
            refreshedAt = completedAt,
        ))

        val cutoff = completedAt - RETENTION_MS
        dao.pruneInactiveTokens(cutoff)
        dao.pruneOldLogs(cutoff)
        dao.pruneOldGaps(cutoff)
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

        internal fun shouldMarkMissed(
            row: AlarmLogEntity,
            coverage: AlarmCoverageStateEntity,
            gaps: List<AlarmCoverageGapEntity>,
            now: Long,
        ): Boolean {
            val expected = row.expectedAt
            return row.status == "PENDING" &&
                expected >= coverage.coveredFromAt &&
                expected < minOf(now, coverage.coveredThroughAt) &&
                gaps.none { it.fromAt <= expected && expected < it.toAt } &&
                AlarmReliabilityPolicy.deadlinePassed(expected, row.exact, now)
        }
    }
}
