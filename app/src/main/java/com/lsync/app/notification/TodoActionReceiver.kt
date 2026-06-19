package com.lsync.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.lsync.app.MainActivity
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.repository.TodoRepository
import com.lsync.app.di.ApplicationScope
import com.lsync.app.ui.widget.WidgetRefreshHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

// 할 일 알림의 "완료" 액션 — 앱을 열지 않고 Todo를 완료 처리하고 알림을 지운다.
// 단, 가계부 연동인데 금액 미정인 Todo는 헤드리스 완료 시 ₩0 Finance가 생성되므로
// (PRD 2.2 "금액 미정 시 UI 팝업 강제") 완료하지 않고 앱(일정 탭)으로 유도한다.
@AndroidEntryPoint
class TodoActionReceiver : BroadcastReceiver() {

    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var todoDao: TodoDao
    @Inject lateinit var widgetRefreshHelper: WidgetRefreshHelper

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
        // suspend 작업(Room+Firestore) 동안 프로세스가 죽지 않도록 유지. finally에서 finish().
        val pending = goAsync()
        scope.launch {
            try {
                val todo = todoDao.getById(todoId)
                // 중복 완료 가드 — 없거나 이미 완료면 no-op (중복 Finance 생성 방지)
                if (todo == null || todo.isCompleted) return@launch

                if (todo.financeIsLinked && todo.financeAmount == null) {
                    // 헤드리스 완료 금지 → 앱으로 유도해 금액 입력을 받는다.
                    context.startActivity(navIntentToSchedule(context))
                } else {
                    // 미연동이거나 금액이 사전 지정된 경우만 헤드리스 완료.
                    todoRepository.complete(todo)
                    // setAutoCancel은 액션 버튼에 적용되지 않으므로 명시적 취소.
                    // notify ID는 AlarmReceiver가 쓴 id.hashCode()와 동일해야 한다.
                    NotificationManagerCompat.from(context).cancel(todoId.hashCode())
                    widgetRefreshHelper.requestUpdate()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun navIntentToSchedule(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAV_TARGET, "schedule")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    companion object {
        const val EXTRA_TODO_ID = "todo_action_id"
    }
}
