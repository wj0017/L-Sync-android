package com.lsync.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.lsync.app.MainActivity
import com.lsync.app.R
import com.lsync.app.data.local.BibleBookNames
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.expenseSum
import com.lsync.app.data.local.netExpense
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

// 정기 리마인더(통독·소비 요약) 발화 지점.
// AlarmManager는 one-shot이므로 발화할 때마다 ReminderScheduler로 다음 회차를 재무장한다.
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var readingPlanRepository: ReadingPlanRepository
    @Inject lateinit var readingPlanDao: ReadingPlanDao
    @Inject lateinit var financeDao: FinanceDao
    @Inject lateinit var budgetDao: BudgetDao
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        // Hilt(@AndroidEntryPoint)는 생성된 부모 클래스의 onReceive에서 필드를 주입한다.
        // super 호출을 빠뜨리면 아래 lateinit이 초기화되지 않아 UninitializedPropertyAccessException.
        super.onReceive(context, intent)

        val type = ReminderType.fromKey(intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TYPE))
            ?: return

        // 알림을 보내지 않는 경우에도 다음 회차는 반드시 등록한다(리마인더 체인이 끊기지 않게).
        reminderScheduler.scheduleNext(type)

        // Room 조회(suspend) 동안 프로세스가 죽지 않도록 유지. finally에서 finish().
        val pending = goAsync()
        scope.launch {
            try {
                val content = when (type) {
                    ReminderType.BIBLE -> buildBibleContent()
                    ReminderType.SPENDING_DAILY -> buildDailySpendingContent()
                    ReminderType.SPENDING_WEEKLY -> buildWeeklySpendingContent()
                } ?: return@launch

                notify(context, type, content)
            } finally {
                pending.finish()
            }
        }
    }

    // ── 알림 내용 ─────────────────────────────────────────────────────────────

    private data class Content(val title: String, val body: String)

    // 오늘 통독 분량이 남아 있을 때만 알린다. 다 읽은 날엔 보내지 않는다(잔소리 방지).
    private suspend fun buildBibleContent(): Content? {
        // 통독 미시작이면 알릴 것이 없다.
        if (readingPlanRepository.getStartDate() == null) return null

        val today = LocalDate.now()
        // 앱을 열지 않은 날은 reading_plan row가 아직 없다(materialize가 HomeViewModel에만 걸려 있음).
        readingPlanRepository.ensureReadingPlanForDate(today)

        val entries = readingPlanDao.getForDate(today.toString())
        if (entries.isEmpty()) return null // 완주했거나 계산 범위 밖

        val remaining = entries.filter { !it.isRead }
        if (remaining.isEmpty()) return null // 오늘 분량 완료

        val chapters = remaining.joinToString(" · ") { "${BibleBookNames.full(it.book)} ${it.chapter}장" }
        // 오늘은 아직 안 읽은 시점이므로 "어제까지" 이어온 연속을 센다.
        val streak = readingPlanRepository.getStreakAsOf(today.minusDays(1))
        val body = if (streak >= 2) "$chapters · 연속 ${streak}일 이어가는 중" else chapters

        return Content(title = "오늘 통독 ${remaining.size}장 남았어요", body = body)
    }

    private suspend fun buildDailySpendingContent(): Content? {
        val userId = authRepository.currentUserId ?: return null // 로그아웃 상태 — 조용히 스킵
        val today = LocalDate.now()
        val todayStr = today.toString()

        val todayRows = financeDao.getAllByDateRange(userId, todayStr, todayStr)
        val todayExpense = todayRows.netExpense()

        val ym = todayStr.take(7)
        val monthRows = financeDao.getAllByDateRange(userId, "$ym-01", "$ym-31")
        val monthExpense = monthRows.netExpense()

        val title = if (todayExpense > 0) "오늘 ${won(todayExpense)} 썼어요" else "오늘은 지출이 없어요"
        val ratio = budgetRatio(userId, monthExpense)
        val body = if (ratio != null) {
            "이번 달 ${won(monthExpense)} · 예산의 ${ratio}%"
        } else {
            "이번 달 ${won(monthExpense)}"
        }

        return Content(title, body)
    }

    // 구간을 "최근 7일 vs 직전 7일"로 잡는다. 사용자가 어떤 요일을 골라도 항상 같은 길이라
    // 비교가 성립한다(월~오늘로 잡으면 수요일 설정 시 3일치만 집계됨).
    private suspend fun buildWeeklySpendingContent(): Content? {
        val userId = authRepository.currentUserId ?: return null
        val today = LocalDate.now()

        val recent = financeDao.getAllByDateRange(userId, today.minusDays(6).toString(), today.toString())
        val previous = financeDao.getAllByDateRange(
            userId, today.minusDays(13).toString(), today.minusDays(7).toString(),
        )

        val recentExpense = recent.netExpense()
        val previousExpense = previous.netExpense()

        val parts = mutableListOf<String>()
        if (previousExpense > 0) {
            val delta = ((recentExpense - previousExpense) * 100.0 / previousExpense).toInt()
            val sign = if (delta >= 0) "+" else ""
            parts += "직전 7일 대비 $sign$delta%"
        }
        topCategory(recent)?.let { (category, amount) -> parts += "$category ${won(amount)} 최다" }

        val ym = today.toString().take(7)
        val monthExpense = financeDao.getAllByDateRange(userId, "$ym-01", "$ym-31").netExpense()
        parts += "이번 달 ${won(monthExpense)}"

        return Content(
            title = "최근 7일 ${won(recentExpense)} 썼어요",
            body = parts.joinToString(" · "),
        )
    }

    // 전체 월 한도(__TOTAL__) 대비 사용률. 예산 미설정이면 null.
    private suspend fun budgetRatio(userId: String, monthExpense: Long): Int? {
        val budget = budgetDao.getById("${userId}_${BudgetEntity.TOTAL_CATEGORY}") ?: return null
        if (budget.deletedAt != null || budget.limitAmount <= 0) return null
        return (monthExpense * 100 / budget.limitAmount).toInt()
    }

    // 카테고리 집계는 EXPENSE 원금 기준(정산분을 카테고리에서 차감하지 않는다 — PRD 2.4).
    private fun topCategory(rows: List<FinanceEntity>): Pair<String, Long>? =
        rows.filter { it.type == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, items) -> items.expenseSum() }
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.let { it.key to it.value }

    // ── 알림 게시 ─────────────────────────────────────────────────────────────

    private fun notify(context: Context, type: ReminderType, content: Content) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = when (type) {
            ReminderType.BIBLE -> ReminderScheduler.CHANNEL_BIBLE
            else -> ReminderScheduler.CHANNEL_SPENDING
        }
        ensureChannel(manager, channelId)

        val navTarget = if (type == ReminderType.BIBLE) "bible" else "finance"
        val notifyId = when (type) {
            ReminderType.BIBLE -> ReminderScheduler.NOTIFY_BIBLE
            ReminderType.SPENDING_DAILY -> ReminderScheduler.NOTIFY_SPENDING_DAILY
            ReminderType.SPENDING_WEEKLY -> ReminderScheduler.NOTIFY_SPENDING_WEEKLY
        }

        // data Uri를 타입별로 고유화 — filterEquals가 extra를 무시하므로 고유 data 없이는
        // 세 알림의 PendingIntent가 하나로 합쳐져 extra가 덮어써진다(AlarmReceiver와 같은 이유).
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAV_TARGET, navTarget)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lsync://nav/$navTarget/${type.key}")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifyId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setPriority(
                if (type == ReminderType.BIBLE) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_LOW,
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(notifyId, notification)
    }

    // 통독은 행동 유도라 소리, 소비 요약은 정보성이라 무음. 채널이 분리돼야 따로 끌 수 있다.
    private fun ensureChannel(manager: NotificationManager, channelId: String) {
        if (manager.getNotificationChannel(channelId) != null) return
        val channel = when (channelId) {
            ReminderScheduler.CHANNEL_BIBLE -> NotificationChannel(
                channelId, "성경 통독 리마인더", NotificationManager.IMPORTANCE_DEFAULT,
            )
            else -> NotificationChannel(
                channelId, "소비 요약", NotificationManager.IMPORTANCE_LOW,
            )
        }
        manager.createNotificationChannel(channel)
    }

    private fun won(amount: Long): String =
        "₩" + NumberFormat.getNumberInstance(Locale.KOREA).format(amount)
}
