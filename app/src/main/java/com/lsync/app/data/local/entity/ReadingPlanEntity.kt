package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_plan")
data class ReadingPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,       // YYYY-MM-DD
    val book: Int,          // 1~66
    val chapter: Int,
    val isRead: Boolean = false,
)
