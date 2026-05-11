package com.lsync.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.lsync.app.R
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val parsed = PaymentNotificationParser.parse(packageName, title, text) ?: return

        serviceScope.launch {
            financeRepository.create(
                type = parsed.type,
                amount = parsed.amount,
                category = parsed.category,
                date = LocalDate.now().toString(),
                note = parsed.note,
            )
            postConfirmationNotification(parsed.amount, parsed.type)
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
    }
}
