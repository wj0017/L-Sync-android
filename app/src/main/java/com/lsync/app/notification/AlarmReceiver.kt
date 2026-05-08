package com.lsync.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lsync.app.R
import com.lsync.app.notification.AlarmScheduler.Companion.CHANNEL_ID
import com.lsync.app.notification.AlarmScheduler.Companion.EXTRA_ID
import com.lsync.app.notification.AlarmScheduler.Companion.EXTRA_TITLE
import com.lsync.app.notification.AlarmScheduler.Companion.EXTRA_TYPE
import com.lsync.app.notification.AlarmScheduler.Companion.TYPE_TODO

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return

        val notificationText = if (type == TYPE_TODO) "마감일이 다가왔습니다" else "일정 시작"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(id.hashCode(), notification)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "일정 및 할 일 알림",
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }
}
