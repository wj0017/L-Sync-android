package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One expected occurrence, recorded independently of an AlarmManager registration. */
@Entity(
    tableName = "alarm_log",
    indices = [
        Index(value = ["alarmKey", "expectedAt"], unique = true),
        Index(value = ["status", "expectedAt"]),
        Index(value = ["userId", "expectedAt"]),
    ],
)
data class AlarmLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmKey: String,
    val expectedAt: Long,
    val userId: String?,
    val status: String = "PENDING",
    // Historical registration success, never evidence for delivery attribution.
    val registered: Boolean = false,
    val exact: Boolean? = null,
    val firedAt: Long? = null,
    val posted: Boolean? = null,
    val skipReason: String? = null,
    val missReason: String? = null,
)

/** Each OS attempt has a new token in PendingIntent identity (Intent.data). */
@Entity(
    tableName = "alarm_delivery_token",
    indices = [Index(value = ["alarmKey", "expectedAt"])],
)
data class AlarmDeliveryTokenEntity(
    @PrimaryKey val token: String,
    val alarmKey: String,
    val expectedAt: Long,
    // PREPARED survives a crash after OS set; delivery itself proves that set succeeded.
    val state: String = "PREPARED",
    val createdAt: Long,
    val setSucceededAt: Long? = null,
    val retiredAt: Long? = null,
    val deliveredAt: Long? = null,
)

@Entity(tableName = "alarm_coverage_gap", indices = [Index(value = ["fromAt", "toAt"])])
data class AlarmCoverageGapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromAt: Long,
    val toAt: Long,
)

@Entity(tableName = "alarm_coverage_state")
data class AlarmCoverageStateEntity(
    @PrimaryKey val id: Int = 1,
    val coveredFromAt: Long,
    val coveredThroughAt: Long,
    val refreshedAt: Long,
)
