package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.remote.FirestoreDataSource
import com.lsync.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val dao: FinanceDao,
    private val remote: FirestoreDataSource,
    private val authRepository: AuthRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val currentUserId: String
        get() = authRepository.currentUserId ?: error("User not signed in")
    fun observeByMonth(yearMonth: String): Flow<List<FinanceEntity>> =
        dao.observeByDateRange("$yearMonth-01", "$yearMonth-31")

    // 다월 범위 거래 원천 (대시보드용). observeByMonth와 동일하게 DAO Flow를 그대로 노출.
    fun observeByDateRange(from: String, to: String): Flow<List<FinanceEntity>> =
        dao.observeByDateRange(from, to)

    fun observeAllSettlementItems(): Flow<List<FinanceEntity>> =
        dao.observeAllSettlementItems()

    suspend fun create(
        type: String,
        amount: Long,
        category: String,
        date: String,
        note: String?,
        settlementGroupId: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val entity = FinanceEntity(
            id = UUID.randomUUID().toString(),
            userId = currentUserId,
            type = type,
            amount = amount,
            category = category,
            date = date,
            note = note,
            sourceTodoId = null,
            isExcluded = false,
            settlementGroupId = settlementGroupId,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        syncSafe { remote.upsertFinance(entity) }
        return entity.id
    }

    suspend fun startSettlement(financeId: String) {
        val entity = dao.getById(financeId) ?: return
        if (entity.settlementGroupId != null) return
        val updated = entity.copy(
            settlementGroupId = UUID.randomUUID().toString(),
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(updated)
        syncSafe { remote.upsertFinance(updated) }
    }

    suspend fun addReimbursement(groupId: String, amount: Long, date: String, note: String?) {
        val now = System.currentTimeMillis()
        val entity = FinanceEntity(
            id = UUID.randomUUID().toString(),
            userId = currentUserId,
            type = "INCOME",
            amount = amount,
            category = FinanceCategory.ETC,
            date = date,
            note = note,
            sourceTodoId = null,
            isExcluded = false,
            settlementGroupId = groupId,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        syncSafe { remote.upsertFinance(entity) }
    }

    suspend fun linkToSettlement(financeId: String, groupId: String) {
        val entity = dao.getById(financeId) ?: return
        val updated = entity.copy(settlementGroupId = groupId, updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        syncSafe { remote.upsertFinance(updated) }
    }

    suspend fun update(
        id: String,
        type: String,
        amount: Long,
        category: String,
        date: String,
        note: String?,
    ) {
        val existing = dao.getById(id) ?: return
        val updated = existing.copy(
            type = type,
            amount = amount,
            category = category,
            date = date,
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(updated)
        syncSafe { remote.upsertFinance(updated) }
    }

    // Todo 연동 항목(sourceTodoId != null)은 직접 삭제 불가 — excludeByTodoId로만 처리해야 함 (CLAUDE.md CRITICAL)
    // 삭제는 tombstone(deletedAt)으로 전파 — hard delete는 pull에서 부활하고 다른 기기에 반영되지 않는다.
    suspend fun delete(id: String) {
        val entity = dao.getById(id)
            ?: return
        if (entity.sourceTodoId != null) {
            throw IllegalStateException("Todo-linked finance cannot be deleted directly. Use excludeByTodoId instead.")
        }
        val now = System.currentTimeMillis()
        // 정산 리더(EXPENSE) 삭제 → 그룹 전체 정리.
        // 정산 입금(INCOME)은 함께 삭제, 단 Todo 연동 입금은 삭제 금지라 그룹 연결만 해제한다.
        val groupId = entity.settlementGroupId
        if (entity.type == "EXPENSE" && groupId != null) {
            dao.getBySettlementGroup(groupId).forEach { member ->
                if (member.id == id) return@forEach
                if (member.sourceTodoId != null) {
                    val unlinked = member.copy(settlementGroupId = null, updatedAt = now)
                    dao.upsert(unlinked)
                    syncSafe { remote.upsertFinance(unlinked) }
                } else {
                    val deletedMember = member.copy(deletedAt = now, updatedAt = now)
                    dao.upsert(deletedMember)
                    syncSafe { remote.upsertFinance(deletedMember) }
                }
            }
        }
        val deleted = entity.copy(deletedAt = now, updatedAt = now)
        dao.upsert(deleted)
        syncSafe { remote.upsertFinance(deleted) }
    }

    suspend fun exportCsv(yearMonth: String): String {
        val rows = dao.getAllByDateRange(currentUserId, "${yearMonth}-01", "${yearMonth}-31")
        val sb = StringBuilder()
        sb.appendLine("날짜,유형,카테고리,금액,메모")
        rows.forEach { f ->
            val typeStr = if (f.type == "INCOME") "수입" else "지출"
            sb.appendLine("${f.date},$typeStr,${csvField(f.category)},${f.amount},${csvField(f.note ?: "")}")
        }
        return sb.toString()
    }

    // RFC 4180 — 쉼표·따옴표·줄바꿈이 든 필드는 따옴표로 감싸고 내부 따옴표는 이중화
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    // 원격 push는 앱 스코프에서 비동기 실행 — 오프라인이면 write Task가 서버 ack까지 완료되지
    // 않으므로 호출부를 막지 않는다. 에러는 Crashlytics 기록 후 조용히 실패(로컬은 이미 저장됨).
    private fun syncSafe(block: suspend () -> Unit) {
        appScope.launch {
            try {
                block()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().apply {
                    setCustomKey("sync_target", "finance")
                    recordException(e)
                    log("sync_failed")
                }
            }
        }
    }
}
