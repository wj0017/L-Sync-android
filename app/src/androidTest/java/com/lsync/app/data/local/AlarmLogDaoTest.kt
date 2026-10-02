package com.lsync.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lsync.app.data.local.dao.AlarmLogDao
import com.lsync.app.data.local.entity.AlarmCoverageGapEntity
import com.lsync.app.data.local.entity.AlarmCoverageStateEntity
import com.lsync.app.data.local.entity.AlarmDeliveryTokenEntity
import com.lsync.app.data.local.entity.AlarmLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@Database(
    entities = [AlarmLogEntity::class, AlarmDeliveryTokenEntity::class, AlarmCoverageGapEntity::class, AlarmCoverageStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AlarmLogTestDatabase : RoomDatabase() {
    abstract fun alarmLogDao(): AlarmLogDao
}

@RunWith(AndroidJUnit4::class)
class AlarmLogDaoTest {
    @Test fun priorDeliverySurvivesFailedReplacementAndColdStartRearm() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AlarmLogTestDatabase::class.java,
        ).build()
        try {
            val dao = db.alarmLogDao()
            dao.insertExpected(AlarmLogEntity(alarmKey = "reminder:bible", expectedAt = 100, userId = null))
            dao.insertExpected(AlarmLogEntity(alarmKey = "reminder:bible", expectedAt = 200, userId = null))
            dao.prepareToken(AlarmDeliveryTokenEntity("old-A", "reminder:bible", 100, createdAt = 1))
            dao.markTokenArmed("old-A", 2)
            dao.prepareToken(AlarmDeliveryTokenEntity("failed-B", "reminder:bible", 200, createdAt = 3))
            dao.markTokenFailed("failed-B")
            // Application.onCreate registers the next occurrence before the old receiver runs.
            dao.prepareToken(AlarmDeliveryTokenEntity("new-B", "reminder:bible", 200, createdAt = 4))
            dao.markTokenArmed("new-B", 5)
            dao.retireToken("old-A", 6)

            assertFalse(dao.recordDelivery("failed-B", 100))
            assertTrue(dao.recordDelivery("old-A", 101))
            assertFalse(dao.recordDelivery("old-A", 102))
            assertEquals("FIRED", dao.getExpected("reminder:bible", 100)?.status)
            assertEquals("PENDING", dao.getExpected("reminder:bible", 200)?.status)
            assertTrue(dao.recordDelivery("new-B", 201))
            assertEquals("FIRED", dao.getExpected("reminder:bible", 200)?.status)
        } finally {
            db.close()
        }
    }

    @Test fun preparedTokenDeliveryProvesSetSucceededAcrossCrash() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AlarmLogTestDatabase::class.java,
        ).build()
        try {
            val dao = db.alarmLogDao()
            dao.insertExpected(AlarmLogEntity(alarmKey = "event:e", expectedAt = 100, userId = "u"))
            dao.prepareToken(AlarmDeliveryTokenEntity("attempt-1", "event:e", 100, createdAt = 1))
            assertTrue(dao.recordDelivery("attempt-1", 101))
            dao.markMissed("event:e", 100, null)
            assertEquals("FIRED", dao.getExpected("event:e", 100)?.status)
        } finally {
            db.close()
        }
    }
}
