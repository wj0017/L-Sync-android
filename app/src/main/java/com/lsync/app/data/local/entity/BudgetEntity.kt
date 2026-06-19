package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,   // 결정론적: "${userId}_${category}" — 멱등 upsert(카테고리당 1행)
    val userId: String,
    val category: String,         // FinanceCategory 값. 전체 한도는 sentinel TOTAL_CATEGORY
    val limitAmount: Long,        // 매월 반복 한도(원)
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,  // soft delete — pull(upsert-only) 삭제 전파용
) {
    companion object { const val TOTAL_CATEGORY = "__TOTAL__" }
}
