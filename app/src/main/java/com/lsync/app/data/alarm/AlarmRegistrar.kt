package com.lsync.app.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lsync.app.data.local.dao.AlarmLogDao
import com.lsync.app.data.local.entity.AlarmDeliveryTokenEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import com.lsync.app.notification.canUseExactAlarms
import com.lsync.app.notification.setAlarmCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Called on AlarmCommandQueue's IO consumer after current source state has been read. */
@Singleton
class AlarmRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AlarmLogDao,
) {
    private val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun replace(
        key: String,
        expectedAt: Long,
        userId: String?,
        receiver: Class<out BroadcastReceiver>,
        requestCode: Int,
        extras: Map<String, String>,
        expectedOccurrences: List<Long> = listOf(expectedAt),
    ) {
        val desired = (expectedOccurrences + expectedAt).toSet()
        desired.forEach { at ->
            dao.insertExpected(AlarmLogEntity(alarmKey = key, expectedAt = at, userId = userId))
            dao.reopenExpected(key, at)
        }
        val previous = dao.getActiveTokens(key)
        // Already armed for the same time and mode: keep the token instead of re-arming on every app start.
        // Only refresh extras (the title may have changed); delivery identity is the token URI, not extras.
        val reusable = reusableToken(previous, expectedAt, System.currentTimeMillis(),
            exactNow = manager.canUseExactAlarms(), registeredExact = dao.getExpected(key, expectedAt)?.exact)
        if (reusable != null) {
            val intent = Intent(context, receiver).apply {
                data = tokenUri(reusable.token)
                extras.forEach { (name, value) -> putExtra(name, value) }
            }
            val alive = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) != null
            if (alive) {
                PendingIntent.getBroadcast(
                    context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                closeSuperseded(key, desired, System.currentTimeMillis())
                return
            }
        }
        val token = UUID.randomUUID().toString()
        dao.prepareToken(AlarmDeliveryTokenEntity(token, key, expectedAt, createdAt = System.currentTimeMillis()))
        val intent = Intent(context, receiver).apply {
            data = tokenUri(token)
            extras.forEach { (name, value) -> putExtra(name, value) }
        }
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE)
        val exact = try {
            manager.setAlarmCompat(expectedAt, pending)
        } catch (failure: Exception) {
            dao.markTokenFailed(token)
            pending.cancel()
            throw failure
        }
        val now = System.currentTimeMillis()
        dao.markTokenArmed(token, now)
        dao.markRegistered(key, expectedAt, exact)
        previous.forEach { old ->
            cancelToken(old.token, receiver, requestCode, now)
            if (old.expectedAt !in desired && old.expectedAt > now)
                dao.closePending(old.alarmKey, old.expectedAt, "SUPERSEDED")
        }
        closeSuperseded(key, desired, now)
    }

    private suspend fun closeSuperseded(key: String, desired: Set<Long>, now: Long) {
        dao.getPendingForKey(key).filter { it.expectedAt !in desired }.forEach {
            if (it.expectedAt > now) dao.closePending(key, it.expectedAt, "SUPERSEDED")
        }
    }

    suspend fun cancel(key: String, receiver: Class<out BroadcastReceiver>, requestCode: Int) {
        val now = System.currentTimeMillis()
        dao.getActiveTokens(key).forEach {
            cancelToken(it.token, receiver, requestCode, now)
            if (it.expectedAt > now) dao.closePending(it.alarmKey, it.expectedAt, "CANCELLED")
        }
        dao.getPendingForKey(key).forEach {
            if (it.expectedAt > now) dao.closePending(key, it.expectedAt, "CANCELLED")
        }
    }

    private suspend fun cancelToken(token: String, receiver: Class<out BroadcastReceiver>, requestCode: Int, now: Long) {
        val intent = Intent(context, receiver).apply { data = tokenUri(token) }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
        dao.retireToken(token, now)
    }

    companion object {
        /**
         * The existing token that can stay armed: the only active token, ARMED for the same future time,
         * not yet delivered, and registered in the current exact/inexact mode. A mode change (permission
         * granted or revoked) must re-arm so the alarm is upgraded or downgraded.
         */
        internal fun reusableToken(
            active: List<AlarmDeliveryTokenEntity>,
            expectedAt: Long,
            now: Long,
            exactNow: Boolean,
            registeredExact: Boolean?,
        ): AlarmDeliveryTokenEntity? {
            val only = active.singleOrNull() ?: return null
            return only.takeIf {
                it.state == "ARMED" && it.expectedAt == expectedAt && it.deliveredAt == null &&
                    expectedAt > now && registeredExact == exactNow
            }
        }

        fun tokenUri(token: String): Uri = Uri.parse("lsync://alarm-delivery/$token")
        fun tokenFrom(intent: Intent): String? =
            intent.data?.takeIf { it.scheme == "lsync" && it.host == "alarm-delivery" }?.lastPathSegment
    }
}
