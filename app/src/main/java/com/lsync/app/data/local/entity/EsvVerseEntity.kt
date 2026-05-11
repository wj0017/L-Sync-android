package com.lsync.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "esv_verses")
data class EsvVerseEntity(
    @PrimaryKey val idx: Int,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
)
