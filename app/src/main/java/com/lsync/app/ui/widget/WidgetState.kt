package com.lsync.app.ui.widget

import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity

data class WidgetState(
    val todos: List<TodoEntity>,
    val events: List<EventEntity>,
    val monthExpense: Long,
    val monthIncome: Long,
    val todayReadingPlan: List<ReadingPlanEntity>,
    val readCount: Int,
    val totalCount: Int,
)
