package com.lsync.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.lsync.app.data.repository.NotificationSettings
import com.lsync.app.data.repository.NotificationSettingsRepository
import com.lsync.app.notification.ReminderReceiver
import com.lsync.app.notification.ReminderScheduler
import com.lsync.app.notification.ReminderType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: NotificationSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val uiState: StateFlow<NotificationSettings> = settingsRepository.settings

    // 설정이 바뀌면 즉시 알람을 재등록/취소한다(syncAll은 멱등).
    private fun update(transform: (NotificationSettings) -> NotificationSettings) {
        settingsRepository.save(transform(settingsRepository.current()))
        reminderScheduler.syncAll()
    }

    fun setBibleEnabled(enabled: Boolean) = update { it.copy(bibleEnabled = enabled) }

    fun setBibleTime(hour: Int, minute: Int) = update { it.copy(bibleHour = hour, bibleMinute = minute) }

    fun setDailySpendingEnabled(enabled: Boolean) = update { it.copy(dailySpendingEnabled = enabled) }

    fun setDailySpendingTime(hour: Int, minute: Int) =
        update { it.copy(dailySpendingHour = hour, dailySpendingMinute = minute) }

    fun setWeeklySpendingEnabled(enabled: Boolean) = update { it.copy(weeklySpendingEnabled = enabled) }

    fun setWeeklySpendingDay(dayOfWeek: Int) = update { it.copy(weeklySpendingDayOfWeek = dayOfWeek) }

    fun setWeeklySpendingTime(hour: Int, minute: Int) =
        update { it.copy(weeklySpendingHour = hour, weeklySpendingMinute = minute) }

    // 시각까지 기다리지 않고 문구·딥링크를 확인하기 위한 즉시 발송.
    // 켜져 있는 리마인더만 보낸다. 리시버가 exported=false여도 앱 내부 명시적 브로드캐스트는 동작한다.
    fun sendPreview() {
        val s = settingsRepository.current()
        buildList {
            if (s.bibleEnabled) add(ReminderType.BIBLE)
            if (s.dailySpendingEnabled) add(ReminderType.SPENDING_DAILY)
            if (s.weeklySpendingEnabled) add(ReminderType.SPENDING_WEEKLY)
        }.forEach { type ->
            context.sendBroadcast(
                Intent(context, ReminderReceiver::class.java).apply {
                    putExtra(ReminderScheduler.EXTRA_REMINDER_TYPE, type.key)
                },
            )
        }
    }
}
