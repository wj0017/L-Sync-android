package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val templateId: String?,
    val title: String,
    val isCompleted: Boolean,
    val dueDate: String?,           // "YYYY-MM-DD" (Floating Date)
    val completedAt: Long?,
    // Finance 연동 정보
    val financeIsLinked: Boolean,
    val financeType: String?,       // "EXPENSE" | "INCOME"
    val financeCategory: String?,
    val financeAmount: Long?,
    val linkedFinanceId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "todo_templates")
data class TodoTemplateEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val rrule: String,              // 반복 규칙 (FREQ=MONTHLY 등)
    val financeIsLinked: Boolean,
    val financeType: String?,
    val financeCategory: String?,
    val financeAmount: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
