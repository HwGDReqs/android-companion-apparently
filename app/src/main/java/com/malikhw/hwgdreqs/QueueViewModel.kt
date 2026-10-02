package com.malikhw.hwgdreqs

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.malikhw.hwgdreqs.network.ApiException
import com.malikhw.hwgdreqs.network.QueueEntry
import com.malikhw.hwgdreqs.network.baseUrlFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QueueUiState(
    val entries: List<QueueEntry> = emptyList(),
    val loaded: Boolean = false,
    val connectionLost: Boolean = false,
    val unauthorized: Boolean = false,
    val authPrompt: Boolean = false,
    val wrongPassword: Boolean = false,
    val actionError: String? = null,
)

class QueueViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = app as HwGDReqsApp
    private val prefs = graph.authPrefs
    private val api = graph.api

    val host: String = handle.get<String>(Extras.HOST).orEmpty()
    val port: Int = handle.get<Int>(Extras.PORT) ?: 0
    private val baseUrl = baseUrlFor(host, port)
    private var pendingDeleteId: String? = null
    private var pendingActionPassword: String? = null

    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    suspend fun pollLoop(): Nothing {
        while (true) {
            val started = SystemClock.elapsedRealtime()
            refresh()
            val elapsed = SystemClock.elapsedRealtime() - started
            delay((REFRESH_MS - elapsed).coerceAtLeast(250))
        }
    }

    private suspend fun refresh() {
        api.getQueue(baseUrl, prefs.getToken(host), prefs.getActionPassword(host)).fold(
            onSuccess = { list ->
                _state.update { it.copy(entries = list, loaded = true, connectionLost = false) }
            },
            onFailure = { e ->
                when {
                    e is ApiException && e.code == "unauthorized" -> {
                        prefs.clearToken(host)
                        _state.update { it.copy(unauthorized = true) }
                    }

                    e is ApiException && e.code in setOf("needs_auth", "wrong_password_i_suppose") ->
                        showAuthPrompt(e.code)

                    else -> _state.update { it.copy(connectionLost = true) }
                }
            },
        )
    }

    fun deleteEntry(levelId: String) {
        viewModelScope.launch { performDelete(levelId) }
    }

    fun submitActionPassword(password: String) {
        if (password.isBlank()) return
        pendingActionPassword = password
        _state.update { it.copy(authPrompt = false, wrongPassword = false) }
        pendingDeleteId?.let { id -> viewModelScope.launch { performDelete(id) } }
    }

    fun dismissAuthPrompt() {
        pendingDeleteId = null
        pendingActionPassword = null
        _state.update { it.copy(authPrompt = false, wrongPassword = false) }
    }

    fun dismissActionError() {
        _state.update { it.copy(actionError = null) }
    }

    private suspend fun performDelete(levelId: String) {
        pendingDeleteId = levelId
        api.deleteLevel(
            baseUrl = baseUrl,
            token = prefs.getToken(host),
            levelId = levelId,
            actionPassword = pendingActionPassword ?: prefs.getActionPassword(host),
        ).fold(
            onSuccess = {
                pendingDeleteId = null
                pendingActionPassword?.let { prefs.setActionPassword(host, it) }
                pendingActionPassword = null
                refresh()
            },
            onFailure = { error ->
                if (error is ApiException) {
                    when (error.code) {
                        "needs_auth", "wrong_password_i_suppose" -> showAuthPrompt(error.code)
                        "unauthorized" -> {
                            prefs.clearToken(host)
                            pendingDeleteId = null
                            pendingActionPassword = null
                            _state.update { it.copy(unauthorized = true) }
                        }

                        else -> {
                            pendingDeleteId = null
                            pendingActionPassword = null
                            _state.update { it.copy(actionError = error.code) }
                        }
                    }
                } else {
                    pendingDeleteId = null
                    pendingActionPassword = null
                    _state.update { it.copy(actionError = error.message ?: "Request failed") }
                }
            },
        )
    }

    private fun showAuthPrompt(error: String) {
        if (error == "wrong_password_i_suppose") prefs.clearActionPassword(host)
        pendingActionPassword = null
        _state.update {
            it.copy(
                authPrompt = true,
                wrongPassword = error == "wrong_password_i_suppose",
            )
        }
    }

    private companion object {
        const val REFRESH_MS = 1_000L
    }
}
