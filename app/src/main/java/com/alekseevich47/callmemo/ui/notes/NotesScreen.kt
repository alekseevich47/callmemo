package com.alekseevich47.callmemo.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.alekseevich47.callmemo.data.repository.CallMemoRepository
import com.alekseevich47.callmemo.domain.model.NoteItem
import com.alekseevich47.callmemo.ui.components.ScreenHeader
import com.alekseevich47.callmemo.ui.components.SectionCard
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoMuted
import com.alekseevich47.callmemo.util.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: CallMemoRepository,
) : ViewModel() {
    val notes: StateFlow<List<NoteItem>> = repository.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }
}

@Composable
fun NotesRoute(
    onOpenSettings: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Заметки", onSettings = onOpenSettings)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notes, key = { it.id }) { note ->
                    SectionCard {
                        Text(note.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(TimeFormat.full(note.createdAt), color = CallMemoMuted, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(note.body, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {},
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 22.dp),
            shape = CircleShape,
            containerColor = CallMemoBlue,
            contentColor = Color.White,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Новая заметка")
        }
    }
}
