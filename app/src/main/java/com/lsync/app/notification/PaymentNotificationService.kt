package com.lsync.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lsync.app.R
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.FinanceRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class PaymentNotificationService : NotificationListenerService() {

    @Inject lateinit var financeRepository: FinanceRepository
    @Inject lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 같은 알림의 갱신/재게시(잠금 해제, 그룹 재정렬 등)로 인한 중복 기록 방지용 최근 키 캐시
    private val recentKeys = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 진행 중·그룹 요약 알림은 결제 원본이 아님 — 파싱하면 중복 기록된다
        if (sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val parsed = PaymentNotificationParser.parse(packageName, title, text) ?: return

        // 리스너 권한은 로그아웃 후에도 살아 있다 — 미로그인 상태면 기록 불가(userId 없음)
        if (authRepository.currentUserId == null) return

        val key = "$packageName|$title|$text"
        val now = System.currentTimeMillis()
        synchronized(recentKeys) {
            recentKeys.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
            if (recentKeys.containsKey(key)) return
            recentKeys[key] = now
        }

        serviceScope.launch {
            // 예외가 새면 프로세스가 죽는다(코루틴 uncaught) — 기록 실패는 Crashlytics로만 남긴다
            runCatching {
                financeRepository.create(
                    type = parsed.type,
                    amount = parsed.amount,
                    category = parsed.category,
                    date = LocalDate.now().toString(),
                    note = parsed.note,
                )
                postConfirmationNotification(parsed.amount, parsed.type)
            }.onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun postConfirmationNotification(amount: Long, type: String) {
        ensurePaymentChannel()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val label = if (type == "INCOME") "입금" else "결제"
        val notification = NotificationCompat.Builder(this, PAYMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$label 자동 등록됨")
            .setContentText("%,d원 가계부에 추가되었습니다".format(amount))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(PAYMENT_CHANNEL_ID.hashCode(), notification)
    }

    private fun ensurePaymentChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(PAYMENT_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            PAYMENT_CHANNEL_ID,
            PAYMENT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val PAYMENT_CHANNEL_ID = "lsync_payment_auto"
        const val PAYMENT_CHANNEL_NAME = "결제 자동 등록"
        private const val DEDUP_WINDOW_MS = 10 * 60_000L
    }
}
