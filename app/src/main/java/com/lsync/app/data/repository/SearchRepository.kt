package com.lsync.app.data.repository

import com.lsync.app.data.local.dao.EventDao
import com.lsync.app.data.local.dao.FinanceDao
import com.lsync.app.data.local.dao.TodoDao
import com.lsync.app.data.local.entity.EventEntity
import com.lsync.app.data.local.entity.FinanceEntity
import com.lsync.app.data.local.entity.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

// 도메인 횡단 검색 결과. 각 섹션은 비어 있을 수 있다.
data class SearchResults(
    val events: List<EventEntity> = emptyList(),
    val todos: List<TodoEntity> = emptyList(),
    val finances: List<FinanceEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = events.isEmpty() && todos.isEmpty() && finances.isEmpty()

    val totalCount: Int
        get() = events.size + todos.size + finances.size
}

class SearchRepository(
    private val eventDao: EventDao,
    private val todoDao: TodoDao,
    private val financeDao: FinanceDao,
) {
    // 공백/빈 쿼리는 DAO를 치지 않고 빈 결과를 즉시 방출한다.
    fun search(query: String): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return flowOf(SearchResults())
        return combine(
            eventDao.searchByTitle(trimmed),
            todoDao.searchByTitle(trimmed),
            financeDao.search(trimmed),
        ) { events, todos, finances ->
            SearchResults(events = events, todos = todos, finances = finances)
        }
    }
}
