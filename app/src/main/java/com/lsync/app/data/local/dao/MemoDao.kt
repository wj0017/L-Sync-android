package com.lsync.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lsync.app.data.local.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Insert
    suspend fun insert(memo: MemoEntity): Long

    @Query("SELECT * FROM memos WHERE book = :book AND chapter = :chapter ORDER BY verse ASC, id ASC")
    fun observeForChapter(book: Int, chapter: Int): Flow<List<MemoEntity>>

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
