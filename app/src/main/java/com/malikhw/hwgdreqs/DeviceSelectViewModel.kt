package com.malikhw.hwgdreqs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.malikhw.hwgdreqs.network.AuthCheck
import com.malikhw.hwgdreqs.network.DeviceDiscovery
import com.malikhw.hwgdreqs.network.DiscoveredDevice
import com.malikhw.hwgdreqs.network.baseUrlFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceSelectViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as HwGDReqsApp
    private val prefs = graph.authPrefs
    private val api = graph.api
    private val discovery = DeviceDiscovery(app)

    val devices = discovery.devices
    val searching = discovery.searching
    private val _autoConnected = MutableStateFlow<DiscoveredDevice?>(null)
    val autoConnected = _autoConnected.asStateFlow()
    private val checkedHosts = mutableSetOf<String>()

    fun startScan() = discovery.start()
    fun stopScan() = discovery.stop()

    fun checkForExistingPairing(device: DiscoveredDevice) {
        val hostKey = device.host
        val token = prefs.getToken(hostKey) ?: return
        if (!checkedHosts.add(hostKey)) return

        viewModelScope.launch {
            when (api.verifyAuth(baseUrlFor(device.host, device.port), token)) {
                AuthCheck.Valid -> _autoConnected.value = device
                AuthCheck.Rejected -> prefs.clearToken(hostKey)
                is AuthCheck.Failed -> Unit
            }
        }
    }

    fun consumeAutoConnected() {
        _autoConnected.value = null
    }

    fun rescan() {
        discovery.stop()
        discovery.start()
    }

    override fun onCleared() {
        discovery.stop()
    }
}
