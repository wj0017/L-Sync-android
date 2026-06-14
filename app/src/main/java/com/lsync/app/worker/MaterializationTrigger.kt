package com.lsync.app.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// 템플릿 생성 직후 인스턴스를 즉시 1회 materialization (Materializer 주기 실행과 별개)
@Singleton
class MaterializationTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<TodoMaterializerWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "todo_materializer_now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
