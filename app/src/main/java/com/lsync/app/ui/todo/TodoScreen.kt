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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
        Column {
            // 헤더
            TodoHeader(onAddClick = { showCreateDialog = true })

            // 날짜 칩 스트립
            DateChipRow(
                selectedDate = uiState.selectedDate,
                onDateSelect = viewModel::onDateSelect,
            )

            Spacer(Modifier.height(4.dp))

            // 투두 목록
            if (uiState.todos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("할 일 없음", style = MaterialTheme.typography.bodyMedium, color = TextDisabled)
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
    }

    // 금액 미정 팝업
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
                viewModel.createTodo(
                    title = title,
                    dueDate = dueDate,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun TodoHeader(onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "할 일",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
        )
        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(BgCard),
        ) {
            Icon(Icons.Default.Add, contentDescription = "추가", tint = TextPrimary)
        }
    }
}

@Composable
private fun DateChipRow(selectedDate: LocalDate, onDateSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
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
                    .background(if (isSelected) TextPrimary else BgCard)
                    .clickable { onDateSelect(date) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN)),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSelected -> BgPrimary
                        isToday    -> AccentBlue
                        else       -> TextSecondary
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) BgPrimary else TextPrimary,
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
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 커스텀 체크박스
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .then(
                    if (todo.isCompleted)
                        Modifier.background(AccentBlue)
                    else
                        Modifier.border(1.5.dp, TextDisabled, CircleShape)
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (todo.isCompleted) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo.isCompleted) TextDisabled else TextPrimary,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                todo.dueDate?.let { add(it) }
                if (todo.financeIsLinked) {
                    val amount = todo.financeAmount?.let { "₩%,d".format(it) } ?: "금액 미정"
                    add("${if (todo.financeType == "INCOME") "수입" else "지출"} · $amount")
                }
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (todo.financeIsLinked && todo.financeAmount == null) AccentRed.copy(alpha = 0.8f) else TextSecondary,
                )
            }
        }

        IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "삭제", tint = TextDisabled, modifier = Modifier.size(16.dp))
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = BgCard,
            title = { Text("할 일 삭제", color = TextPrimary) },
            text = { Text("'${todo.title}'을(를) 삭제할까요?\n연결된 가계부는 유지됩니다.", color = TextSecondary) },
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
private fun FinanceAmountDialog(
    todoTitle: String,
    category: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("금액 입력", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("'$todoTitle'", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text("카테고리: $category", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amountText.toLongOrNull()?.let { onConfirm(it) } },
                enabled = amountText.isNotBlank(),
            ) { Text("확인", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에", color = TextSecondary) }
        },
    )
}

@Composable
private fun CreateTodoDialog(
    defaultDate: String,
    onConfirm: (String, String?, Boolean, String?, String?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(defaultDate) }
    var financeLinked by remember { mutableStateOf(false) }
    var financeType by remember { mutableStateOf("EXPENSE") }
    var financeCategory by remember { mutableStateOf("") }
    var financeAmount by remember { mutableStateOf("") }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentBlue,
        focusedLabelColor = AccentBlue,
        cursorColor = AccentBlue,
        unfocusedBorderColor = Divider,
        unfocusedLabelColor = TextSecondary,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("새 할 일", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("제목") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = textFieldColors,
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it },
                    label = { Text("마감일 (YYYY-MM-DD)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = textFieldColors,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = financeLinked, onCheckedChange = { financeLinked = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentBlue, uncheckedColor = TextSecondary),
                    )
                    Text("가계부 연동", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                if (financeLinked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("EXPENSE" to "지출", "INCOME" to "수입").forEach { (type, label) ->
                            FilterChip(
                                selected = financeType == type,
                                onClick = { financeType = type },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentBlue,
                                    selectedLabelColor = Color.White,
                                    containerColor = BgElevated,
                                    labelColor = TextSecondary,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = financeCategory, onValueChange = { financeCategory = it },
                        label = { Text("카테고리") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = textFieldColors,
                    )
                    OutlinedTextField(
                        value = financeAmount,
                        onValueChange = { financeAmount = it.filter(Char::isDigit) },
                        label = { Text("금액 (미정이면 비워두세요)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = textFieldColors,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title,
                            dueDate.ifBlank { null },
                            financeLinked,
                            if (financeLinked) financeType else null,
                            if (financeLinked) financeCategory.ifBlank { null } else null,
                            if (financeLinked) financeAmount.toLongOrNull() else null,
                        )
                    }
                }
            ) { Text("추가", color = AccentBlue) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary) } },
    )
}
