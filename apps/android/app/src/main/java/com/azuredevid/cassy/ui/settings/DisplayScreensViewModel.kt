package com.azuredevid.cassy.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azuredevid.cassy.R
import com.azuredevid.cassy.domain.repository.CreatedDisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayScreen
import com.azuredevid.cassy.domain.repository.DisplayTokenRepository
import com.azuredevid.cassy.ui.designsystem.UiText
import com.azuredevid.cassy.ui.designsystem.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DisplayScreensUiState(
    val isLoading: Boolean = false,
    val screens: List<DisplayScreen> = emptyList(),
    /** Name form for a new screen; null when closed. */
    val newName: String? = null,
    val isBusy: Boolean = false,
    /** The screen just created: its link is shown once, then only the hash stays on the server. */
    val created: CreatedDisplayScreen? = null,
    /** Screen waiting for the owner to confirm the revoke. */
    val revoking: DisplayScreen? = null,
    val message: UiText? = null,
    val messageOk: Boolean? = null
)

/**
 * Owner management of queue TV screens. A new screen returns a link with its code once; the owner
 * shares it to the TV. Revoking cuts that TV off at once.
 */
class DisplayScreensViewModel(private val repository: DisplayTokenRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DisplayScreensUiState())
    val uiState: StateFlow<DisplayScreensUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { refresh() }
    }

    fun openNew() = _uiState.update { it.copy(newName = "", message = null) }

    fun setName(value: String) = _uiState.update { s -> s.newName?.let { s.copy(newName = value.take(MAX_NAME)) } ?: s }

    fun dismissNew() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(newName = null, message = null) }
    }

    fun create() {
        val name = _uiState.value.newName?.trim().orEmpty()
        if (name.isEmpty() || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null) }
            repository.create(name)
                .onSuccess { created ->
                    _uiState.update { it.copy(isBusy = false, newName = null, created = created) }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isBusy = false, message = err.toUiText(R.string.display_msg_create_failed), messageOk = false) }
                }
        }
    }

    /** Closes the one-time link dialog; the link cannot be shown again. */
    fun dismissCreated() = _uiState.update { it.copy(created = null) }

    fun askRevoke(screen: DisplayScreen) = _uiState.update { it.copy(revoking = screen.takeIf { s -> s.isActive }) }

    fun cancelRevoke() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(revoking = null) }
    }

    fun confirmRevoke() {
        val screen = _uiState.value.revoking ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            repository.revoke(screen.id)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, revoking = null, message = UiText.res(R.string.display_msg_revoked, screen.name), messageOk = true) }
                    refresh()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isBusy = false, revoking = null, message = err.toUiText(R.string.display_msg_revoke_failed), messageOk = false) }
                }
        }
    }

    private suspend fun refresh() {
        repository.list()
            .onSuccess { list -> _uiState.update { it.copy(isLoading = false, screens = list.sortedBy { s -> !s.isActive }) } }
            .onFailure { err -> _uiState.update { it.copy(isLoading = false, message = err.toUiText(R.string.display_msg_load_failed), messageOk = false) } }
    }

    private companion object {
        const val MAX_NAME = 50
    }
}
