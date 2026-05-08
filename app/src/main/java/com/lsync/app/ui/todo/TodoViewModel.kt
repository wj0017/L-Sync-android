package com.lsync.app.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TodoUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val todos: List<TodoEntity> = emptyList(),
    val pendingFinanceTodo: TodoEntity? = null, // 금액 미정 시 팝업 대상
    val error: String? = null,
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    init {
        loadTodosForDate(LocalDate.now())
    }

    fun onDateSelect(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadTodosForDate(date)
    }

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
                repository.create(
                    userId = "local_user", // TODO: Firebase Auth 연동 후 교체
                    title = title,
                    dueDate = dueDate,
                    financeIsLinked = financeIsLinked,
                    financeType = financeType,
                    financeCategory = financeCategory,
                    financeAmount = financeAmount,
                )
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun toggleComplete(todo: TodoEntity) {
        viewModelScope.launch {
            if (todo.isCompleted) {
                repository.uncheck(todo)
            } else {
                // financeIsLinked이고 금액 미정이면 팝업 먼저
                if (todo.financeIsLinked && todo.financeAmount == null) {
                    _uiState.update { it.copy(pendingFinanceTodo = todo) }
                } else {
                    repository.complete(todo)
                        .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
                }
            }
        }
    }

    // 금액 팝업에서 확정
    fun confirmFinanceAndComplete(amount: Long) {
        val todo = _uiState.value.pendingFinanceTodo ?: return
        viewModelScope.launch {
            repository.complete(todo, amount)
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            _uiState.update { it.copy(pendingFinanceTodo = null) }
        }
    }

    fun dismissFinancePopup() = _uiState.update { it.copy(pendingFinanceTodo = null) }

    fun deleteTodo(todo: TodoEntity) {
        viewModelScope.launch {
            repository.delete(todo)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun loadTodosForDate(date: LocalDate) {
        viewModelScope.launch {
            repository.observeByDate(date.toString())
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { todos -> _uiState.update { it.copy(todos = todos) } }
        }
    }
}
