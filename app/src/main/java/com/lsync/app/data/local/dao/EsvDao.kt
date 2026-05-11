package com.lsync.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.lsync.app.data.local.entity.EsvVerseEntity

@Dao
interface EsvDao {
    @Query("SELECT * FROM esv_verses WHERE book = :book AND chapter = :chapter ORDER BY verse ASC")
    suspend fun getVerses(book: Int, chapter: Int): List<EsvVerseEntity>
}
