package com.example.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalog.data.AppGraph
import com.example.catalog.data.Item
import com.example.catalog.data.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ListState {
    data object Loading : ListState
    data class Content(val items: List<Item>) : ListState
    data object Empty : ListState
    data class Error(val message: String) : ListState
}

data class CatalogUiState(
    val query: String = "",
    val list: ListState = ListState.Loading,
    val refreshing: Boolean = false,
)

class CatalogViewModel : ViewModel() {
    private val store = AppGraph.lastQuery
    private val _state = MutableStateFlow(CatalogUiState(query = store?.get().orEmpty()))
    val state: StateFlow<CatalogUiState> = _state
    private var job: Job? = null

    init {
        load(showLoading = true)
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        store?.set(_state.value.query.trim())
        load(showLoading = true)
    }

    fun clearSearch() {
        _state.update { it.copy(query = "") }
        store?.clear()
        load(showLoading = true)
    }

    fun retry() = load(showLoading = true)

    fun refresh() = load(showLoading = false)

    private fun load(showLoading: Boolean) {
        job?.cancel()
        _state.update {
            if (showLoading) it.copy(list = ListState.Loading, refreshing = false)
            else it.copy(refreshing = true)
        }
        val query = _state.value.query.trim().ifEmpty { null }
        job = viewModelScope.launch {
            val list = try {
                val items = AppGraph.api.items(query)
                if (items.isEmpty()) ListState.Empty else ListState.Content(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ListState.Error(e.toUserMessage())
            }
            _state.update { it.copy(list = list, refreshing = false) }
        }
    }
}
