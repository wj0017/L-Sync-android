package com.lsync.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// 정기 리마인더 설정. 기기별 로컬 설정이라 Firestore 동기화·Room 저장 대상이 아니다.
// 기본값은 전부 off — 사용자가 켜지 않은 알림이 앱 업데이트만으로 울리지 않게 한다.
data class NotificationSettings(
    val bibleEnabled: Boolean = false,
    val bibleHour: Int = 21,
    val bibleMinute: Int = 0,

    val dailySpendingEnabled: Boolean = false,
    val dailySpendingHour: Int = 21,
    val dailySpendingMinute: Int = 30,

    val weeklySpendingEnabled: Boolean = false,
    val weeklySpendingDayOfWeek: Int = 7, // java.time.DayOfWeek.value — 1=월 … 7=일
    val weeklySpendingHour: Int = 20,
    val weeklySpendingMinute: Int = 0,
)

// ReadingPlanRepository(reading_plan_prefs)와 동일한 SharedPreferences 패턴.
// 설정 화면이 구독할 수 있도록 StateFlow를 얹었다.
@Singleton
class NotificationSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "notification_prefs"

        private const val KEY_BIBLE_ENABLED = "bible_enabled"
        private const val KEY_BIBLE_HOUR = "bible_hour"
        private const val KEY_BIBLE_MINUTE = "bible_minute"

        private const val KEY_DAILY_ENABLED = "daily_spending_enabled"
        private const val KEY_DAILY_HOUR = "daily_spending_hour"
        private const val KEY_DAILY_MINUTE = "daily_spending_minute"

        private const val KEY_WEEKLY_ENABLED = "weekly_spending_enabled"
        private const val KEY_WEEKLY_DOW = "weekly_spending_dow"
        private const val KEY_WEEKLY_HOUR = "weekly_spending_hour"
        private const val KEY_WEEKLY_MINUTE = "weekly_spending_minute"
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    fun save(new: NotificationSettings) {
        prefs.edit()
            .putBoolean(KEY_BIBLE_ENABLED, new.bibleEnabled)
            .putInt(KEY_BIBLE_HOUR, new.bibleHour)
            .putInt(KEY_BIBLE_MINUTE, new.bibleMinute)
            .putBoolean(KEY_DAILY_ENABLED, new.dailySpendingEnabled)
            .putInt(KEY_DAILY_HOUR, new.dailySpendingHour)
            .putInt(KEY_DAILY_MINUTE, new.dailySpendingMinute)
            .putBoolean(KEY_WEEKLY_ENABLED, new.weeklySpendingEnabled)
            .putInt(KEY_WEEKLY_DOW, new.weeklySpendingDayOfWeek)
            .putInt(KEY_WEEKLY_HOUR, new.weeklySpendingHour)
            .putInt(KEY_WEEKLY_MINUTE, new.weeklySpendingMinute)
            .apply()
        _settings.value = new
    }

    // 스케줄러는 알람 등록 직전에 최신 값이 필요하다(다른 프로세스/리시버에서 호출될 수 있음).
    fun current(): NotificationSettings = _settings.value

    private fun read(): NotificationSettings {
        val defaults = NotificationSettings()
        val p = prefs
        return NotificationSettings(
            bibleEnabled = p.getBoolean(KEY_BIBLE_ENABLED, defaults.bibleEnabled),
            bibleHour = p.getInt(KEY_BIBLE_HOUR, defaults.bibleHour),
            bibleMinute = p.getInt(KEY_BIBLE_MINUTE, defaults.bibleMinute),
            dailySpendingEnabled = p.getBoolean(KEY_DAILY_ENABLED, defaults.dailySpendingEnabled),
            dailySpendingHour = p.getInt(KEY_DAILY_HOUR, defaults.dailySpendingHour),
            dailySpendingMinute = p.getInt(KEY_DAILY_MINUTE, defaults.dailySpendingMinute),
            weeklySpendingEnabled = p.getBoolean(KEY_WEEKLY_ENABLED, defaults.weeklySpendingEnabled),
            weeklySpendingDayOfWeek = p.getInt(KEY_WEEKLY_DOW, defaults.weeklySpendingDayOfWeek),
            weeklySpendingHour = p.getInt(KEY_WEEKLY_HOUR, defaults.weeklySpendingHour),
            weeklySpendingMinute = p.getInt(KEY_WEEKLY_MINUTE, defaults.weeklySpendingMinute),
        )
    }
}
