package com.malikhw.hwgdreqs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.malikhw.hwgdreqs.network.ApiException
import com.malikhw.hwgdreqs.network.AuthCheck
import com.malikhw.hwgdreqs.network.baseUrlFor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface PairingState {
    data object Checking : PairingState
    data class WaitingForPc(val pin: String) : PairingState
    data class Failed(val message: String) : PairingState
    data object Paired : PairingState
}

class PairingViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = app as HwGDReqsApp
    private val prefs = graph.authPrefs
    private val api = graph.api

    val host: String = handle.get<String>(Extras.HOST).orEmpty()
    val port: Int = handle.get<Int>(Extras.PORT) ?: 0
    val deviceName: String = handle.get<String>(Extras.NAME).orEmpty()
    private val baseUrl = baseUrlFor(host, port)

    private val _state = MutableStateFlow<PairingState>(PairingState.Checking)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        begin()
    }

    fun begin() {
        job?.cancel()
        job = viewModelScope.launch { run() }
    }

    private suspend fun run() {
        val token = prefs.getToken(host)
        if (token != null) {
            _state.value = PairingState.Checking
            when (val check = api.verifyAuth(baseUrl, token)) {
                AuthCheck.Valid -> {
                    _state.value = PairingState.Paired
                    return
                }

                AuthCheck.Rejected -> prefs.clearToken(host)
                is AuthCheck.Failed -> {
                    _state.value = PairingState.Failed(describe(IOException(check.message)))
                    return
                }
            }
        }

        val pin = api.generatePin()
        _state.value = PairingState.WaitingForPc(pin)
        api.pair(baseUrl, pin).fold(
            onSuccess = { newToken ->
                prefs.setToken(host, newToken)
                _state.value = PairingState.Paired
            },
            onFailure = { e -> _state.value = PairingState.Failed(describe(e)) },
        )
    }

    private fun describe(e: Throwable): String {
        val ctx = getApplication<Application>()
        return when {
            e is ApiException -> when (e.code) {
                "pairing_canceled" -> ctx.getString(R.string.err_declined)
                "invalid_pin" -> ctx.getString(R.string.err_invalid_pin)
                "desktop_pair_unavailable" -> ctx.getString(R.string.err_unavailable)
                else -> ctx.getString(R.string.err_generic, e.code)
            }

            e is IOException -> ctx.getString(R.string.err_network)
            else -> ctx.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
        }
    }
}
