package com.lsync.app.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var showEventDialog by remember { mutableStateOf(false) }
    var showTodoDialog  by remember { mutableStateOf(false) }
    var fabExpanded     by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgPrimary,
        floatingActionButton = {
            ExpandableFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onAddEvent = { fabExpanded = false; showEventDialog = true },
                onAddTodo  = { fabExpanded = false; showTodoDialog  = true },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgPrimary),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = uiState.selectedMonth.year.toString(),
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = FgTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${uiState.selectedMonth.monthValue}월",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 30.sp,
                        letterSpacing = (-0.035).em,
                        color = FgPrimary,
                        lineHeight = (30 * 1.08).sp,
                    )
                }
            }

            // 캘린더 그리드
            ScheduleCalendarGrid(
                currentMonth   = uiState.selectedMonth,
                selectedDate   = uiState.selectedDate,
                eventDateSet   = uiState.eventDateSet,
                todoDateSet    = uiState.todoDateSet,
                onMonthChange  = viewModel::onMonthChange,
                onDateClick    = viewModel::onDateSelect,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .height(1.dp)
                    .background(Divider)
            )

            Text(
                text = uiState.selectedDate
                    .format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
                    .uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = FgTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            if (uiState.dayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("일정·할 일 없음", fontSize = 14.sp, color = FgDisabled, fontFamily = Pretendard)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.dayItems, key = {
                        when (it) {
                            is ScheduleItem.Event -> "e_${it.entity.id}"
                            is ScheduleItem.Todo  -> "t_${it.entity.id}"
                        }
                    }) { item ->
                        when (item) {
                            is ScheduleItem.Event -> EventCard(
                                event = item.entity,
                                onDelete = { viewModel.deleteEvent(item.entity.id) },
                            )
                            is ScheduleItem.Todo -> TodoItemCard(
                                todo = item.entity,
                                onToggle = { viewModel.toggleComplete(item.entity) },
                                onDelete = { viewModel.deleteTodo(item.entity) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Finance 금액 팝업
    uiState.pendingFinanceTodo?.let { todo ->
        FinanceAmountDialog(
            todoTitle = todo.title,
            category = todo.financeCategory ?: "미분류",
            onConfirm = viewModel::confirmFinanceAndComplete,
            onDismiss = viewModel::dismissFinancePopup,
        )
    }

    if (showEventDialog) {
        CreateEventDialog(
            selectedDate = uiState.selectedDate,
            onConfirm = { title, isAllDay, startDate, rrule, hasAlarm ->
                viewModel.createEvent(title, isAllDay, startDate, rrule, hasAlarm)
                showEventDialog = false
            },
            onDismiss = { showEventDialog = false },
        )
    }

    if (showTodoDialog) {
        CreateTodoDialog(
            defaultDate = uiState.selectedDate.toString(),
            onConfirm = { title, dueDate, linked, type, category, amount ->
                viewModel.createTodo(title, dueDate, linked, type, category, amount)
                showTodoDialog = false
            },
            onDismiss = { showTodoDialog = false },
        )
    }
}

// ── 캘린더 그리드 ──────────────────────────────────────────────────────────────

@Composable
private fun ScheduleCalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    eventDateSet: Set<String>,
    todoDateSet: Set<String>,
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
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, day ->
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
                ScheduleDayCell(
                    day        = day,
                    isSelected = day.date == selectedDate,
                    isToday    = day.date == LocalDate.now(),
                    hasEvent   = eventDateSet.contains(day.date.toString()),
                    hasTodo    = todoDateSet.contains(day.date.toString()),
                    onClick    = { if (day.position == DayPosition.MonthDate) onDateClick(day.date) },
                )
            },
        )
    }
}

@Composable
private fun ScheduleDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    hasTodo: Boolean,
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
        if (isCurrentMonth && (hasEvent || hasTodo)) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasEvent) Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) BgPrimary else AccentBlue))
                if (hasTodo)  Box(Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) BgPrimary else AccentGreen))
            }
        }
    }
}

// ── 아이템 카드 ───────────────────────────────────────────────────────────────

@Composable
private fun EventCard(event: EventEntity, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val timeText = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(AccentBlue)
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(event.title, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = (-0.005).em, color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(timeText.uppercase(), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.12.em, color = FgTertiary)
        }
        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = FgDisabled, modifier = Modifier.size(16.dp))
        }
    }

    if (showConfirm) {
        LSyncDialog(
            title = "일정 삭제", body = "'${event.title}'을(를) 삭제할까요?",
            confirmLabel = "삭제", isDanger = true,
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun TodoItemCard(todo: TodoEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (todo.isCompleted) AccentBlue else Color.Transparent)
                .then(if (!todo.isCompleted) Modifier.border(1.5.dp, FgDisabled, CircleShape) else Modifier)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (todo.isCompleted) Text("✓", fontFamily = Pretendard, fontSize = 11.sp, color = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                fontFamily = Pretendard,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = (-0.005).em,
                color = if (todo.isCompleted) FgDisabled else FgPrimary,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val metaParts = buildList {
                todo.dueDate?.let { add(it) }
                if (todo.financeIsLinked) {
                    val amt = todo.financeAmount?.let { "₩%,d".format(it) } ?: "금액 미정"
                    add("${if (todo.financeType == "INCOME") "수입" else "지출"} · $amt")
                }
            }
            if (metaParts.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = metaParts.joinToString("  ·  ").uppercase(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.12.em,
                    color = if (todo.financeIsLinked && todo.financeAmount == null) AccentRed80 else FgTertiary,
                )
            }
        }
        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Delete, contentDescription = "삭제", tint = FgDisabled, modifier = Modifier.size(16.dp))
        }
    }

    if (showConfirm) {
        LSyncDialog(
            title = "할 일 삭제", body = "'${todo.title}'을(를) 삭제할까요?\n연결된 가계부는 유지됩니다.",
            confirmLabel = "삭제", isDanger = true,
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false },
        )
    }
}

// ── FAB ───────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandableFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddEvent: () -> Unit,
    onAddTodo: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFabItem(label = "할 일 추가", icon = { Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(18.dp)) }, onClick = onAddTodo)
                SmallFabItem(label = "일정 추가", icon = { Icon(Icons.Outlined.CalendarMonth, null, modifier = Modifier.size(18.dp)) }, onClick = onAddEvent)
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = AccentBlue,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(if (expanded) Icons.Outlined.Close else Icons.Outlined.Add, contentDescription = "추가 메뉴")
        }
    }
}

@Composable
private fun SmallFabItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FgPrimary)
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = BgCard,
            contentColor = FgPrimary,
            shape = CircleShape,
            modifier = Modifier.border(1.dp, HairlineWhite, CircleShape),
        ) { icon() }
    }
}

// ── 다이얼로그 (CalendarScreen·TodoScreen에서 이전) ────────────────────────────

@Composable
private fun CreateEventDialog(
    selectedDate: LocalDate,
    onConfirm: (String, Boolean, String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(true) }
    var hasAlarm by remember { mutableStateOf(false) }

    LSyncInputDialog(title = "새 일정", onDismiss = onDismiss,
        onConfirm = {
            if (title.isNotBlank()) {
                val startDate = if (isAllDay) selectedDate.toString() else "${selectedDate}T09:00:00+09:00"
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
        Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), fontSize = 12.sp, color = FgSecondary, fontFamily = Pretendard)
    }
}

@Composable
private fun CreateTodoDialog(
    defaultDate: String,
    onConfirm: (String, String?, Boolean, String?, String?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title           by remember { mutableStateOf("") }
    var dueDate         by remember { mutableStateOf(defaultDate) }
    var financeLinked   by remember { mutableStateOf(false) }
    var financeType     by remember { mutableStateOf("EXPENSE") }
    var financeCategory by remember { mutableStateOf("") }
    var financeAmount   by remember { mutableStateOf("") }

    LSyncInputDialog(title = "새 할 일", confirmEnabled = title.isNotBlank(),
        onConfirm = {
            onConfirm(title, dueDate.ifBlank { null }, financeLinked,
                if (financeLinked) financeType else null,
                if (financeLinked) financeCategory.ifBlank { null } else null,
                if (financeLinked) financeAmount.toLongOrNull() else null)
        },
        onDismiss = onDismiss,
    ) {
        LSyncField(label = "제목", value = title, onValueChange = { title = it })
        Spacer(Modifier.height(10.dp))
        LSyncField(label = "마감일 (YYYY-MM-DD)", value = dueDate, onValueChange = { dueDate = it })
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { financeLinked = !financeLinked }) {
            Box(
                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (financeLinked) AccentBlue else Color.Transparent)
                    .then(if (!financeLinked) Modifier.border(1.5.dp, FgSecondary, RoundedCornerShape(3.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) { if (financeLinked) Text("✓", fontSize = 11.sp, color = Color.White, fontFamily = Pretendard) }
            Spacer(Modifier.width(8.dp))
            Text("가계부 연동", fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
        }
        if (financeLinked) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EXPENSE" to "지출", "INCOME" to "수입").forEach { (type, label) ->
                    val sel = financeType == type
                    Box(
                        modifier = Modifier.clip(CircleShape)
                            .background(if (sel) FgPrimary else Color.Transparent)
                            .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                            .clickable { financeType = type }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.005.em, color = if (sel) BgPrimary else FgSecondary) }
                }
            }
            Spacer(Modifier.height(10.dp))
            LSyncField(label = "카테고리", value = financeCategory, onValueChange = { financeCategory = it })
            Spacer(Modifier.height(10.dp))
            LSyncField(label = "금액 (미정이면 비워두세요)", value = financeAmount, onValueChange = { financeAmount = it.filter(Char::isDigit) })
        }
    }
}

@Composable
private fun FinanceAmountDialog(
    todoTitle: String, category: String,
    onConfirm: (Long) -> Unit, onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    LSyncInputDialog(title = "금액 입력", confirmEnabled = amountText.isNotBlank(),
        onConfirm = { amountText.toLongOrNull()?.let { onConfirm(it) } },
        onDismiss = onDismiss,
    ) {
        Text("'$todoTitle'", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgPrimary)
        Spacer(Modifier.height(2.dp))
        Text("카테고리: $category", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary)
        Spacer(Modifier.height(14.dp))
        LSyncField(label = "금액 (원)", value = amountText, onValueChange = { amountText = it.filter(Char::isDigit) })
    }
}

// ── 공용 다이얼로그 프리미티브 (CalendarScreen에서 이전) ──────────────────────

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
        text  = { Text(body, fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary, lineHeight = (13 * 1.55).sp) },
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
        value = value, onValueChange = onValueChange,
        label = { Text(label, fontFamily = Pretendard) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        textStyle = LocalTextStyle.current.copy(fontFamily = Pretendard, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue, focusedLabelColor = AccentBlue, cursorColor = AccentBlue,
            unfocusedBorderColor = Divider, unfocusedLabelColor = FgSecondary,
            focusedTextColor = FgPrimary, unfocusedTextColor = FgPrimary,
        ),
    )
}

@Composable
fun LSyncCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onCheckedChange(!checked) }) {
        Box(
            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(3.dp))
                .background(if (checked) AccentBlue else Color.Transparent)
                .then(if (!checked) Modifier.border(1.5.dp, FgSecondary, RoundedCornerShape(3.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) { if (checked) Text("✓", fontSize = 11.sp, color = Color.White, fontFamily = Pretendard) }
        Spacer(Modifier.width(8.dp))
        Text(label, fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
    }
}
