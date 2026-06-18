package com.lsync.app.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Repeat
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
import com.lsync.app.data.local.entity.TodoTemplateEntity
import com.lsync.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var showEventDialog  by remember { mutableStateOf(false) }
    var showTodoDialog   by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    var fabExpanded      by remember { mutableStateOf(false) }
    var editingEvent     by remember { mutableStateOf<EventEntity?>(null) }
    var editingEventDate by remember { mutableStateOf<String?>(null) }   // 편집 중 발생일(반복 범위 위임용)
    var pendingEventEdit by remember { mutableStateOf<PendingEventEdit?>(null) }
    var editingTodo      by remember { mutableStateOf<TodoEntity?>(null) }

    Scaffold(
        containerColor = BgPrimary,
        floatingActionButton = {
            ExpandableFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onAddEvent  = { fabExpanded = false; showEventDialog  = true },
                onAddTodo   = { fabExpanded = false; showTodoDialog   = true },
                onAddRepeat = { fabExpanded = false; showRepeatDialog = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgPrimary),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Header
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 14.dp, top = 24.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "${uiState.selectedMonth.year}년 ${uiState.selectedMonth.monthValue}월",
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            letterSpacing = (-0.035).em,
                            color = FgPrimary,
                            lineHeight = (26 * 1.08).sp,
                        )
                    }
                }
            }

            // 캘린더 그리드
            item(key = "calendar") {
                ScheduleCalendarGrid(
                    currentMonth   = uiState.selectedMonth,
                    selectedDate   = uiState.selectedDate,
                    eventDateSet   = uiState.eventDateSet,
                    todoDateSet    = uiState.todoDateSet,
                    onMonthChange  = viewModel::onMonthChange,
                    onDateClick    = viewModel::onDateSelect,
                )
            }

            item(key = "divider") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .height(1.dp)
                        .background(Divider)
                )
            }

            item(key = "date_label") {
                Text(
                    text = uiState.selectedDate
                        .format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
                        .uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = FgTertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (uiState.dayItems.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("일정·할 일 없음", fontSize = 14.sp, color = FgDisabled, fontFamily = Pretendard)
                    }
                }
            } else {
                items(uiState.dayItems, key = {
                    when (it) {
                        is ScheduleItem.Event -> "e_${it.entity.id}"
                        is ScheduleItem.Todo  -> "t_${it.entity.id}"
                    }
                }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        when (item) {
                            is ScheduleItem.Event -> EventCard(
                                event = item.entity,
                                isRecurring = item.entity.rrule != null,
                                onEdit = { editingEvent = item.entity; editingEventDate = item.occurrenceDate },
                                onDelete = { scope ->
                                    viewModel.deleteEvent(item.entity.id, scope, item.occurrenceDate)
                                },
                            )
                            is ScheduleItem.Todo -> TodoItemCard(
                                todo = item.entity,
                                onToggle = { viewModel.toggleComplete(item.entity) },
                                onEdit = { editingTodo = item.entity },
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

    if (showRepeatDialog) {
        CreateRepeatTodoDialog(
            templates = uiState.templates,
            onConfirm = { title, rrule, linked, type, category, amount ->
                viewModel.createTemplate(title, rrule, linked, type, category, amount)
                showRepeatDialog = false
            },
            onDelete = { id -> viewModel.deleteTemplate(id) },
            onDismiss = { showRepeatDialog = false },
        )
    }

    // 편집 다이얼로그 — 생성 다이얼로그를 prefill하여 재사용
    editingEvent?.let { event ->
        val occDate = editingEventDate
        CreateEventDialog(
            selectedDate = LocalDate.parse(event.startDate.take(10)),
            initial = event,
            onConfirm = { title, isAllDay, startDate, rrule, hasAlarm ->
                if (event.rrule != null) {
                    // 반복 일정 — 확정 후 수정 범위를 물어 위임(이 일정만/이후 모든/전체)
                    pendingEventEdit = PendingEventEdit(event.id, occDate, title, isAllDay, startDate, rrule, hasAlarm)
                } else {
                    // 비반복 — 기존 update 경로(범위 없음)
                    viewModel.updateEvent(event.id, title, isAllDay, startDate, rrule, hasAlarm)
                }
                editingEvent = null
            },
            onDismiss = { editingEvent = null },
        )
    }

    // 반복 일정 수정 범위 다이얼로그(반복 발생 편집 확정 후)
    pendingEventEdit?.let { req ->
        EditScopeDialog(
            title = req.title,
            onSelect = { scope ->
                viewModel.updateEvent(req.masterId, scope, req.occurrenceDate, req.title, req.isAllDay, req.startDate, req.rrule, req.hasAlarm)
                pendingEventEdit = null
            },
            onDismiss = { pendingEventEdit = null },
        )
    }

    editingTodo?.let { todo ->
        CreateTodoDialog(
            defaultDate = todo.dueDate ?: uiState.selectedDate.toString(),
            initial = todo,
            onConfirm = { title, dueDate, linked, type, category, amount ->
                viewModel.updateTodo(todo.id, title, dueDate, linked, type, category, amount)
                editingTodo = null
            },
            onDismiss = { editingTodo = null },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    event: EventEntity,
    isRecurring: Boolean,
    onEdit: () -> Unit,
    onDelete: (DeleteScope) -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val timeText = if (event.isAllDay) "종일" else event.startDate.substringAfter("T").take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = {}, onLongClick = onEdit),
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
        if (isRecurring) {
            // 반복 일정 — 삭제 범위 선택(이 일정만 / 이후 모든 / 전체)
            DeleteScopeDialog(
                title = event.title,
                onSelect = { scope -> onDelete(scope); showConfirm = false },
                onDismiss = { showConfirm = false },
            )
        } else {
            LSyncDialog(
                title = "일정 삭제", body = "'${event.title}'을(를) 삭제할까요?",
                confirmLabel = "삭제", isDanger = true,
                onConfirm = { onDelete(DeleteScope.ALL); showConfirm = false },
                onDismiss = { showConfirm = false },
            )
        }
    }
}

// 반복 일정 삭제 범위 선택 다이얼로그(이 일정만 / 이후 모든 일정 / 전체 일정)
@Composable
private fun DeleteScopeDialog(
    title: String,
    onSelect: (DeleteScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = { Text("반복 일정 삭제", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.018).em, color = FgPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("'${title}'", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary, lineHeight = (13 * 1.55).sp)
                Spacer(Modifier.height(8.dp))
                DeleteScopeRow("이 일정만") { onSelect(DeleteScope.THIS) }
                DeleteScopeRow("이후 모든 일정") { onSelect(DeleteScope.FOLLOWING) }
                DeleteScopeRow("전체 일정", isDanger = true) { onSelect(DeleteScope.ALL) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
            }
        },
    )
}

// 반복 일정 편집 확정값 보관 — 범위 선택까지 잠시 들고 있다가 ViewModel로 위임.
private data class PendingEventEdit(
    val masterId: String,
    val occurrenceDate: String?,
    val title: String,
    val isAllDay: Boolean,
    val startDate: String,
    val rrule: String?,
    val hasAlarm: Boolean,
)

// 반복 일정 수정 범위 선택 다이얼로그(이 일정만 / 이후 모든 일정 / 전체 일정)
@Composable
private fun EditScopeDialog(
    title: String,
    onSelect: (EditScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = { Text("반복 일정 수정", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.018).em, color = FgPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("'${title}'", fontFamily = Pretendard, fontSize = 13.sp, color = FgSecondary, lineHeight = (13 * 1.55).sp)
                Spacer(Modifier.height(8.dp))
                DeleteScopeRow("이 일정만") { onSelect(EditScope.THIS) }
                DeleteScopeRow("이후 모든 일정") { onSelect(EditScope.FOLLOWING) }
                DeleteScopeRow("전체 일정") { onSelect(EditScope.ALL) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgSecondary)
            }
        },
    )
}

@Composable
private fun DeleteScopeRow(label: String, isDanger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = Pretendard,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = if (isDanger) AccentRed else FgPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoItemCard(todo: TodoEntity, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onEdit)
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
    onAddRepeat: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it },
            exit  = fadeOut() + slideOutVertically { it },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFabItem(label = "반복 할 일", icon = { Icon(Icons.Outlined.Repeat, null, modifier = Modifier.size(18.dp)) }, onClick = onAddRepeat)
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
    initial: EventEntity? = null,
    onConfirm: (String, Boolean, String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title    by remember { mutableStateOf(initial?.title ?: "") }
    var isAllDay by remember { mutableStateOf(initial?.isAllDay ?: true) }
    var hasAlarm by remember { mutableStateOf(initial?.hasAlarm ?: false) }

    // 반복 설정 — 편집 진입 시 기존 rrule을 역파싱해 prefill
    val initialOption = remember(initial) { initial?.rrule?.let { parseRrule(it) } }
    var repeatEnabled by remember { mutableStateOf(initialOption != null) }
    var frequency     by remember { mutableStateOf(initialOption?.frequency ?: Frequency.WEEKLY) }
    var intervalText  by remember { mutableStateOf((initialOption?.interval ?: 1).toString()) }
    var weekdays      by remember { mutableStateOf(initialOption?.weekdays ?: emptySet()) }

    val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val option = RecurrenceOption(
        frequency = frequency,
        interval = interval,
        weekdays = if (frequency == Frequency.WEEKLY) weekdays else emptySet(),
    )

    // 편집 시 기존 시간 부분 보존(생성은 09:00 기본)
    val timePart = initial?.startDate
        ?.let { if (it.contains("T")) it.substringAfter("T") else null }
        ?: "09:00:00+09:00"

    LSyncInputDialog(
        title = if (initial != null) "일정 수정" else "새 일정",
        confirmLabel = if (initial != null) "수정" else "추가",
        onDismiss = onDismiss,
        onConfirm = {
            if (title.isNotBlank()) {
                val startDate = if (isAllDay) selectedDate.toString() else "${selectedDate}T$timePart"
                val rrule = if (repeatEnabled) buildRrule(option) else null
                onConfirm(title, isAllDay, startDate, rrule, hasAlarm)
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

        // 반복 설정 — 켜면 CreateRepeatTodoDialog와 동일한 빈도/간격/요일 피커 노출
        Spacer(Modifier.height(12.dp))
        LSyncCheckbox(label = "반복", checked = repeatEnabled, onCheckedChange = { repeatEnabled = it })
        if (repeatEnabled) {
            Spacer(Modifier.height(12.dp))
            // 빈도: 매일/매주/매월/매년 — ghost chip (active = FgPrimary solid)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Frequency.DAILY to "매일", Frequency.WEEKLY to "매주",
                    Frequency.MONTHLY to "매월", Frequency.YEARLY to "매년",
                ).forEach { (freq, label) ->
                    val sel = frequency == freq
                    Box(
                        modifier = Modifier.clip(CircleShape)
                            .background(if (sel) FgPrimary else Color.Transparent)
                            .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                            .clickable { frequency = freq }
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (sel) BgPrimary else FgSecondary) }
                }
            }
            Spacer(Modifier.height(10.dp))

            // 간격(INTERVAL): N마다
            LSyncField(label = "간격 (N마다 반복)", value = intervalText, onValueChange = { intervalText = it.filter(Char::isDigit) })

            // 요일(BYDAY): 매주일 때만 노출. 멀티선택, 미선택 시 BYDAY 생략
            if (frequency == Frequency.WEEKLY) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    Weekday.values().forEach { wd ->
                        val sel = wd in weekdays
                        Box(
                            modifier = Modifier.weight(1f).clip(CircleShape)
                                .background(if (sel) FgPrimary else Color.Transparent)
                                .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                                .clickable { weekdays = if (sel) weekdays - wd else weekdays + wd }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(weekdayLabel(wd), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (sel) BgPrimary else FgSecondary) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(describeRrule(buildRrule(option)), fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary)
        }
    }
}

@Composable
private fun CreateTodoDialog(
    defaultDate: String,
    initial: TodoEntity? = null,
    onConfirm: (String, String?, Boolean, String?, String?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title           by remember { mutableStateOf(initial?.title ?: "") }
    var dueDate         by remember { mutableStateOf(initial?.dueDate ?: defaultDate) }
    var financeLinked   by remember { mutableStateOf(initial?.financeIsLinked ?: false) }
    var financeType     by remember { mutableStateOf(initial?.financeType ?: "EXPENSE") }
    var financeCategory by remember { mutableStateOf(initial?.financeCategory ?: "") }
    var financeAmount   by remember { mutableStateOf(initial?.financeAmount?.toString() ?: "") }

    LSyncInputDialog(
        title = if (initial != null) "할 일 수정" else "새 할 일",
        confirmLabel = if (initial != null) "수정" else "추가",
        confirmEnabled = title.isNotBlank(),
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
private fun CreateRepeatTodoDialog(
    templates: List<TodoTemplateEntity>,
    onConfirm: (title: String, rrule: String, financeIsLinked: Boolean, financeType: String?, financeCategory: String?, financeAmount: Long?) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title           by remember { mutableStateOf("") }
    var frequency       by remember { mutableStateOf(Frequency.WEEKLY) }
    var intervalText    by remember { mutableStateOf("1") }
    var weekdays        by remember { mutableStateOf(emptySet<Weekday>()) }
    var financeLinked   by remember { mutableStateOf(false) }
    var financeType     by remember { mutableStateOf("EXPENSE") }
    var financeCategory by remember { mutableStateOf("") }
    var financeAmount   by remember { mutableStateOf("") }

    val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val option = RecurrenceOption(
        frequency = frequency,
        interval = interval,
        weekdays = if (frequency == Frequency.WEEKLY) weekdays else emptySet(),
    )

    LSyncInputDialog(title = "반복 할 일", confirmEnabled = title.isNotBlank(),
        onConfirm = {
            if (title.isNotBlank()) {
                onConfirm(title, buildRrule(option), financeLinked,
                    if (financeLinked) financeType else null,
                    if (financeLinked) financeCategory.ifBlank { null } else null,
                    if (financeLinked) financeAmount.toLongOrNull() else null)
            }
        },
        onDismiss = onDismiss,
    ) {
        // 활성 템플릿 목록 + 반복 중지(soft delete). 이미 생성된 인스턴스는 유지된다.
        if (templates.isNotEmpty()) {
            Text("반복 중", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.12.em, color = FgTertiary)
            Spacer(Modifier.height(8.dp))
            templates.forEach { template ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.title, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = FgPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(describeRrule(template.rrule), fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary)
                    }
                    TextButton(onClick = { onDelete(template.id) }) {
                        Text("반복 중지", fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = FgSecondary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
            Spacer(Modifier.height(14.dp))
        }

        LSyncField(label = "제목", value = title, onValueChange = { title = it })
        Spacer(Modifier.height(12.dp))

        // 빈도: 매일/매주/매월/매년 — ghost chip (active = FgPrimary solid)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Frequency.DAILY to "매일", Frequency.WEEKLY to "매주",
                Frequency.MONTHLY to "매월", Frequency.YEARLY to "매년",
            ).forEach { (freq, label) ->
                val sel = frequency == freq
                Box(
                    modifier = Modifier.clip(CircleShape)
                        .background(if (sel) FgPrimary else Color.Transparent)
                        .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                        .clickable { frequency = freq }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (sel) BgPrimary else FgSecondary) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 간격(INTERVAL): N마다
        LSyncField(label = "간격 (N마다 반복)", value = intervalText, onValueChange = { intervalText = it.filter(Char::isDigit) })

        // 요일(BYDAY): 매주일 때만 노출. 멀티선택, 미선택 시 BYDAY 생략
        if (frequency == Frequency.WEEKLY) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                Weekday.values().forEach { wd ->
                    val sel = wd in weekdays
                    Box(
                        modifier = Modifier.weight(1f).clip(CircleShape)
                            .background(if (sel) FgPrimary else Color.Transparent)
                            .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                            .clickable { weekdays = if (sel) weekdays - wd else weekdays + wd }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(weekdayLabel(wd), fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (sel) BgPrimary else FgSecondary) }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(describeRrule(buildRrule(option)), fontFamily = Pretendard, fontSize = 12.sp, color = FgSecondary)

        // 가계부 연동 (CreateTodoDialog와 동일 패턴)
        Spacer(Modifier.height(12.dp))
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

private fun weekdayLabel(wd: Weekday): String = when (wd) {
    Weekday.MON -> "월"; Weekday.TUE -> "화"; Weekday.WED -> "수"; Weekday.THU -> "목"
    Weekday.FRI -> "금"; Weekday.SAT -> "토"; Weekday.SUN -> "일"
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
    confirmLabel: String = "추가",
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
                Text(confirmLabel, fontFamily = Pretendard, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (confirmEnabled) AccentBlue else FgDisabled)
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
