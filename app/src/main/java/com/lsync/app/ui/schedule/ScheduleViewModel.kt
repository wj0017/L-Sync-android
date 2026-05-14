package com.lsync.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// 홈 화면과 공유하는 통합 일정 아이템
sealed class ScheduleItem {
    data class Event(val entity: EventEntity) : ScheduleItem()
    data class Todo(val entity: TodoEntity)   : ScheduleItem()
}

data class ScheduleUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthEvents: List<EventEntity> = emptyList(),
    val todoDateSet: Set<String> = emptySet(),
    val dayItems: List<ScheduleItem> = emptyList(),
    val pendingFinanceTodo: TodoEntity? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val eventDateSet: Set<String> get() = monthEvents.map { it.startDate.take(10) }.toSet()
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var monthEventsJob: Job? = null
    private var dayItemsJob: Job? = null
    private var todoDateJob: Job? = null

    init {
        val today = LocalDate.now()
        loadMonthData(YearMonth.now())
        observeDayItems(today)
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadMonthData(month)
    }

    fun onDateSelect(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        observeDayItems(date)
    }

    private fun loadMonthData(month: YearMonth) {
        val from = month.atDay(1).toString()
        val to   = month.atEndOfMonth().toString()

        monthEventsJob?.cancel()
        monthEventsJob = viewModelScope.launch {
            eventRepository.observeByDateRange(from, to)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { events -> _uiState.update { it.copy(monthEvents = events) } }
        }

        todoDateJob?.cancel()
        todoDateJob = viewModelScope.launch {
            todoRepository.observeAll()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { todos ->
                    val set = todos
                        .filter { it.dueDate != null && it.dueDate >= from && it.dueDate <= to }
                        .mapNotNull { it.dueDate }
                        .toSet()
                    _uiState.update { it.copy(todoDateSet = set) }
                }
        }
    }

    private fun observeDayItems(date: LocalDate) {
        dayItemsJob?.cancel()
        dayItemsJob = viewModelScope.launch {
            combine(
                eventRepository.observeByDateRange(date.toString(), date.toString()),
                todoRepository.observeByDate(date.toString()),
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
             .collect { items -> _uiState.update { it.copy(dayItems = items) } }
        }
    }

    // ── 이벤트 CRUD ──────────────────────────────────────────────────────────

    fun createEvent(
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String? = null,
        hasAlarm: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                eventRepository.create(
                    userId = "local_user",
                    title = title,
                    isAllDay = isAllDay,
                    startDate = startDate,
                    rrule = rrule,
                    hasAlarm = hasAlarm,
                )
            }.onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
             .onSuccess { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun deleteEvent(id: String) = viewModelScope.launch { eventRepository.delete(id) }

    // ── 투두 CRUD ─────────────────────────────────────────────────────────────

    fun createTodo(
        title: String,
        dueDate: String? = null,
        financeIsLinked: Boolean = false,
        financeType: String? = null,
        financeCategory: String? = null,
        financeAmount: Long? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                todoRepository.create(
                    userId = "local_user",
                    title = title,
                    dueDate = dueDate,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
            }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleComplete(todo: TodoEntity) {
        viewModelScope.launch {
            if (todo.isCompleted) {
                todoRepository.uncheck(todo)
            } else {
                if (todo.financeIsLinked && todo.financeAmount == null) {
                    _uiState.update { it.copy(pendingFinanceTodo = todo) }
                } else {
                    todoRepository.complete(todo)
                        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                }
            }
        }
    }

    fun confirmFinanceAndComplete(amount: Long) {
        val todo = _uiState.value.pendingFinanceTodo ?: return
        viewModelScope.launch {
            todoRepository.complete(todo, amount)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(pendingFinanceTodo = null) }
        }
    }

    fun dismissFinancePopup() = _uiState.update { it.copy(pendingFinanceTodo = null) }

    fun deleteTodo(todo: TodoEntity) = viewModelScope.launch { todoRepository.delete(todo) }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
