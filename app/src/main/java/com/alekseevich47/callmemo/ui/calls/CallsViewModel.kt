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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val repository: CallMemoRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val calls: StateFlow<List<CallLogEntry>> = query
        .flatMapLatest { repository.observeCalls(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchQuery: StateFlow<String> = query

    fun onQueryChange(value: String) {
        query.value = value
    }
}

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    private val repository: CallMemoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val callId: Long = checkNotNull(savedStateHandle["callId"])
    private val _detail = MutableStateFlow<CallDetail?>(null)
    val detail: StateFlow<CallDetail?> = _detail

    init {
        viewModelScope.launch {
            _detail.value = repository.callDetail(callId)
        }
    }
}
