package com.lsync.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lsync.app.data.local.entity.SyncLogEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "migration-6-8-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    private fun createLegacyDatabase(version: Int) {
        // These are the v6 table definitions. Phase 23 added only sync_log in v7.
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { oldDb ->
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `isAllDay` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `timezone` TEXT, `rrule` TEXT, `exdatesJson` TEXT, `overridesJson` TEXT, `hasAlarm` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `todos` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `templateId` TEXT, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `dueDate` TEXT, `completedAt` INTEGER, `financeIsLinked` INTEGER NOT NULL, `financeType` TEXT, `financeCategory` TEXT, `financeAmount` INTEGER, `linkedFinanceId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `todo_templates` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `rrule` TEXT NOT NULL, `financeIsLinked` INTEGER NOT NULL, `financeType` TEXT, `financeCategory` TEXT, `financeAmount` INTEGER, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `finance` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category` TEXT NOT NULL, `date` TEXT NOT NULL, `note` TEXT, `sourceTodoId` TEXT, `isExcluded` INTEGER NOT NULL, `settlementGroupId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `reading_plan` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `book` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `isRead` INTEGER NOT NULL)")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `memos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `book` INTEGER NOT NULL, `chapter` INTEGER NOT NULL, `verse` INTEGER NOT NULL, `text` TEXT NOT NULL, `date` TEXT NOT NULL)")
            oldDb.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `category` TEXT NOT NULL, `limitAmount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
            oldDb.execSQL("INSERT INTO todos (id, userId, title, isCompleted, financeIsLinked, createdAt, updatedAt) VALUES ('legacy-todo', 'user', 'kept after upgrade', 0, 0, 100, 100)")
            if (version == 7) {
                oldDb.execSQL("CREATE TABLE IF NOT EXISTS `sync_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` TEXT NOT NULL, `target` TEXT NOT NULL, `entityId` TEXT, `success` INTEGER NOT NULL, `errorType` TEXT, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `resolvedAt` INTEGER)")
                oldDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_log_userId_createdAt` ON `sync_log` (`userId`, `createdAt`)")
            }
            oldDb.version = version
        }
    }

    @Test
    fun version6TodoSurvivesMigrationAndSyncLogIsUsable() = runBlocking {
        createLegacyDatabase(6)
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
            .build()
        try {
            val todo = upgraded.todoDao().getById("legacy-todo")
            assertNotNull(todo)
            assertEquals("kept after upgrade", todo?.title)

            upgraded.syncLogDao().insert(
                SyncLogEntity(
                    userId = "user",
                    target = "todos",
                    entityId = "legacy-todo",
                    success = false,
                    errorType = "TestFailure",
                    errorMessage = null,
                    createdAt = 101,
                    resolvedAt = null,
                )
            )
            assertEquals(1, upgraded.syncLogDao().getRetryableUnresolvedCount("user"))
            upgraded.alarmLogDao().insertExpected(AlarmLogEntity(alarmKey = "event:legacy-todo", expectedAt = 200, userId = "user"))
            assertNotNull(upgraded.alarmLogDao().getExpected("event:legacy-todo", 200))
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun version7DataSurvivesAndAlarmTablesAreUsable() = runBlocking {
        createLegacyDatabase(7)
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_7_8)
            .build()
        try {
            assertEquals("kept after upgrade", upgraded.todoDao().getById("legacy-todo")?.title)
            upgraded.alarmLogDao().insertExpected(AlarmLogEntity(alarmKey = "todo:legacy-todo", expectedAt = 300, userId = "user"))
            upgraded.alarmLogDao().markFired("todo:legacy-todo", 300, 301)
            assertEquals("FIRED", upgraded.alarmLogDao().getExpected("todo:legacy-todo", 300)?.status)
        } finally {
            upgraded.close()
        }
    }
}
