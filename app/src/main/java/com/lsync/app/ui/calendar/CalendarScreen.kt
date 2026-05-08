package com.lsync.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.lsync.app.data.local.entity.EventEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    val selectedDateEvents = remember(uiState.events, uiState.selectedDate) {
        val dateStr = uiState.selectedDate.toString()
        uiState.events.filter { event ->
            if (event.isAllDay) event.startDate == dateStr
            else event.startDate.startsWith(dateStr)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("캘린더") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "일정 추가")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            CalendarView(
                currentMonth = uiState.selectedMonth,
                selectedDate = uiState.selectedDate,
                eventsMap = uiState.events.groupBy { it.startDate.take(10) },
                onMonthChange = viewModel::onMonthChange,
                onDateClick = viewModel::onDateSelect,
            )

            HorizontalDivider()

            EventList(
                events = selectedDateEvents,
                onDelete = viewModel::deleteEvent,
            )
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            selectedDate = uiState.selectedDate,
            onConfirm = { title, isAllDay, startDate, rrule, hasAlarm ->
                viewModel.createEvent(
                    userId = "local_user", // TODO: Firebase Auth 연동 후 교체
                    title = title,
                    isAllDay = isAllDay,
                    startDate = startDate,
                    rrule = rrule,
                    hasAlarm = hasAlarm,
                )
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun CalendarView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    eventsMap: Map<String, List<EventEntity>>,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
) {
    val startMonth = remember { YearMonth.now().minusMonths(12) }
    val endMonth = remember { YearMonth.now().plusMonths(12) }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeekFromLocale(),
    )

    LaunchedEffect(state.firstVisibleMonth) {
        onMonthChange(state.firstVisibleMonth.yearMonth)
    }

    HorizontalCalendar(
        state = state,
        dayContent = { day ->
            CalendarDayCell(
                day = day,
                isSelected = day.date == selectedDate,
                hasEvent = eventsMap.containsKey(day.date.toString()),
                onClick = { if (day.position == DayPosition.MonthDate) onDateClick(day.date) },
            )
        },
        monthHeader = { month ->
            Text(
                text = month.yearMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    )
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
) {
    val isCurrentMonth = day.position == DayPosition.MonthDate
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(enabled = isCurrentMonth, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !isCurrentMonth -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasEvent && isCurrentMonth) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun EventList(events: List<EventEntity>, onDelete: (String) -> Unit) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("일정이 없습니다", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    LazyColumn {
        items(events, key = { it.id }) { event ->
            EventItem(event = event, onDelete = { onDelete(event.id) })
        }
    }
}

@Composable
private fun EventItem(event: EventEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(event.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingContent = {
            TextButton(onClick = { showConfirm = true }) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp),
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("일정 삭제") },
            text = { Text("'${event.title}'을(를) 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun CreateEventDialog(
    selectedDate: LocalDate,
    onConfirm: (title: String, isAllDay: Boolean, startDate: String, rrule: String?, hasAlarm: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(true) }
    var hasAlarm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 일정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    Text("종일")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = hasAlarm, onCheckedChange = { hasAlarm = it })
                    Text("알림")
                }
                Text(
                    text = "날짜: $selectedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val startDate = if (isAllDay) selectedDate.toString()
                        else "${selectedDate}T09:00:00+09:00"
                        onConfirm(title, isAllDay, startDate, null, hasAlarm)
                    }
                }
            ) { Text("추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
