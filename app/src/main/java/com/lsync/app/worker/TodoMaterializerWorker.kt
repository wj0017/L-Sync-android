package com.lsync.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.local.entity.TodoTemplateEntity
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.TodoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

// Client-side Materialization: 반복 Todo 템플릿에서 오늘~+14일 인스턴스 생성 (PRD 2.3)
@HiltWorker
class TodoMaterializerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val todoTemplateDao: TodoTemplateDao,
    private val todoDao: TodoDao,
    private val authRepository: AuthRepository,
    private val todoRepository: TodoRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = authRepository.currentUserId ?: return Result.success()
        return try {
            val today = LocalDate.now()
            val endDate = today.plusDays(WINDOW_DAYS)
            val todayStr = today.toString()

            val templates = todoTemplateDao.getActiveTemplates(userId)
            for (template in templates) {
                try {
                    materializeTemplate(template, today, endDate, todayStr, userId)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping template ${template.id}: ${e.message}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in doWork", e)
            Result.failure()
        }
    }

    private suspend fun materializeTemplate(
        template: TodoTemplateEntity,
        today: LocalDate,
        endDate: LocalDate,
        todayStr: String,
        userId: String,
    ) {
        val rule = RecurrenceRule(template.rrule.removePrefix("RRULE:"))

        // DTSTART: 템플릿 생성일을 기준 시작일로 사용
        val createdDate = Instant.ofEpochMilli(template.createdAt)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val startDt = DateTime(createdDate.toEpochDay() * MS_PER_DAY)

        // 미완료 미래 인스턴스 목록 (업데이트 대상 — 완료된 인스턴스는 제외됨)
        val pendingInstances = todoDao.getPendingFutureByTemplate(template.id, todayStr)
        val pendingById = pendingInstances.associateBy { it.id }

        val now = System.currentTimeMillis()
        val toUpsert = mutableListOf<TodoEntity>()

        val iter = rule.iterator(startDt)
        var safety = 0
        while (iter.hasNext() && safety < MAX_ITERATIONS) {
            safety++
            val nextDate = LocalDate.ofEpochDay(iter.nextMillis() / MS_PER_DAY)

            if (nextDate.isAfter(endDate)) break
            if (nextDate.isBefore(today)) continue

            val dueDateStr = nextDate.toString()
            // 결정론적 ID — 멱등성 보장 (같은 날 여러 번 실행해도 중복 생성 없음)
            val instanceId = "${template.id}_${dueDateStr}"

            val existingPending = pendingById[instanceId]
            if (existingPending != null) {
                // 미완료 기존 인스턴스 → 최신 템플릿 정보로 갱신
                toUpsert.add(
                    existingPending.copy(
                        title = template.title,
                        financeIsLinked = template.financeIsLinked,
                        financeType = template.financeType,
                        financeCategory = template.financeCategory,
                        financeAmount = template.financeAmount,
                        updatedAt = now,
                    )
                )
            } else {
                // 완료된 인스턴스는 덮어쓰지 않음
                if (todoDao.getById(instanceId) != null) continue
                // 신규 인스턴스 생성
                toUpsert.add(
                    TodoEntity(
                        id = instanceId,
                        userId = userId,
                        templateId = template.id,
                        title = template.title,
                        isCompleted = false,
                        dueDate = dueDateStr,
                        completedAt = null,
                        financeIsLinked = template.financeIsLinked,
                        financeType = template.financeType,
                        financeCategory = template.financeCategory,
                        financeAmount = template.financeAmount,
                        linkedFinanceId = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }

        if (toUpsert.isNotEmpty()) {
            todoRepository.upsertMaterialized(toUpsert)
        }
    }

    companion object {
        private const val TAG = "TodoMaterializerWorker"
        private const val MS_PER_DAY = 86400000L
        private const val WINDOW_DAYS = 14L
        private const val MAX_ITERATIONS = 10_000

        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<TodoMaterializerWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "todo_materializer",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
