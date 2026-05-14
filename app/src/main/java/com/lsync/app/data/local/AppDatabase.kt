package com.lsync.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.local.entity.TodoTemplateEntity

@Database(
    entities = [
        EventEntity::class,
        TodoEntity::class,
        TodoTemplateEntity::class,
        FinanceEntity::class,
        ReadingPlanEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun todoDao(): TodoDao
    abstract fun todoTemplateDao(): TodoTemplateDao
    abstract fun financeDao(): FinanceDao
    abstract fun readingPlanDao(): ReadingPlanDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reading_plan (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        book INTEGER NOT NULL,
                        chapter INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
