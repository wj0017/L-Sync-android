package com.lsync.app.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.ui.calendar.LSyncDialog
import com.lsync.app.ui.calendar.LSyncField
import com.lsync.app.ui.calendar.LSyncInputDialog
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
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
                    text = uiState.selectedDate.year.toString(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    letterSpacing = 0.12.em,
                    color = FgTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "할 일",
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    letterSpacing = (-0.035).em,
                    color = FgPrimary,
                )
            }
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BgCard)
                    .border(1.dp, HairlineWhite, CircleShape),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "추가", tint = FgPrimary, modifier = Modifier.size(20.dp))
            }
        }

        // Date chip strip
        DateChipRow(selectedDate = uiState.selectedDate, onDateSelect = viewModel::onDateSelect)

        Spacer(Modifier.height(4.dp))

        // Todo list
        if (uiState.todos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("할 일 없음", fontSize = 14.sp, color = FgDisabled, fontFamily = Pretendard)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.todos, key = { it.id }) { todo ->
                    TodoItem(
                        todo = todo,
                        onToggle = { viewModel.toggleComplete(todo) },
                        onDelete = { viewModel.deleteTodo(todo) },
                    )
                }
            }
        }
    }

    // Finance amount popup
    uiState.pendingFinanceTodo?.let { todo ->
        FinanceAmountDialog(
            todoTitle = todo.title,
            category = todo.financeCategory ?: "미분류",
            onConfirm = viewModel::confirmFinanceAndComplete,
            onDismiss = viewModel::dismissFinancePopup,
        )
    }

    if (showCreateDialog) {
        CreateTodoDialog(
            defaultDate = uiState.selectedDate.toString(),
            onConfirm = { title, dueDate, financeIsLinked, financeType, financeCategory, financeAmount ->
                viewModel.createTodo(title, dueDate, financeIsLinked, financeType, financeCategory, financeAmount)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun DateChipRow(selectedDate: LocalDate, onDateSelect: (LocalDate) -> Unit) {
    val today = remember { LocalDate.now() }
    val dates = remember { (-1..5).map { today.plusDays(it.toLong()) } }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) FgPrimary else BgCard)
                    .then(if (!isSelected) Modifier.border(1.dp, HairlineWhite, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onDateSelect(date) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .defaultMinSize(minWidth = 46.dp),
            ) {
                // DOW label — uppercase, tracking
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN)).uppercase(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    color = when {
                        isSelected -> BgPrimary
                        isToday    -> AccentBlue
                        else       -> FgSecondary
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isSelected) BgPrimary else FgPrimary,
                )
            }
        }
    }
}

@Composable
private fun TodoItem(todo: TodoEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
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
        // Custom 22dp circle checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (todo.isCompleted) AccentBlue else Color.Transparent)
                .then(
                    if (!todo.isCompleted)
                        Modifier.border(1.5.dp, FgDisabled, CircleShape)
                    else Modifier
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (todo.isCompleted) {
                Text("✓", fontFamily = Pretendard, fontSize = 11.sp, color = Color.White)
            }
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
                val isWarn = todo.financeIsLinked && todo.financeAmount == null
                Text(
                    text = metaParts.joinToString("  ·  ").uppercase(),
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.12.em,
                    color = if (isWarn) AccentRed80 else FgTertiary,
                )
            }
        }

        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
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
            title = "할 일 삭제",
            body = "'${todo.title}'을(를) 삭제할까요?\n연결된 가계부는 유지됩니다.",
            confirmLabel = "삭제",
            isDanger = true,
            onConfirm = { onDelete(); showConfirm = false },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun FinanceAmountDialog(
    todoTitle: String, category: String,
    onConfirm: (Long) -> Unit, onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }

    LSyncInputDialog(
        title = "금액 입력",
        confirmEnabled = amountText.isNotBlank(),
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

@Composable
private fun CreateTodoDialog(
    defaultDate: String,
    onConfirm: (String, String?, Boolean, String?, String?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title          by remember { mutableStateOf("") }
    var dueDate        by remember { mutableStateOf(defaultDate) }
    var financeLinked  by remember { mutableStateOf(false) }
    var financeType    by remember { mutableStateOf("EXPENSE") }
    var financeCategory by remember { mutableStateOf("") }
    var financeAmount  by remember { mutableStateOf("") }

    LSyncInputDialog(
        title = "새 할 일",
        confirmEnabled = title.isNotBlank(),
        onConfirm = {
            onConfirm(
                title,
                dueDate.ifBlank { null },
                financeLinked,
                if (financeLinked) financeType else null,
                if (financeLinked) financeCategory.ifBlank { null } else null,
                if (financeLinked) financeAmount.toLongOrNull() else null,
            )
        },
        onDismiss = onDismiss,
    ) {
        LSyncField(label = "제목", value = title, onValueChange = { title = it })
        Spacer(Modifier.height(10.dp))
        LSyncField(label = "마감일 (YYYY-MM-DD)", value = dueDate, onValueChange = { dueDate = it })
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { financeLinked = !financeLinked }) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (financeLinked) AccentBlue else Color.Transparent)
                    .then(if (!financeLinked) Modifier.border(1.5.dp, FgSecondary, RoundedCornerShape(3.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (financeLinked) Text("✓", fontSize = 11.sp, color = Color.White, fontFamily = Pretendard)
            }
            Spacer(Modifier.width(8.dp))
            Text("가계부 연동", fontFamily = Pretendard, fontSize = 13.sp, color = FgPrimary)
        }
        if (financeLinked) {
            Spacer(Modifier.height(12.dp))
            // Ghost chips — outline only, active = white solid
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EXPENSE" to "지출", "INCOME" to "수입").forEach { (type, label) ->
                    val sel = financeType == type
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (sel) FgPrimary else Color.Transparent)
                            .border(1.dp, if (sel) FgPrimary else Divider, CircleShape)
                            .clickable { financeType = type }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontFamily = Pretendard,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            letterSpacing = 0.005.em,
                            color = if (sel) BgPrimary else FgSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LSyncField(label = "카테고리", value = financeCategory, onValueChange = { financeCategory = it })
            Spacer(Modifier.height(10.dp))
            LSyncField(label = "금액 (미정이면 비워두세요)", value = financeAmount,
                onValueChange = { financeAmount = it.filter(Char::isDigit) })
        }
    }
}
