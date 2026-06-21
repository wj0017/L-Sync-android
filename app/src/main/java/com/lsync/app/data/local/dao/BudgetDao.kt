package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE userId = :userId AND deletedAt IS NULL")
    fun observeBudgets(userId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("UPDATE budgets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    // 마이그레이션(Auth) 일관: userId 일괄 변경
    @Query("UPDATE budgets SET userId = :newId WHERE userId = :oldId")
    suspend fun migrateUserId(oldId: String, newId: String)

    // 로그아웃 시 동기화 대상 테이블 전체 비움 (계정 전환 데이터 격리)
    @Query("DELETE FROM budgets")
    suspend fun clearAll()
}
