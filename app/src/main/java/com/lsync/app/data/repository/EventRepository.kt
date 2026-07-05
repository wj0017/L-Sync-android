package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.recurrence.EventOverride
import com.lsync.app.data.recurrence.withExdate
import com.lsync.app.data.recurrence.withOverride
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.di.ApplicationScope
import com.lsync.app.notification.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val dao: EventDao,
    private val remote: FirestoreDataSource,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()

    fun observeByDateRange(from: String, to: String): Flow<List<EventEntity>> =
        dao.observeByDateRange(from, to)

    // 표시 전개용 — 과거 시작 반복 마스터 포함(observeByDateRange는 startDate >= from이라 누락)
    fun observeForExpansion(from: String, to: String): Flow<List<EventEntity>> =
        dao.observeForExpansion(from, to)

    suspend fun save(event: EventEntity) {
        dao.upsert(event)
        alarmScheduler.scheduleForEvent(event)
        syncSafe { remote.upsertEvent(event) }
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

    // 일정 수정 — 불변 필드(id/userId/createdAt) 보존 후 save 재사용(upsert+동기화+알람 재등록)
    suspend fun update(
        id: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String? = null,
        hasAlarm: Boolean = false,
    ): EventEntity? {
        val existing = dao.getById(id) ?: return null
        val updated = existing.copy(
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            timezone = if (isAllDay) null else (existing.timezone ?: "Asia/Seoul"),
            rrule = rrule,
            hasAlarm = hasAlarm,
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
        return updated
    }

    // 삭제는 tombstone(soft delete)으로 전파 — 원격 hard delete는 push 실패 시 다음 pull에서
    // 부활하고, upsert-only pull이라 다른 기기의 로컬에서도 지워지지 않는다.
    suspend fun delete(id: String) {
        val existing = dao.getById(id) ?: return
        val now = System.currentTimeMillis()
        val deleted = existing.copy(deletedAt = now, updatedAt = now)
        dao.upsert(deleted)
        alarmScheduler.cancelEvent(id)
        syncSafe { remote.upsertEvent(deleted) }
    }

    // ── 반복 일정 삭제 범위 ────────────────────────────────────────────────────

    // 이 발생만: 마스터의 exdatesJson에 발생일을 추가(EventRecurrence.withExdate)한 뒤 save.
    // 동기화·알람 재등록은 save가 처리. 단발(rrule==null) 발생이면 전체 삭제와 동일.
    suspend fun deleteOccurrence(masterId: String, occurrenceDate: String) {
        val existing = dao.getById(masterId) ?: return
        if (existing.rrule.isNullOrBlank()) {
            delete(masterId)
            return
        }
        val updated = existing.copy(
            exdatesJson = withExdate(existing.exdatesJson, occurrenceDate),
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
    }

    // 이후 모든: rrule에 UNTIL=(occurrenceDate 전날)을 설정해 시리즈를 절단.
    // occurrenceDate가 시작일 이하면 보일 발생이 없어 전체 삭제와 동일.
    suspend fun deleteFollowing(masterId: String, occurrenceDate: String) {
        val existing = dao.getById(masterId) ?: return
        if (existing.rrule.isNullOrBlank()) {
            delete(masterId)
            return
        }
        val occ = runCatching { LocalDate.parse(occurrenceDate) }.getOrNull()
        val start = runCatching { LocalDate.parse(existing.startDate.take(10)) }.getOrNull()
        if (occ == null || (start != null && !occ.isAfter(start))) {
            delete(masterId)
            return
        }
        // UNTIL은 inclusive → 발생일 전날을 경계로 두어 occurrenceDate부터 제거
        val until = occ.minusDays(1).format(UNTIL_FORMAT)
        val updated = existing.copy(
            rrule = withUntil(existing.rrule, until),
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
    }

    // 전체: 기존 hard delete 재사용.
    suspend fun deleteSeries(masterId: String) = delete(masterId)

    // ── 반복 일정 수정 범위 ────────────────────────────────────────────────────

    // 이 발생만: 마스터 overridesJson에 occurrenceDate→EventOverride를 병합(withOverride) 후 save.
    // 마스터 rrule/startDate는 불변 — override는 발생 날짜를 옮기지 않고 시각·제목·알람만 바꾼다(Step 0 규칙).
    // 단발(rrule==null) 발생이면 범위 개념이 없어 마스터 전체 수정과 동일.
    suspend fun editOccurrence(
        masterId: String,
        occurrenceDate: String,
        title: String,
        startTimeIso: String?,
        hasAlarm: Boolean,
    ) {
        val existing = dao.getById(masterId) ?: return
        if (existing.rrule.isNullOrBlank()) {
            editSeries(masterId, title, existing.isAllDay, startTimeIso ?: existing.startDate, existing.rrule, hasAlarm)
            return
        }
        // override.startDate의 날짜 부분은 반드시 occurrenceDate(시각만 변경). 종일/시각없음이면 null.
        val override = EventOverride(
            title = title,
            startDate = overrideStartFor(occurrenceDate, startTimeIso),
            hasAlarm = hasAlarm,
        )
        val updated = existing.copy(
            overridesJson = withOverride(existing.overridesJson, occurrenceDate, override),
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
    }

    // 이후 모든: ① 원본 마스터를 occurrenceDate 전날까지 UNTIL 절단(deleteFollowing과 동일 로직) +
    //            ② occurrenceDate(+새 시각)를 시작으로 하는 새 마스터 생성(새 id, 같은/수정된 rrule, exdates·overrides 비움).
    // 경계: 원본 UNTIL=occurrenceDate-1(inclusive)이라 occurrenceDate 미포함, 새 마스터는 occurrenceDate 포함 → 중복/누락 없음.
    suspend fun editFollowing(
        masterId: String,
        occurrenceDate: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String?,
        hasAlarm: Boolean,
    ) {
        val existing = dao.getById(masterId) ?: return
        if (existing.rrule.isNullOrBlank()) {
            editSeries(masterId, title, isAllDay, startDate, rrule, hasAlarm)
            return
        }
        val occ = runCatching { LocalDate.parse(occurrenceDate) }.getOrNull()
        val start = runCatching { LocalDate.parse(existing.startDate.take(10)) }.getOrNull()
        // occurrenceDate가 시작일 이하면 절단할 과거 발생이 없어 전체 수정과 동일.
        if (occ == null || (start != null && !occ.isAfter(start))) {
            editSeries(masterId, title, isAllDay, startDate, rrule, hasAlarm)
            return
        }
        // ① 원본 절단 (deleteFollowing과 동일 — UNTIL은 inclusive이므로 발생일 전날)
        val until = occ.minusDays(1).format(UNTIL_FORMAT)
        val truncated = existing.copy(
            rrule = withUntil(existing.rrule, until),
            updatedAt = System.currentTimeMillis(),
        )
        save(truncated)
        // ② occurrenceDate부터 시작하는 새 마스터 — exdates/overrides는 비운 채 생성(create가 동기화·알람 처리)
        create(
            userId = existing.userId,
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            rrule = rrule,
            hasAlarm = hasAlarm,
        )
    }

    // 전체: 마스터 직접 수정(기존 update와 같은 save 경로). 단 두 가지를 명확히 처리한다.
    //  · 발생일이 아닌 마스터 원래 시작일(날짜)을 보존 — 합성 발생 startDate로 마스터 시작일을 덮어쓰지 않는다.
    //  · rrule 변경 시 발생 키가 달라져 기존 override/exdate가 무의미해지므로 비워 정합을 유지한다.
    suspend fun editSeries(
        masterId: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String?,
        hasAlarm: Boolean,
    ): EventEntity? {
        val existing = dao.getById(masterId) ?: return null
        val masterDate = existing.startDate.take(10)
        // 마스터 날짜는 보존하고 시각(있으면)만 새 값으로 교체
        val newStart = if (isAllDay) masterDate
        else masterDate + (if (startDate.length > 10) startDate.substring(10) else "")
        val rruleChanged = (existing.rrule ?: "") != (rrule ?: "")
        val updated = existing.copy(
            title = title,
            isAllDay = isAllDay,
            startDate = newStart,
            timezone = if (isAllDay) null else (existing.timezone ?: "Asia/Seoul"),
            rrule = rrule,
            hasAlarm = hasAlarm,
            exdatesJson = if (rruleChanged) null else existing.exdatesJson,
            overridesJson = if (rruleChanged) null else existing.overridesJson,
            updatedAt = System.currentTimeMillis(),
        )
        save(updated)
        return updated
    }

    // override.startDate 정규화: 날짜 부분은 반드시 occurrenceDate(이동 금지). 종일/시각없음(<=10자)이면 null.
    private fun overrideStartFor(occurrenceDate: String, startIso: String?): String? {
        if (startIso.isNullOrBlank() || startIso.length <= 10) return null
        return occurrenceDate + startIso.substring(10)
    }

    // rrule의 UNTIL 파트를 교체하거나 없으면 추가(다른 파트는 보존).
    private fun withUntil(rrule: String, until: String): String {
        val prefix = if (rrule.startsWith("RRULE:")) "RRULE:" else ""
        val parts = rrule.removePrefix("RRULE:")
            .split(';')
            .filter { it.isNotBlank() && !it.startsWith("UNTIL=", ignoreCase = true) }
        return prefix + (parts + "UNTIL=$until").joinToString(";")
    }

    companion object {
        // lib-recur 파싱 가능한 날짜 형식(YYYYMMDD)
        private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    // 원격 push는 앱 스코프에서 비동기 실행 — 오프라인이면 write Task가 서버 ack까지 완료되지
    // 않으므로 호출부를 막지 않는다. 에러는 Crashlytics 기록 후 조용히 실패(로컬은 이미 저장됨).
    private fun syncSafe(block: suspend () -> Unit) {
        appScope.launch {
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
}
