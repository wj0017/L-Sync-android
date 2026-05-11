package com.lsync.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lsync.app.data.local.dao.BibleDao
import com.lsync.app.data.local.entity.BibleVerseEntity

@Database(
    entities = [BibleVerseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BibleDatabase : RoomDatabase() {
    abstract fun bibleDao(): BibleDao
}
