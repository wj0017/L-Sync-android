package com.lsync.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.local.entity.TodoTemplateEntity
import com.lsync.app.data.recurrence.EventOccurrence
import com.lsync.app.data.recurrence.expandEvents
import com.lsync.app.data.repository.AuthRepository
import com.lsync.app.data.repository.EventRepository
import com.lsync.app.data.repository.TodoRepository
import com.lsync.app.ui.widget.WidgetRefreshHelper
import com.lsync.app.worker.MaterializationTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// 반복 일정 삭제 범위
enum class DeleteScope { THIS, FOLLOWING, ALL }

// 반복 일정 수정 범위
enum class EditScope { THIS, FOLLOWING, ALL }

// 홈 화면과 공유하는 통합 일정 아이템
sealed class ScheduleItem {
    // entity: 반복 발생 시 마스터를 copy한 "합성 발생 엔티티"(id는 마스터 유지). 비반복은 원본.
    // occurrenceDate: 이 발생의 날짜(YYYY-MM-DD). 비반복은 null.
    data class Event(
        val entity: EventEntity,
        val occurrenceDate: String? = null,
    ) : ScheduleItem()
    data class Todo(val entity: TodoEntity)   : ScheduleItem()
}

data class ScheduleUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthOccurrences: List<EventOccurrence> = emptyList(),
    val todoDateSet: Set<String> = emptySet(),
    val dayItems: List<ScheduleItem> = emptyList(),
    val templates: List<TodoTemplateEntity> = emptyList(),
    val pendingFinanceTodo: TodoEntity? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val eventDateSet: Set<String> get() = monthOccurrences.map { it.date }.toSet()
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val todoRepository: TodoRepository,
    private val authRepository: AuthRepository,
    private val widgetRefreshHelper: WidgetRefreshHelper,
    private val materializationTrigger: MaterializationTrigger,
) : ViewModel() {
    private val currentUserId: String
        get() = authRepository.currentUserId ?: error("User not signed in")

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private var monthEventsJob: Job? = null
    private var dayItemsJob: Job? = null
    private var todoDateJob: Job? = null

    init {
        val today = LocalDate.now()
        loadMonthData(YearMonth.now())
        observeDayItems(today)
        observeTemplates()
    }

    private fun observeTemplates() {
        viewModelScope.launch {
            todoRepository.observeActiveTemplates(currentUserId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { templates -> _uiState.update { it.copy(templates = templates) } }
        }
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
            // 과거 시작 반복 마스터까지 포함해 조회 → EventRecurrence로 월 범위 전개
            eventRepository.observeForExpansion(from, to)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { events ->
                    val occurrences = expandEvents(events, from, to)
                    _uiState.update { it.copy(monthOccurrences = occurrences) }
                }
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
            val dateStr = date.toString()
            combine(
                eventRepository.observeForExpansion(dateStr, dateStr),
                todoRepository.observeByDate(dateStr),
            ) { events, todos ->
                buildList {
                    // 해당 날짜의 발생을 전개 → 합성 발생 엔티티로 변환(EventCard 무수정 표시)
                    expandEvents(events, dateStr, dateStr).forEach { occ ->
                        add(ScheduleItem.Event(occ.toSyntheticEntity(events), occ.date))
                    }
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
                    userId = currentUserId,
                    title = title,
                    isAllDay = isAllDay,
                    startDate = startDate,
                    rrule = rrule,
                    hasAlarm = hasAlarm,
                )
            }.onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
             .onSuccess { _uiState.update { it.copy(isLoading = false) }; widgetRefreshHelper.requestUpdate() }
        }
    }

    fun updateEvent(
        id: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String? = null,
        hasAlarm: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                eventRepository.update(
                    id = id,
                    title = title,
                    isAllDay = isAllDay,
                    startDate = startDate,
                    rrule = rrule,
                    hasAlarm = hasAlarm,
                )
            }.onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
             .onSuccess { _uiState.update { it.copy(isLoading = false) }; widgetRefreshHelper.requestUpdate() }
        }
    }

    // 반복 일정 수정 범위 위임: THIS=이 발생만(override) / FOLLOWING=이후 모든(원본 절단+새 마스터) / ALL=전체(마스터 직접 수정).
    // occurrenceDate가 null이면(비반복 발생) 전체 수정으로 폴백. 동기화·알람은 Repository의 save/create에 위임.
    fun updateEvent(
        masterId: String,
        scope: EditScope,
        occurrenceDate: String?,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        rrule: String?,
        hasAlarm: Boolean,
    ) = viewModelScope.launch {
        runCatching {
            when (scope) {
                EditScope.THIS ->
                    occurrenceDate?.let { eventRepository.editOccurrence(masterId, it, title, startDate, hasAlarm) }
                        ?: eventRepository.editSeries(masterId, title, isAllDay, startDate, rrule, hasAlarm)
                EditScope.FOLLOWING ->
                    occurrenceDate?.let { eventRepository.editFollowing(masterId, it, title, isAllDay, startDate, rrule, hasAlarm) }
                        ?: eventRepository.editSeries(masterId, title, isAllDay, startDate, rrule, hasAlarm)
                EditScope.ALL -> eventRepository.editSeries(masterId, title, isAllDay, startDate, rrule, hasAlarm)
            }
        }.onSuccess { widgetRefreshHelper.requestUpdate() }
         .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    // 비반복 일정 또는 반복 마스터 전체 삭제(기존 호출부 호환).
    fun deleteEvent(id: String) = deleteEvent(id, DeleteScope.ALL, null)

    // 반복 일정 삭제 범위 위임: THIS=이 발생만 / FOLLOWING=이후 모든 / ALL=전체.
    fun deleteEvent(masterId: String, scope: DeleteScope, occurrenceDate: String?) =
        viewModelScope.launch {
            when (scope) {
                DeleteScope.THIS ->
                    occurrenceDate?.let { eventRepository.deleteOccurrence(masterId, it) }
                        ?: eventRepository.deleteSeries(masterId)
                DeleteScope.FOLLOWING ->
                    occurrenceDate?.let { eventRepository.deleteFollowing(masterId, it) }
                        ?: eventRepository.deleteSeries(masterId)
                DeleteScope.ALL -> eventRepository.deleteSeries(masterId)
            }
            widgetRefreshHelper.requestUpdate()
        }

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
                    userId = currentUserId,
                    title = title,
                    dueDate = dueDate,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
            }.onSuccess { widgetRefreshHelper.requestUpdate() }
             .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun updateTodo(
        id: String,
        title: String,
        dueDate: String?,
        financeIsLinked: Boolean = false,
        financeType: String? = null,
        financeCategory: String? = null,
        financeAmount: Long? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                todoRepository.update(
                    id = id,
                    title = title,
                    dueDate = dueDate,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
            }.onSuccess { widgetRefreshHelper.requestUpdate() }
             .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleComplete(todo: TodoEntity) {
        viewModelScope.launch {
            if (todo.isCompleted) {
                todoRepository.uncheck(todo)
                widgetRefreshHelper.requestUpdate()
            } else {
                if (todo.financeIsLinked && todo.financeAmount == null) {
                    _uiState.update { it.copy(pendingFinanceTodo = todo) }
                } else {
                    todoRepository.complete(todo)
                        .onSuccess { widgetRefreshHelper.requestUpdate() }
                        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                }
            }
        }
    }

    fun confirmFinanceAndComplete(amount: Long) {
        val todo = _uiState.value.pendingFinanceTodo ?: return
        viewModelScope.launch {
            todoRepository.complete(todo, amount)
                .onSuccess { widgetRefreshHelper.requestUpdate() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(pendingFinanceTodo = null) }
        }
    }

    fun dismissFinancePopup() = _uiState.update { it.copy(pendingFinanceTodo = null) }

    fun deleteTodo(todo: TodoEntity) = viewModelScope.launch { todoRepository.delete(todo); widgetRefreshHelper.requestUpdate() }

    // ── 반복 Todo 템플릿 ───────────────────────────────────────────────────────
    // rrule은 호출자(UI)가 buildRrule로 만들어 넘긴다.

    fun createTemplate(
        title: String,
        rrule: String,
        financeIsLinked: Boolean = false,
        financeType: String? = null,
        financeCategory: String? = null,
        financeAmount: Long? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                todoRepository.createTemplate(
                    userId = currentUserId,
                    title = title,
                    rrule = rrule,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
            }.onSuccess {
                // 일 1회 주기 실행과 별개로 즉시 1회 인스턴스 생성
                materializationTrigger.runNow()
                widgetRefreshHelper.requestUpdate()
            }.onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // 데이터 영속성 정책: hard delete가 아닌 비활성화(soft)
    fun deleteTemplate(id: String) = viewModelScope.launch {
        todoRepository.deactivateTemplate(id)
        widgetRefreshHelper.requestUpdate()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

// 발생(EventOccurrence)을 마스터 copy 기반 "합성 발생 엔티티"로 변환.
// id는 마스터 유지(편집/삭제가 마스터를 찾음), 표시값(startDate/title/hasAlarm)만 발생 유효값으로 치환.
private fun EventOccurrence.toSyntheticEntity(masters: List<EventEntity>): EventEntity {
    val master = masters.first { it.id == masterId }
    return master.copy(
        startDate = startDate,
        title = title,
        hasAlarm = hasAlarm,
    )
}
