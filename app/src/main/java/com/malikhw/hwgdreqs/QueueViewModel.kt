package com.malikhw.hwgdreqs

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.malikhw.hwgdreqs.network.ApiException
import com.malikhw.hwgdreqs.network.QueueEntry
import com.malikhw.hwgdreqs.network.baseUrlFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QueueUiState(
    val entries: List<QueueEntry> = emptyList(),
    val loaded: Boolean = false,
    val connectionLost: Boolean = false,
    val unauthorized: Boolean = false,
)

class QueueViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = app as HwGDReqsApp
    private val prefs = graph.authPrefs
    private val api = graph.api

    val host: String = handle.get<String>(Extras.HOST).orEmpty()
    val port: Int = handle.get<Int>(Extras.PORT) ?: 0
    private val baseUrl = baseUrlFor(host, port)

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
        api.getQueue(baseUrl, prefs.getToken(host)).fold(
            onSuccess = { list ->
                _state.update { it.copy(entries = list, loaded = true, connectionLost = false) }
            },
            onFailure = { e ->
                if (e is ApiException && e.code == "unauthorized") {
                    prefs.clearToken(host)
                    _state.update { it.copy(unauthorized = true) }
                } else {
                    _state.update { it.copy(connectionLost = true) }
                }
            },
        )
    }

    private companion object {
        const val REFRESH_MS = 2_000L
    }
}
