package com.lsync.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.repository.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CalendarUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<EventEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadEventsForCurrentMonth()
    }

    fun onMonthChange(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadEventsForMonth(month)
    }

    fun onDateSelect(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun createEvent(
        userId: String,
        title: String,
        isAllDay: Boolean,
        startDate: String,
        endDate: String? = null,
        rrule: String? = null,
        hasAlarm: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                repository.create(
                    userId = userId,
                    title = title,
                    isAllDay = isAllDay,
                    startDate = startDate,
                    endDate = endDate,
                    rrule = rrule,
                    hasAlarm = hasAlarm,
                )
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun loadEventsForCurrentMonth() = loadEventsForMonth(YearMonth.now())

    private fun loadEventsForMonth(month: YearMonth) {
        viewModelScope.launch {
            val from = month.atDay(1).toString()
            val to = month.atEndOfMonth().toString()
            repository.observeByDateRange(from, to)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { events -> _uiState.update { it.copy(events = events) } }
        }
    }
}
