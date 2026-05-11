package com.lsync.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lsync.app.data.local.dao.EsvDao
import com.lsync.app.data.local.entity.EsvVerseEntity

@Database(entities = [EsvVerseEntity::class], version = 1, exportSchema = false)
abstract class EsvDatabase : RoomDatabase() {
    abstract fun esvDao(): EsvDao
}
