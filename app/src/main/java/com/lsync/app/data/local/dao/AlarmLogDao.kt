package com.lsync.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmCoverageStateEntity
import com.lsync.app.data.local.entity.AlarmDeliveryTokenEntity
import com.lsync.app.data.local.entity.AlarmLogEntity

@Dao
interface AlarmLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpected(row: AlarmLogEntity): Long

    @Query("SELECT * FROM alarm_log WHERE alarmKey = :key AND expectedAt = :expectedAt LIMIT 1")
    suspend fun getExpected(key: String, expectedAt: Long): AlarmLogEntity?

    @Query("UPDATE alarm_log SET status = 'PENDING', missReason = NULL WHERE alarmKey = :key AND expectedAt = :expectedAt AND status IN ('SUPERSEDED', 'CANCELLED')")
    suspend fun reopenExpected(key: String, expectedAt: Long): Int

    @Query("SELECT * FROM alarm_log WHERE userId = :userId AND expectedAt >= :fromAt AND expectedAt < :toAt ORDER BY expectedAt")
    suspend fun getExpectedInWindow(userId: String, fromAt: Long, toAt: Long): List<AlarmLogEntity>

    @Query("SELECT * FROM alarm_log WHERE userId IS NULL AND alarmKey LIKE 'reminder:%' AND expectedAt >= :fromAt AND expectedAt < :toAt ORDER BY expectedAt")
    suspend fun getReminderExpectedInWindow(fromAt: Long, toAt: Long): List<AlarmLogEntity>

    @Query("UPDATE alarm_log SET status = :status WHERE alarmKey = :key AND status = 'PENDING'")
    suspend fun closeAllPending(key: String, status: String): Int

    @Query("UPDATE alarm_log SET status = :status WHERE alarmKey = :key AND expectedAt != :keepAt AND status = 'PENDING'")
    suspend fun closeOtherPending(key: String, keepAt: Long, status: String): Int

    @Query("SELECT * FROM alarm_log WHERE alarmKey = :key AND status = 'PENDING'")
    suspend fun getPendingForKey(key: String): List<AlarmLogEntity>

    // Fired is absorbing. A late delivered broadcast may correct a previously missed row.
    @Query("UPDATE alarm_log SET status = 'FIRED', firedAt = COALESCE(firedAt, :firedAt) WHERE alarmKey = :key AND expectedAt = :expectedAt")
    suspend fun markFired(key: String, expectedAt: Long, firedAt: Long): Int

    @Query("UPDATE alarm_log SET status = 'MISSED', missReason = :reason WHERE alarmKey = :key AND expectedAt = :expectedAt AND status = 'PENDING'")
    suspend fun markMissed(key: String, expectedAt: Long, reason: String?): Int

    @Query("UPDATE alarm_log SET status = :status WHERE alarmKey = :key AND expectedAt = :expectedAt AND status = 'PENDING'")
    suspend fun closePending(key: String, expectedAt: Long, status: String): Int

    @Query("UPDATE alarm_log SET registered = 1, exact = :exact WHERE alarmKey = :key AND expectedAt = :expectedAt")
    suspend fun markRegistered(key: String, expectedAt: Long, exact: Boolean): Int

    @Query("UPDATE alarm_log SET posted = :posted, skipReason = :skipReason WHERE alarmKey = :key AND expectedAt = :expectedAt AND status = 'FIRED'")
    suspend fun recordPostResult(key: String, expectedAt: Long, posted: Boolean, skipReason: String?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun prepareToken(token: AlarmDeliveryTokenEntity)

    @Query("SELECT * FROM alarm_delivery_token WHERE token = :token LIMIT 1")
    suspend fun getToken(token: String): AlarmDeliveryTokenEntity?

    @Query("SELECT * FROM alarm_delivery_token WHERE alarmKey = :key AND state IN ('PREPARED', 'ARMED') ORDER BY createdAt")
    suspend fun getActiveTokens(key: String): List<AlarmDeliveryTokenEntity>

    @Query("UPDATE alarm_delivery_token SET state = 'ARMED', setSucceededAt = :now WHERE token = :token AND state = 'PREPARED'")
    suspend fun markTokenArmed(token: String, now: Long): Int

    @Query("UPDATE alarm_delivery_token SET state = 'FAILED' WHERE token = :token AND state = 'PREPARED'")
    suspend fun markTokenFailed(token: String): Int

    @Query("UPDATE alarm_delivery_token SET state = 'RETIRED', retiredAt = :now WHERE token = :token AND state IN ('PREPARED', 'ARMED')")
    suspend fun retireToken(token: String, now: Long): Int

    @Query("UPDATE alarm_delivery_token SET deliveredAt = :now WHERE token = :token AND deliveredAt IS NULL AND state != 'FAILED'")
    suspend fun claimDelivery(token: String, now: Long): Int

    /** Only the token from PendingIntent identity is accepted; mutable extras and current slot are ignored. */
    @Transaction
    suspend fun recordDelivery(token: String, now: Long): Boolean {
        val delivery = getToken(token) ?: return false
        if (delivery.state == "FAILED" || getExpected(delivery.alarmKey, delivery.expectedAt) == null) return false
        if (claimDelivery(token, now) != 1) return false
        return markFired(delivery.alarmKey, delivery.expectedAt, now) == 1
    }

    @Insert
    suspend fun insertCoverageGap(gap: AlarmCoverageGapEntity)

    @Query("SELECT * FROM alarm_coverage_gap WHERE fromAt < :toAt AND toAt > :fromAt ORDER BY fromAt")
    suspend fun getCoverageGaps(fromAt: Long, toAt: Long): List<AlarmCoverageGapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCoverageState(state: AlarmCoverageStateEntity)

    @Query("SELECT * FROM alarm_coverage_state WHERE id = 1")
    suspend fun getCoverageState(): AlarmCoverageStateEntity?

    @Query("SELECT * FROM alarm_log WHERE status = 'PENDING' AND expectedAt < :beforeAt")
    suspend fun getOverduePending(beforeAt: Long): List<AlarmLogEntity>

    // A gap is not evidence of a missed delivery. This also corrects a premature
    // MISSED written by a scheduler during a cold start before reconciliation ran.
    @Query("UPDATE alarm_log SET status = 'UNOBSERVED', missReason = NULL WHERE expectedAt >= :fromAt AND expectedAt < :toAt AND status IN ('PENDING', 'MISSED')")
    suspend fun markUnobserved(fromAt: Long, toAt: Long): Int

    @Query("SELECT * FROM alarm_coverage_gap WHERE fromAt = :fromAt LIMIT 1")
    suspend fun getCoverageGapStartingAt(fromAt: Long): AlarmCoverageGapEntity?

    @Query("UPDATE alarm_coverage_gap SET toAt = :toAt WHERE fromAt = :fromAt AND toAt < :toAt")
    suspend fun extendCoverageGap(fromAt: Long, toAt: Long): Int

    // Keep a log while an OS delivery token may still identify it, even if old.
    @Query("DELETE FROM alarm_log WHERE expectedAt < :cutoff AND NOT EXISTS (SELECT 1 FROM alarm_delivery_token t WHERE t.alarmKey = alarm_log.alarmKey AND t.expectedAt = alarm_log.expectedAt AND t.state IN ('PREPARED', 'ARMED'))")
    suspend fun pruneOldLogs(cutoff: Long): Int

    @Query("DELETE FROM alarm_delivery_token WHERE createdAt < :cutoff AND state NOT IN ('PREPARED', 'ARMED')")
    suspend fun pruneInactiveTokens(cutoff: Long): Int

    @Query("DELETE FROM alarm_coverage_gap WHERE toAt <= :cutoff")
    suspend fun pruneOldGaps(cutoff: Long): Int
}
