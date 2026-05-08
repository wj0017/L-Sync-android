package com.lsync.app.data.repository

import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.entity.FinanceEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinanceRepository @Inject constructor(
    private val dao: FinanceDao,
) {
    fun observeByMonth(yearMonth: String): Flow<List<FinanceEntity>> =
        dao.observeByDateRange("$yearMonth-01", "$yearMonth-31")
}
