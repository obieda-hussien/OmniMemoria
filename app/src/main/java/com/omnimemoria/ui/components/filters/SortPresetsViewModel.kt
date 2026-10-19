package com.omnimemoria.ui.components.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnimemoria.data.repository.SortPresetRepository
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.SortConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class SortPresetsViewModel @Inject constructor(private val repository: SortPresetRepository) : ViewModel() {
    val presets = repository.getAll().map { rows -> rows.filter { it.name != "__active_query__" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun save(name: String, sort: SortConfig, filter: FilterConfig) = run {
        viewModelScope.launch { safely { repository.save(name, sort, filter) } }
    }
    fun delete(id: Int) { viewModelScope.launch { safely { repository.delete(id) } } }
    fun clearError() { _error.value = null }
    private suspend fun safely(action: suspend () -> Unit) {
        _error.value = null
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _error.value = error.message ?: "Unable to save preset" }
    }
}
