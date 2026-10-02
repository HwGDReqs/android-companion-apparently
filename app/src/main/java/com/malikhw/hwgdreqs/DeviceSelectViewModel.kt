package com.malikhw.hwgdreqs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.malikhw.hwgdreqs.network.DeviceDiscovery

class DeviceSelectViewModel(app: Application) : AndroidViewModel(app) {
    private val discovery = DeviceDiscovery(app)

    val devices = discovery.devices
    val searching = discovery.searching

    fun startScan() = discovery.start()
    fun stopScan() = discovery.stop()

    fun rescan() {
        discovery.stop()
        discovery.start()
    }

    override fun onCleared() {
        discovery.stop()
    }
}
