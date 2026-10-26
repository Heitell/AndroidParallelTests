package com.example.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.catalog.data.AppGraph
import com.example.catalog.data.ItemDetails
import com.example.catalog.data.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Content(val item: ItemDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}

/**
 * Одна модель на экран карточки. Переживает поворот экрана; при выходе из карточки
 * запрос отменяется, а при повторном открытии товар загружается заново.
 */
class DetailsViewModel : ViewModel() {
    private val _state = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val state: StateFlow<DetailsState> = _state
    private var id: Int? = null
    private var job: Job? = null

    fun open(id: Int) {
        if (this.id == id) return
        this.id = id
        load()
    }

    fun close() {
        job?.cancel()
        id = null
        _state.value = DetailsState.Loading
    }

    fun retry() = load()

    private fun load() {
        val id = id ?: return
        job?.cancel()
        _state.value = DetailsState.Loading
        job = viewModelScope.launch {
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
