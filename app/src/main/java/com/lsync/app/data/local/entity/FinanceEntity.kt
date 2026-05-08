package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance")
data class FinanceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,           // "EXPENSE" | "INCOME"
    val amount: Long,
    val category: String,
    val date: String,           // "YYYY-MM-DD" (디바이스 로컬 기준)
    val note: String?,
    val sourceTodoId: String?,  // Todo 삭제 시 null로 해제, 데이터는 유지
    val isExcluded: Boolean,    // Todo Uncheck 시 Soft Delete (통계 제외)
    val createdAt: Long,
    val updatedAt: Long,
)
