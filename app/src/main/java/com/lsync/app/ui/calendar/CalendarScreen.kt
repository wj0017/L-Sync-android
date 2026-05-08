package com.lsync.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Header: year (uppercase) + month (large)
        CalendarHeader(
            month = uiState.selectedMonth,
            onAddClick = { showCreateDialog = true },
        )

        // Calendar grid
        CalendarGrid(
            currentMonth = uiState.selectedMonth,
            selectedDate = uiState.selectedDate,
            eventsMap = uiState.events.groupBy { it.startDate.take(10) },
            onMonthChange = viewModel::onMonthChange,
            onDateClick = viewModel::onDateSelect,
        )

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(1.dp)
                .background(Divider)
        )

        // Date label (meta uppercase style)
        Text(
            text = uiState.selectedDate
                .format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
                .uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = FgTertiary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        // Event list
        if (selectedDateEvents.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("일정 없음", fontSize = 14.sp, color = FgDisabled)
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
            .padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            // Year — uppercase tracking label
            Text(
                text = month.year.toString(),
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.12.em,
                color = FgTertiary,
            )
            Spacer(Modifier.height(6.dp))
            // Month — large display title
            Text(
                text = "${month.monthValue}월",
                fontFamily = Pretendard,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = (-0.035).em,
                color = FgPrimary,
                lineHeight = (30 * 1.08).sp,
            )
        }
        // Icon button with hairline
        IconButton(
            onClick = onAddClick,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(BgCard)
                .border(1.dp, HairlineWhite, CircleShape),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "일정 추가",
                tint = FgPrimary,
                modifier = Modifier.size(20.dp),
            )
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
        // DOW header: Sunday = AccentRed80, rest = FgDisabled
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            val days = listOf("일", "월", "화", "수", "목", "금", "토")
            days.forEachIndexed { i, day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        color = if (i == 0) AccentRed80 else FgDisabled,
                    )
                }
            }
        }

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
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (isSelected) FgPrimary else Color.Transparent)
            .clickable(enabled = isCurrentMonth, onClick = onClick),
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            fontFamily = Pretendard,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = when {
                isSelected      -> BgPrimary
                !isCurrentMonth -> FgDisabled
                isToday         -> AccentBlue
                isSunday        -> AccentRed80
                else            -> FgPrimary
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

// Card with hairline border (premium detail)
@Composable
private fun EventCard(event: EventEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    val timeText = if (event.isAllDay) "종일"
    else event.startDate.substringAfter("T").take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(AccentBlue)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = event.title,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = (-0.005).em,
                color = FgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = timeText.uppercase(),
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.12.em,
                color = FgTertiary,
            )
        }
        IconButton(
            onClick = { showConfirm = true },
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "삭제",
                tint = FgDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showConfirm) {
        LSyncDialog(
            title = "일정 삭제",
            body = "'${event.title}'을(를) 삭제할까요?",
            confirmLabel = "삭제",
            isDanger = true,
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false },
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

    LSyncInputDialog(
        title = "새 일정",
        onDismiss = onDismiss,
        onConfirm = {
            if (title.isNotBlank()) {
                val startDate = if (isAllDay) selectedDate.toString()
                else "${selectedDate}T09:00:00+09:00"
                onConfirm(title, isAllDay, startDate, null, hasAlarm)
            }
        },
        confirmEnabled = title.isNotBlank(),
    ) {
        LSyncField(label = "제목", value = title, onValueChange = { title = it })
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            LSyncCheckbox(label = "종일", checked = isAllDay, onCheckedChange = { isAllDay = it })
            LSyncCheckbox(label = "알림", checked = hasAlarm, onCheckedChange = { hasAlarm = it })
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")),
            fontSize = 12.sp,
            color = FgSecondary,
            fontFamily = Pretendard,
        )
    }
}

// ── Shared dialog primitives ──────────────────────────────────────────────────

@Composable
fun LSyncDialog(
    title: String, body: String, confirmLabel: String,
    isDanger: Boolean = false,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.018).em, color = FgPrimary) },
        text  = { Text(body,  fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary, lineHeight = (13 * 1.55).sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isDanger) AccentRed else AccentBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
            }
        },
    )
}

@Composable
fun LSyncInputDialog(
    title: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.018).em, color = FgPrimary) },
        text  = { Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { content() } },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text("추가", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (confirmEnabled) AccentBlue else FgDisabled)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
            }
        },
    )
}

@Composable
fun LSyncField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = Pretendard) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        textStyle = LocalTextStyle.current.copy(fontFamily = Pretendard, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue,
            focusedLabelColor = AccentBlue,
            cursorColor = AccentBlue,
            unfocusedBorderColor = Divider,
            unfocusedLabelColor = FgSecondary,
            focusedTextColor = FgPrimary,
            unfocusedTextColor = FgPrimary,
        ),
    )
}

@Composable
fun LSyncCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCheckedChange(!checked) }) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) AccentBlue else Color.Transparent)
                .then(if (!checked) Modifier.border(1.5.dp, FgSecondary, RoundedCornerShape(3.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", fontSize = 11.sp, color = Color.White, fontFamily = Pretendard)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
    }
}
