package com.lsync.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lsync.app.data.repository.SearchRepository
import com.lsync.app.data.repository.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 통합 검색 UI 상태. query는 텍스트필드 단일 소스, results는 디바운스 파이프라인이 채운다.
data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val hasSearched: Boolean = false, // 빈 문자열이 아닌 쿼리를 입력한 적이 있는지 (빈 화면 vs "결과 없음" 구분용)
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // 연속 입력은 200ms 디바운스 후 flatMapLatest로 이전 검색을 취소하고 최신 쿼리만 구독한다.
        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        viewModelScope.launch {
            queryFlow
                .debounce(DEBOUNCE_MS)
                .flatMapLatest { repository.search(it) }
                .collect { results ->
                    _uiState.update { it.copy(results = results) }
                }
        }
    }

    // (1) 디바운스 파이프라인 입력 갱신, (2) 텍스트필드 표시용 query 즉시 반영.
    fun onQueryChange(query: String) {
        queryFlow.value = query
        _uiState.update {
            it.copy(
                query = query,
                hasSearched = it.hasSearched || query.isNotBlank(),
            )
        }
    }

    // 검색어/결과 초기화. hasSearched도 리셋해 초기 안내 UI로 되돌린다.
    fun clearQuery() {
        queryFlow.value = ""
        _uiState.value = SearchUiState()
    }

    private companion object {
        const val DEBOUNCE_MS = 200L
    }
}
