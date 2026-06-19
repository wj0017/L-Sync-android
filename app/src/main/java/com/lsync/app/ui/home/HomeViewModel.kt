package com.lsync.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.recurrence.EventOccurrence
import com.lsync.app.data.recurrence.expandEvents
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.data.repository.ReadingPlanSettings
import com.lsync.app.data.repository.TodoRepository
import com.lsync.app.ui.schedule.ScheduleItem
import com.lsync.app.ui.widget.WidgetRefreshHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReadingPlanSectionState(
    val entries: List<ReadingPlanEntity> = emptyList(),
    val totalRead: Int = 0,
    val startDate: LocalDate? = null,
    val settings: ReadingPlanSettings = ReadingPlanSettings(),
    val estimatedTotalDays: Int = 365,
    val streak: Int = 0,
    val weeklyHeatmap: List<Boolean> = emptyList(),
) {
    val hasStarted: Boolean get() = startDate != null
    val todayDoneCount: Int get() = entries.count { it.isRead }
    val todayTotal: Int get() = entries.size
    val overallProgress: Float
        get() = if (ReadingPlanRepository.TOTAL_CHAPTERS == 0) 0f
                else totalRead.toFloat() / ReadingPlanRepository.TOTAL_CHAPTERS
    val dayNumber: Int
        get() = startDate?.let {
            java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() + 1
        } ?: 0
}

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val todayItems: List<ScheduleItem> = emptyList(),
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val readingPlan: ReadingPlanSectionState = ReadingPlanSectionState(),
    val showSetupSheet: Boolean = false,
    val pendingSettings: ReadingPlanSettings = ReadingPlanSettings(),
    val error: String? = null,
) {
    val monthNet: Long get() = monthIncome - monthExpense
    val previewItems: List<ScheduleItem> get() = todayItems.take(5)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val todoRepository: TodoRepository,
    private val financeRepository: FinanceRepository,
    private val readingPlanRepository: ReadingPlanRepository,
    private val widgetRefreshHelper: WidgetRefreshHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        viewModelScope.launch { readingPlanRepository.ensureReadingPlanForDate(today) }
        observeTodaySchedule(today)
        observeMonthFinance(today)
        observeReadingPlan(today)
    }

    private fun observeTodaySchedule(today: LocalDate) {
        viewModelScope.launch {
            val dateStr = today.toString()
            combine(
                // 과거 시작 반복 마스터까지 포함 조회 → EventRecurrence로 오늘 발생 전개
                eventRepository.observeForExpansion(dateStr, dateStr),
                todoRepository.observeByDate(dateStr),
            ) { events, todos ->
                buildList {
                    // 오늘 발생을 전개 → 합성 발생 엔티티로 변환(HomeScreen은 item.entity를 읽어 표시)
                    expandEvents(events, dateStr, dateStr).forEach { occ ->
                        add(ScheduleItem.Event(occ.toSyntheticEntity(events), occ.date))
                    }
                    todos.forEach  { add(ScheduleItem.Todo(it)) }
                }.sortedWith(compareBy(
                    { it is ScheduleItem.Todo && it.entity.isCompleted },
                    {
                        when (it) {
                            is ScheduleItem.Event -> it.entity.startDate
                            is ScheduleItem.Todo  -> it.entity.dueDate ?: "9999"
                        }
                    }
                ))
            }.catch { e -> _uiState.update { it.copy(error = e.message) } }
             .collect { items -> _uiState.update { it.copy(todayItems = items) } }
        }
    }

    private fun observeMonthFinance(today: LocalDate) {
        val monthKey = "%04d-%02d".format(today.year, today.monthValue)
        viewModelScope.launch {
            financeRepository.observeByMonth(monthKey)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { list ->
                    _uiState.update {
                        it.copy(
                            monthIncome  = list.filter { f -> f.type == "INCOME" }.sumOf { f -> f.amount },
                            monthExpense = list.filter { f -> f.type == "EXPENSE" }.sumOf { f -> f.amount },
                        )
                    }
                }
        }
    }

    private fun observeReadingPlan(today: LocalDate) {
        viewModelScope.launch {
            readingPlanRepository.observeForDate(today.toString())
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { entries ->
                    val totalRead      = readingPlanRepository.getTotalRead()
                    val settings       = readingPlanRepository.getSettings()
                    val estimatedDays  = readingPlanRepository.estimatedTotalDays()
                    val streak         = readingPlanRepository.getStreak()
                    val weeklyHeatmap  = readingPlanRepository.getWeeklyHeatmap()
                    _uiState.update {
                        it.copy(
                            readingPlan = it.readingPlan.copy(
                                entries            = entries,
                                totalRead          = totalRead,
                                startDate          = readingPlanRepository.getStartDate(),
                                settings           = settings,
                                estimatedTotalDays = estimatedDays,
                                streak             = streak,
                                weeklyHeatmap      = weeklyHeatmap,
                            )
                        )
                    }
                }
        }
    }

    // ── 통독 설정 시트 ────────────────────────────────────────────────────────

    fun openSetupSheet() {
        val current = readingPlanRepository.getSettings()
        _uiState.update { it.copy(showSetupSheet = true, pendingSettings = current) }
    }

    fun closeSetupSheet() = _uiState.update { it.copy(showSetupSheet = false) }

    fun updatePendingSettings(settings: ReadingPlanSettings) =
        _uiState.update { it.copy(pendingSettings = settings) }

    fun applySettings() {
        val settings = _uiState.value.pendingSettings
        val today = LocalDate.now()
        readingPlanRepository.saveSettings(settings)
        if (!_uiState.value.readingPlan.hasStarted) {
            readingPlanRepository.setStartDate(today)
        }
        viewModelScope.launch {
            readingPlanRepository.resetFromToday()
            observeReadingPlan(today)
            widgetRefreshHelper.requestUpdate()
        }
        _uiState.update { it.copy(showSetupSheet = false) }
    }

    // ── 개별 조작 ─────────────────────────────────────────────────────────────

    fun markChapterRead(book: Int, chapter: Int, isRead: Boolean) {
        viewModelScope.launch {
            readingPlanRepository.markRead(LocalDate.now().toString(), book, chapter, isRead)
            widgetRefreshHelper.requestUpdate()
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

// 발생(EventOccurrence)을 마스터 copy 기반 "합성 발생 엔티티"로 변환(ScheduleViewModel과 동일 방식).
// id는 마스터 유지, 표시값(startDate/title/hasAlarm)만 발생 유효값으로 치환.
private fun EventOccurrence.toSyntheticEntity(masters: List<EventEntity>): EventEntity {
    val master = masters.first { it.id == masterId }
    return master.copy(
        startDate = startDate,
        title = title,
        hasAlarm = hasAlarm,
    )
}
