package com.lsync.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lsync.app.data.local.dao.BudgetDao
import com.lsync.app.data.local.dao.AlarmLogDao
import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.MemoDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.dao.SyncLogDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.dao.TodoTemplateDao
import com.lsync.app.data.local.entity.BudgetEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import com.lsync.app.data.local.entity.AlarmDeliveryTokenEntity
import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmCoverageStateEntity
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.MemoEntity
import com.lsync.app.data.local.entity.ReadingPlanEntity
import com.lsync.app.data.local.entity.SyncLogEntity
import com.lsync.app.data.local.entity.TodoEntity
import com.lsync.app.data.local.entity.TodoTemplateEntity

@Database(
    entities = [
        EventEntity::class,
        TodoEntity::class,
        TodoTemplateEntity::class,
        FinanceEntity::class,
        ReadingPlanEntity::class,
        MemoEntity::class,
        BudgetEntity::class,
        SyncLogEntity::class,
        AlarmLogEntity::class,
        AlarmDeliveryTokenEntity::class,
        AlarmCoverageGapEntity::class,
        AlarmCoverageStateEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun todoDao(): TodoDao
    abstract fun todoTemplateDao(): TodoTemplateDao
    abstract fun financeDao(): FinanceDao
    abstract fun readingPlanDao(): ReadingPlanDao
    abstract fun memoDao(): MemoDao
    abstract fun budgetDao(): BudgetDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun alarmLogDao(): AlarmLogDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `memos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `book` INTEGER NOT NULL,
                        `chapter` INTEGER NOT NULL,
                        `verse` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `date` TEXT NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE finance ADD COLUMN settlementGroupId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE finance ADD COLUMN deletedAt INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `budgets` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `limitAmount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `deletedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }

        // sync_log: 로컬 전용 동기화 진단 로그. 인덱스 이름은 Room @Index 생성 규칙과 문자 단위로 일치해야 한다.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sync_log` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `target` TEXT NOT NULL,
                        `entityId` TEXT,
                        `success` INTEGER NOT NULL,
                        `errorType` TEXT,
                        `errorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER
                    )"""
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sync_log_userId_createdAt` ON `sync_log` (`userId`, `createdAt`)"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS `alarm_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alarmKey` TEXT NOT NULL, `expectedAt` INTEGER NOT NULL, `userId` TEXT, `status` TEXT NOT NULL, `registered` INTEGER NOT NULL, `exact` INTEGER, `firedAt` INTEGER, `posted` INTEGER, `skipReason` TEXT, `missReason` TEXT)""")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_alarm_log_alarmKey_expectedAt` ON `alarm_log` (`alarmKey`, `expectedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_log_status_expectedAt` ON `alarm_log` (`status`, `expectedAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_log_userId_expectedAt` ON `alarm_log` (`userId`, `expectedAt`)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `alarm_delivery_token` (`token` TEXT NOT NULL, `alarmKey` TEXT NOT NULL, `expectedAt` INTEGER NOT NULL, `state` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `setSucceededAt` INTEGER, `retiredAt` INTEGER, `deliveredAt` INTEGER, PRIMARY KEY(`token`))""")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_delivery_token_alarmKey_expectedAt` ON `alarm_delivery_token` (`alarmKey`, `expectedAt`)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `alarm_coverage_gap` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fromAt` INTEGER NOT NULL, `toAt` INTEGER NOT NULL)""")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_alarm_coverage_gap_fromAt_toAt` ON `alarm_coverage_gap` (`fromAt`, `toAt`)")
                database.execSQL("""CREATE TABLE IF NOT EXISTS `alarm_coverage_state` (`id` INTEGER NOT NULL, `coveredFromAt` INTEGER NOT NULL, `coveredThroughAt` INTEGER NOT NULL, `refreshedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            }
        }
    }
}
