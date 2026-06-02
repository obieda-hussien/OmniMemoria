package com.omnimemoria.ui.trash

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.TrashItem
import com.omnimemoria.data.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI Events ─────────────────────────────────────────────────────────────────

sealed class TrashUiEvent {
    data class RequestMediaPermission(
        val pendingIntent: PendingIntent,
        val onConfirmed: () -> Unit
    ) : TrashUiEvent()

    data class ShowSnackbar(val message: String) : TrashUiEvent()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository
) : ViewModel() {

    val trashItems: StateFlow<List<TrashItem>> = trashRepository.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trashCount: StateFlow<Int> = trashRepository.getCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uiEvents = Channel<TrashUiEvent>(Channel.BUFFERED)
    val uiEvents: Flow<TrashUiEvent> = _uiEvents.receiveAsFlow()

    // ── Restore ───────────────────────────────────────────────────────────────

    fun restore(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val pi = trashRepository.restoreFromTrashIntent(item)
            val name = item.originalPath.substringAfterLast('/').ifBlank { item.originalPath }
            if (pi != null) {
                _uiEvents.send(TrashUiEvent.RequestMediaPermission(pi) {
                    viewModelScope.launch(Dispatchers.IO) {
                        trashRepository.confirmRestoreFromTrash(item)
                        _uiEvents.send(TrashUiEvent.ShowSnackbar("\"$name\" restored"))
                    }
                })
            } else {
                trashRepository.confirmRestoreFromTrash(item)
                _uiEvents.send(TrashUiEvent.ShowSnackbar("\"$name\" restored"))
            }
        }
    }

    // ── Permanent delete ───────────────────────────────────────────────────────

    fun permanentlyDelete(item: TrashItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val pi = trashRepository.permanentlyDeleteIntent(item)
            if (pi != null) {
                _uiEvents.send(TrashUiEvent.RequestMediaPermission(pi) {
                    viewModelScope.launch(Dispatchers.IO) {
                        trashRepository.confirmPermanentlyDelete(item)
                        _uiEvents.send(TrashUiEvent.ShowSnackbar("Deleted permanently"))
                    }
                })
            } else {
                trashRepository.confirmPermanentlyDelete(item)
                _uiEvents.send(TrashUiEvent.ShowSnackbar("Deleted permanently"))
            }
        }
    }

    // ── Empty trash ────────────────────────────────────────────────────────────

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val pi = trashRepository.getEmptyTrashIntent()
            if (pi != null) {
                _uiEvents.send(TrashUiEvent.RequestMediaPermission(pi) {
                    viewModelScope.launch(Dispatchers.IO) {
                        trashRepository.confirmEmptyTrash()
                        _isLoading.value = false
                        _uiEvents.send(TrashUiEvent.ShowSnackbar("Trash emptied"))
                    }
                })
            } else {
                trashRepository.confirmEmptyTrash()
                _isLoading.value = false
                _uiEvents.send(TrashUiEvent.ShowSnackbar("Trash emptied"))
            }
        }
    }
}
