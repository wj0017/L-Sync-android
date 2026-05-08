package com.lsync.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.repository.TodoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// Client-side Materialization: 반복 Todo 템플릿에서 +14일치 인스턴스 생성 (PRD 2.3)
@HiltWorker
class TodoMaterializerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val todoDao: TodoDao,
    private val todoRepository: TodoRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val templates = todoDao.observeActiveTemplates()
        // TODO: RRULE 파서 라이브러리(예: dmfs/lib-recur) 추가 후 구현
        // 각 템플릿의 rrule을 파싱 → 오늘~+14일 발생 날짜 목록 생성
        // → 결정론적 ID "${templateId}_${dueDate}"로 upsert (중복 방지)
        return Result.success()
    }

    companion object {
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
