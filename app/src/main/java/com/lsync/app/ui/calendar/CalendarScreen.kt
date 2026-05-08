package com.lsync.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    val selectedDateEvents = remember(uiState.events, uiState.selectedDate) {
        val dateStr = uiState.selectedDate.toString()
        uiState.events.filter { it.startDate.startsWith(dateStr) }
    }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column {
            // 헤더
            CalendarHeader(
                month = uiState.selectedMonth,
                onAddClick = { showCreateDialog = true },
            )

            // 캘린더 그리드
            CalendarGrid(
                currentMonth = uiState.selectedMonth,
                selectedDate = uiState.selectedDate,
                eventsMap = uiState.events.groupBy { it.startDate.take(10) },
                onMonthChange = viewModel::onMonthChange,
                onDateClick = viewModel::onDateSelect,
            )

            // 날짜 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(1.dp)
                    .background(Divider)
            )

            // 선택된 날짜 레이블
            Text(
                text = uiState.selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            // 이벤트 목록
            if (selectedDateEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("일정 없음", style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(selectedDateEvents, key = { it.id }) { event ->
                        EventCard(event = event, onDelete = { viewModel.deleteEvent(event.id) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateEventDialog(
            selectedDate = uiState.selectedDate,
            onConfirm = { title, isAllDay, startDate, rrule, hasAlarm ->
                viewModel.createEvent(
                    userId = "local_user",
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
}

@Composable
private fun CalendarHeader(month: YearMonth, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("yyyy")),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Text(
                text = month.format(DateTimeFormatter.ofPattern("M월")),
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
            )
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BgCard),
        ) {
            Icon(Icons.Default.Add, contentDescription = "일정 추가", tint = TextPrimary)
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    eventsMap: Map<String, List<EventEntity>>,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
) {
    val startMonth = remember { YearMonth.now().minusMonths(12) }
    val endMonth   = remember { YearMonth.now().plusMonths(12) }
    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeekFromLocale(),
    )

    LaunchedEffect(state.firstVisibleMonth) {
        onMonthChange(state.firstVisibleMonth.yearMonth)
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        // 요일 헤더
        Row(modifier = Modifier.fillMaxWidth()) {
            val daysOfWeek = listOf("일", "월", "화", "수", "목", "금", "토")
            daysOfWeek.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        HorizontalCalendar(
            state = state,
            dayContent = { day ->
                DayCell(
                    day = day,
                    isSelected = day.date == selectedDate,
                    isToday = day.date == LocalDate.now(),
                    hasEvent = eventsMap.containsKey(day.date.toString()),
                    onClick = { if (day.position == DayPosition.MonthDate) onDateClick(day.date) },
                )
            },
        )
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
) {
    val isCurrentMonth = day.position == DayPosition.MonthDate
    val isSunday = day.date.dayOfWeek == DayOfWeek.SUNDAY

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> TextPrimary
                    else       -> Color.Transparent
                }
            )
            .clickable(enabled = isCurrentMonth, onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                isSelected     -> BgPrimary
                !isCurrentMonth -> TextDisabled
                isToday        -> AccentBlue
                isSunday       -> AccentRed.copy(alpha = 0.8f)
                else           -> TextPrimary
            },
        )
        if (hasEvent && isCurrentMonth) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) BgPrimary else AccentBlue)
            )
        }
    }
}

@Composable
private fun EventCard(event: EventEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    val timeText = if (event.isAllDay) "종일"
    else event.startDate.substringAfter("T").take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽 컬러 바
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(AccentBlue)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        IconButton(onClick = { showConfirm = true }) {
            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextDisabled, modifier = Modifier.size(18.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = BgCard,
            title = { Text("일정 삭제", color = TextPrimary) },
            text = { Text("'${event.title}'을(를) 삭제할까요?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showConfirm = false }) {
                    Text("삭제", color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("취소", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun CreateEventDialog(
    selectedDate: LocalDate,
    onConfirm: (String, Boolean, String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(true) }
    var hasAlarm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("새 일정", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        focusedLabelColor = AccentBlue,
                        cursorColor = AccentBlue,
                        unfocusedBorderColor = Divider,
                        unfocusedLabelColor = TextSecondary,
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentBlue, uncheckedColor = TextSecondary),
                        )
                        Text("종일", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hasAlarm,
                            onCheckedChange = { hasAlarm = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentBlue, uncheckedColor = TextSecondary),
                        )
                        Text("알림", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
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
            ) { Text("추가", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary) }
        },
    )
}
