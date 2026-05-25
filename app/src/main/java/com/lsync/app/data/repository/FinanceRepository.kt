package com.lsync.app.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.data.local.FinanceCategory
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.remote.FirestoreDataSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val dao: FinanceDao,
    private val remote: FirestoreDataSource,
) {
    fun observeByMonth(yearMonth: String): Flow<List<FinanceEntity>> =
        dao.observeByDateRange("$yearMonth-01", "$yearMonth-31")

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
            userId = "local_user",
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
            userId = "local_user",
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
    suspend fun delete(id: String) {
        val entity = dao.getById(id)
            ?: return
        if (entity.sourceTodoId != null) {
            throw IllegalStateException("Todo-linked finance cannot be deleted directly. Use excludeByTodoId instead.")
        }
        // 정산 리더(EXPENSE) 삭제 → 그룹 전체 정리.
        // 정산 입금(INCOME)은 함께 삭제, 단 Todo 연동 입금은 삭제 금지라 그룹 연결만 해제한다.
        val groupId = entity.settlementGroupId
        if (entity.type == "EXPENSE" && groupId != null) {
            dao.getBySettlementGroup(groupId).forEach { member ->
                if (member.id == id) return@forEach
                if (member.sourceTodoId != null) {
                    val unlinked = member.copy(settlementGroupId = null, updatedAt = System.currentTimeMillis())
                    dao.upsert(unlinked)
                    syncSafe { remote.upsertFinance(unlinked) }
                } else {
                    dao.deleteById(member.id)
                    syncSafe { remote.deleteFinance(member.id) }
                }
            }
        }
        dao.deleteById(id)
        syncSafe { remote.deleteFinance(id) }
    }

    suspend fun exportCsv(yearMonth: String): String {
        val rows = dao.getAllByDateRange("local_user", "${yearMonth}-01", "${yearMonth}-31")
        val sb = StringBuilder()
        sb.appendLine("날짜,유형,카테고리,금액,메모")
        rows.forEach { f ->
            val typeStr = if (f.type == "INCOME") "수입" else "지출"
            sb.appendLine("${f.date},$typeStr,${f.category},${f.amount},${f.note ?: ""}")
        }
        return sb.toString()
    }

    private suspend fun syncSafe(block: suspend () -> Unit) {
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
