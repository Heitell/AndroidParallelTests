package com.example.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalog.data.AppGraph
import com.example.catalog.data.ItemDetails
import com.example.catalog.data.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Content(val item: ItemDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}

class DetailsViewModel(private val id: Int) : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = DetailsState.Loading
        viewModelScope.launch {
            _state.value = try {
                DetailsState.Content(AppGraph.api.item(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailsState.Error(e.toUserMessage())
            }
        }
    }
}
