package com.lsync.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.FinanceRepository
import com.lsync.app.data.repository.ReadingPlanRepository
import com.lsync.app.data.repository.TodoRepository
import com.lsync.app.ui.schedule.ScheduleItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReadingPlanSectionState(
    val entries: List<ReadingPlanEntity> = emptyList(),
    val totalRead: Int = 0,
    val startDate: LocalDate? = null,
) {
    val hasStarted: Boolean get() = startDate != null
    val todayDoneCount: Int get() = entries.count { it.isRead }
    val todayTotal: Int get() = entries.size
    val overallProgress: Float
        get() = if (ReadingPlanRepository.TOTAL_CHAPTERS == 0) 0f
                else totalRead.toFloat() / ReadingPlanRepository.TOTAL_CHAPTERS
}

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val todayItems: List<ScheduleItem> = emptyList(),
    val monthIncome: Long = 0L,
    val monthExpense: Long = 0L,
    val readingPlan: ReadingPlanSectionState = ReadingPlanSectionState(),
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
            combine(
                eventRepository.observeByDateRange(today.toString(), today.toString()),
                todoRepository.observeByDate(today.toString()),
            ) { events, todos ->
                buildList {
                    events.forEach { add(ScheduleItem.Event(it)) }
                    todos.forEach  { add(ScheduleItem.Todo(it)) }
                }.sortedWith(compareBy(
                    { it is ScheduleItem.Todo && (it as ScheduleItem.Todo).entity.isCompleted },
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
                    val totalRead = readingPlanRepository.getTotalRead()
                    _uiState.update {
                        it.copy(
                            readingPlan = it.readingPlan.copy(
                                entries   = entries,
                                totalRead = totalRead,
                                startDate = readingPlanRepository.getStartDate(),
                            )
                        )
                    }
                }
        }
    }

    fun markChapterRead(book: Int, chapter: Int, isRead: Boolean) {
        viewModelScope.launch {
            readingPlanRepository.markRead(LocalDate.now().toString(), book, chapter, isRead)
        }
    }

    fun startReadingPlan() {
        val today = LocalDate.now()
        readingPlanRepository.setStartDate(today)
        viewModelScope.launch {
            readingPlanRepository.ensureReadingPlanForDate(today)
            observeReadingPlan(today)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
