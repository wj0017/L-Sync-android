package com.lsync.app.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.lsync.app.data.local.entity.TodoEntity

@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("할 일") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "할 일 추가")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.todos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("할 일이 없습니다", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.todos, key = { it.id }) { todo ->
                        TodoItem(
                            todo = todo,
                            onToggle = { viewModel.toggleComplete(todo) },
                            onDelete = { viewModel.deleteTodo(todo) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // 금액 미정 팝업 (PRD 2.1 정책: 금액 미정 시 UI 팝업 강제)
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
private fun TodoItem(todo: TodoEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    ListItem(
        leadingContent = {
            Checkbox(checked = todo.isCompleted, onCheckedChange = { onToggle() })
        },
        headlineContent = {
            Text(
                text = todo.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else null,
                color = if (todo.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val labels = buildList {
                todo.dueDate?.let { add(it) }
                if (todo.financeIsLinked) {
                    val amount = todo.financeAmount?.let { "₩${"%,d".format(it)}" } ?: "금액 미정"
                    add("${todo.financeType ?: "지출"} · $amount")
                }
            }
            if (labels.isNotEmpty()) Text(labels.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
        },
        trailingContent = {
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = MaterialTheme.colorScheme.error)
            }
        },
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("할 일 삭제") },
            text = { Text("'${todo.title}'을(를) 삭제할까요?\n연결된 가계부 기록은 유지됩니다.") },
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
private fun FinanceAmountDialog(
    todoTitle: String,
    category: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("금액 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("'$todoTitle' 완료 시 가계부에 기록됩니다.")
                Text("카테고리: $category", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amountText.toLongOrNull()?.let { onConfirm(it) } },
                enabled = amountText.isNotBlank(),
            ) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("나중에") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 할 일") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("제목") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it },
                    label = { Text("마감일 (YYYY-MM-DD)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = financeLinked, onCheckedChange = { financeLinked = it })
                    Text("가계부 연동")
                }
                if (financeLinked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("EXPENSE" to "지출", "INCOME" to "수입").forEach { (type, label) ->
                            FilterChip(
                                selected = financeType == type,
                                onClick = { financeType = type },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = financeCategory, onValueChange = { financeCategory = it },
                        label = { Text("카테고리") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = financeAmount,
                        onValueChange = { financeAmount = it.filter { c -> c.isDigit() } },
                        label = { Text("금액 (미정이면 비워두세요)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
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
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
