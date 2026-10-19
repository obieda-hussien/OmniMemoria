package com.omnimemoria.ui.trash

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.local.db.TrashItem
import com.omnimemoria.data.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TrashUiEvent {
    data class RequestMediaPermission(val pendingIntent: PendingIntent, val onConfirmed: () -> Unit) : TrashUiEvent()
    data class ShowSnackbar(val message: String) : TrashUiEvent()
}

@HiltViewModel
class TrashViewModel @Inject constructor(private val repository: TrashRepository) : ViewModel() {
    val trashItems = repository.getAllFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trashCount = repository.getCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _uiEvents = Channel<TrashUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun permissionCancelled() { _isLoading.value = false }
    fun restore(item: TrashItem) = perform(listOf(item), true)
    fun permanentlyDelete(item: TrashItem) = perform(listOf(item), false)
    // Snapshot the requested items. A later arrival must not be removed by this confirmation.
    fun emptyTrash() = perform(trashItems.value.toList(), false)

    private fun perform(items: List<TrashItem>, restore: Boolean) {
        if (_isLoading.value || items.isEmpty()) return
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = if (restore) repository.restoreFromTrashIntent(items) else repository.permanentlyDeleteIntent(items)
                if (request == null) finish(items, restore)
                else _uiEvents.send(TrashUiEvent.RequestMediaPermission(request) {
                    viewModelScope.launch(Dispatchers.IO) { finish(items, restore) }
                })
            } catch (cancelled: CancellationException) { _isLoading.value = false; throw cancelled }
            catch (error: Exception) { fail(error) }
        }
    }
    private suspend fun finish(items: List<TrashItem>, restore: Boolean) {
        try {
            if (restore) repository.confirmRestoreFromTrash(items) else repository.confirmPermanentlyDelete(items)
            _uiEvents.send(TrashUiEvent.ShowSnackbar(if (restore) "Media restored" else "Media deleted permanently"))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { fail(error) }
        finally { _isLoading.value = false }
    }
    private suspend fun fail(error: Exception) {
        _isLoading.value = false
        _uiEvents.send(TrashUiEvent.ShowSnackbar(error.message ?: "Media operation failed. Your trash records have been retained."))
    }
}
