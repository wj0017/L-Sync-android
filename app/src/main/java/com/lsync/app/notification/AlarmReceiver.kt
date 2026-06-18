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

        // 탭 시 MainActivity를 일정 탭으로 띄운다(Step 0 딥링크 메커니즘 재사용).
        // data Uri를 항목별로 고유화 — filterEquals가 extra를 무시하므로 고유 data 없이는
        // 여러 항목 PendingIntent가 하나로 합쳐져 extra가 덮어써진다.
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAV_TARGET, "schedule")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lsync://nav/schedule/$id")
        }
        // requestCode는 id.hashCode()(고정 슬롯). 완료 액션(Step 3)은 id.hashCode()+1을 쓰므로 침범하지 않는다.
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // 할 일 알림에만 "완료" 액션 추가 — 앱을 열지 않고 Todo를 완료 처리한다(TodoActionReceiver).
        // requestCode는 id.hashCode()+1, data는 lsync://complete/$id로 contentIntent와 분리해
        // FLAG_UPDATE_CURRENT가 서로 덮어쓰지 않게 한다.
        if (type == TYPE_TODO) {
            val completeIntent = Intent(context, TodoActionReceiver::class.java).apply {
                putExtra(TodoActionReceiver.EXTRA_TODO_ID, id)
                data = Uri.parse("lsync://complete/$id")
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                id.hashCode() + 1,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_notification, "완료", completePendingIntent)
        }

        manager.notify(id.hashCode(), builder.build())
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
