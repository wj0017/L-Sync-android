package com.lsync.app.ui.widget

import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.ReadingPlanDao
import com.lsync.app.data.local.dao.TodoDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todoDao(): TodoDao
    fun eventDao(): EventDao
    fun financeDao(): FinanceDao
    fun readingPlanDao(): ReadingPlanDao
}
