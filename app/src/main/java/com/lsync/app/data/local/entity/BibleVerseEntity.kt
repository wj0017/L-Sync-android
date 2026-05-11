package com.lsync.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bible_verses")
data class BibleVerseEntity(
    @PrimaryKey val idx: Int,
    val book: Int,
    val chapter: Int,
    val verse: Int,
    val text: String,
    val testament: String,
    @ColumnInfo(name = "book_name") val bookName: String,
    @ColumnInfo(name = "book_short") val bookShort: String,
)
