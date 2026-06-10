package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.notification.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val remote: FirestoreDataSource,
    private val alarmScheduler: AlarmScheduler,
) {
    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()

    fun observeByDateRange(from: String, to: String): Flow<List<EventEntity>> =
        dao.observeByDateRange(from, to)

    suspend fun save(event: EventEntity) {
        dao.upsert(event)
        syncSafe { remote.upsertEvent(event) }
        alarmScheduler.scheduleForEvent(event)
    }

    suspend fun create(
        userId: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        endDate: String? = null,
        timezone: String? = null,
        rrule: String? = null,
        hasAlarm: Boolean = false,
    ): EventEntity {
        val now = System.currentTimeMillis()
        val entity = EventEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = endDate,
            timezone = if (isAllDay) null else (timezone ?: "Asia/Seoul"),
            rrule = rrule,
            exdatesJson = null,
            overridesJson = null,
            hasAlarm = hasAlarm,
            createdAt = now,
            updatedAt = now,
        )
        save(entity)
        return entity
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
        syncSafe { remote.deleteEvent(id) }
        alarmScheduler.cancel(id)
    }

    // 네트워크 에러는 Crashlytics에 기록하고 로컬은 이미 저장됐으므로 조용히 실패
    private suspend fun syncSafe(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("sync_target", "events")
                recordException(e)
                log("sync_failed")
            }
        }
    }
}
