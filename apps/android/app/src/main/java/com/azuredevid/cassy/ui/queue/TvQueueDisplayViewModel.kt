package com.azuredevid.cassy.ui.queue

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.PublicQueueRepository
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class TvQueueDisplayUiState(
    val displayToken: String = "",
    val isPaired: Boolean = false,
    val storeName: String = "",
    val preparingNumbers: List<String> = emptyList(),
    val readyNumbers: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null
)

/**
 * ViewModel for the 10-foot Android TV queue board (`TvQueueDisplayScreen`).
 * Pairs with an owner-issued display token and polls `/api/v1/public/queue` on a structured coroutine loop.
 */
class TvQueueDisplayViewModel(
    private val repository: PublicQueueRepository,
    private val pollIntervalMs: Long = 5_000L
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvQueueDisplayUiState())
    val uiState: StateFlow<TvQueueDisplayUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun setTokenInput(token: String) {
        _uiState.update { it.copy(displayToken = token.trim(), error = null) }
    }

    fun pairAndStart() {
        val token = _uiState.value.displayToken.trim()
        if (token.isEmpty()) return
        startPolling(token)
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        _uiState.value = TvQueueDisplayUiState()
    }

    fun refreshNow() {
        val token = _uiState.value.displayToken.trim()
        if (token.isEmpty()) return
        viewModelScope.launch { fetchOnce(token) }
    }

    private fun startPolling(token: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            fetchOnce(token)
            while (isActive) {
                delay(pollIntervalMs)
                fetchOnce(token)
            }
        }
    }

    private suspend fun fetchOnce(token: String) {
        repository.fetchQueue(token)
            .onSuccess { snap ->
                val preparing = snap.orders
                    .filter { it.status == "confirmed" || it.status == "preparing" }
                    .map { it.queueNumber }
                val ready = snap.orders
                    .filter { it.status == "ready" }
                    .map { it.queueNumber }
                _uiState.update {
                    it.copy(
                        isPaired = true,
                        storeName = snap.storeName,
                        preparingNumbers = preparing,
                        readyNumbers = ready,
                        isLoading = false,
                        error = null
                    )
                }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = err.toUiText(R.string.tv_queue_load_failed)
                    )
                }
            }
    }
}
