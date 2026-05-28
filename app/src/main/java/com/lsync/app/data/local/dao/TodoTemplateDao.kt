package com.lsync.app.data.local.dao

import androidx.room.*
import com.lsync.app.data.local.entity.TodoTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoTemplateDao {
    @Query("SELECT * FROM todo_templates WHERE userId = :userId AND isActive = 1")
    fun observeActiveTemplates(userId: String): Flow<List<TodoTemplateEntity>>

    @Query("SELECT * FROM todo_templates WHERE userId = :userId AND isActive = 1")
    suspend fun getActiveTemplates(userId: String): List<TodoTemplateEntity>

    @Upsert
    suspend fun upsert(template: TodoTemplateEntity)

    @Query("UPDATE todo_templates SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivate(id: String, updatedAt: Long)

    @Query("UPDATE todo_templates SET userId = :newId WHERE userId = :oldId")
    suspend fun migrateUserId(oldId: String, newId: String)
}
