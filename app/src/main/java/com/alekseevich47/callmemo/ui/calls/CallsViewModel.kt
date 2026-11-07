package com.alekseevich47.callmemo.ui.calls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alekseevich47.callmemo.data.repository.CallMemoRepository
import com.alekseevich47.callmemo.domain.model.CallDetail
import com.alekseevich47.callmemo.domain.model.CallLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class CallDateFilter(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    val active: Boolean get() = start != null
}

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val repository: CallMemoRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val dateFilter = MutableStateFlow(CallDateFilter())

    val searchQuery: StateFlow<String> = query
    val filter: StateFlow<CallDateFilter> = dateFilter

    @OptIn(ExperimentalCoroutinesApi::class)
    val calls: StateFlow<List<CallLogEntry>> = combine(query, dateFilter) { q, filter ->
        q to filter
    }.flatMapLatest { (q, filter) ->
        val zone = ZoneId.systemDefault()
        val from = filter.start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val toExclusiveEnd = (filter.end ?: filter.start)
            ?.plusDays(1)
            ?.atStartOfDay(zone)
            ?.toInstant()
            ?.toEpochMilli()
            ?.minus(1)
        repository.observeCalls(q, from, toExclusiveEnd)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setDateFilter(start: LocalDate?, end: LocalDate?) {
        dateFilter.value = when {
            start == null -> CallDateFilter()
            end == null || end == start -> CallDateFilter(start = start, end = start)
            end.isBefore(start) -> CallDateFilter(start = end, end = start)
            else -> CallDateFilter(start = start, end = end)
        }
    }

    fun clearDateFilter() {
        dateFilter.value = CallDateFilter()
    }
}

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    private val repository: CallMemoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val callId: Long = savedStateHandle.get<Long>("callId") ?: -1L
    private val _detail = MutableStateFlow<CallDetail?>(null)
    val detail: StateFlow<CallDetail?> = _detail

    init {
        viewModelScope.launch {
            if (callId > 0L) {
                _detail.value = repository.callDetail(callId)
            }
        }
    }
}
