package com.lsync.app.ui.widget

import android.graphics.Bitmap
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity

data class WidgetState(
    val todos: List<TodoEntity>,
    val events: List<EventEntity>,
    val monthExpense: Long,
    val monthIncome: Long,
    val dailyExpenses: List<Long>,
    val dailyIncomes: List<Long>,
    val todayReadingPlan: List<ReadingPlanEntity>,
    val readCount: Int,
    val totalCount: Int,
    val dateLabel: String,
    val circularProgressBitmap: Bitmap?,
) {
    companion object {
        fun empty() = WidgetState(
            todos = emptyList(),
            events = emptyList(),
            monthExpense = 0,
            monthIncome = 0,
            dailyExpenses = emptyList(),
            dailyIncomes = emptyList(),
            todayReadingPlan = emptyList(),
            readCount = 0,
            totalCount = 0,
            dateLabel = "",
            circularProgressBitmap = null,
        )
    }
}
