package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val isAllDay: Boolean,
    // isAllDay=false → "2024-05-01T09:00:00+09:00" (ISO8601)
    // isAllDay=true  → "2024-05-01" (Floating Date)
    val startDate: String,
    val endDate: String?,
    val timezone: String?,       // isAllDay=true 면 null
    val rrule: String?,
    val exdatesJson: String?,    // JSON array of excluded dates
    val overridesJson: String?,  // JSON map: date → overridden fields
    val hasAlarm: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
