package com.malikhw.hwgdreqs.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet6Address

class DeviceDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private val pending = ArrayDeque<NsdServiceInfo>()
    private val retries = mutableMapOf<String, Int>()
    private var resolving = false

    @Synchronized
    fun start() {
        if (listener != null) return
        _devices.value = emptyList()
        try {
            multicastLock = wifi.createMulticastLock("hwgdreqs-mdns").apply {
                setReferenceCounted(false)
                acquire()
            }
            val l = newListener()
            listener = l
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            releaseLock()
            listener = null
            _searching.value = false
        }
    }

    @Synchronized
    fun stop() {
        val l = listener ?: return
        listener = null
        pending.clear()
        retries.clear()
        try {
            nsd.stopServiceDiscovery(l)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop discovery", e)
        }
        releaseLock()
        _searching.value = false
    }

    private fun releaseLock() {
        try {
            multicastLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        multicastLock = null
    }

    private fun newListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Discovery started: $regType")
            _searching.value = true
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
            if (serviceInfo.serviceType.contains("hwgdreqs", ignoreCase = true)) {
                enqueueResolve(serviceInfo)
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            synchronized(this@DeviceDiscovery) {
                pending.removeAll { it.serviceName == serviceInfo.serviceName }
            }
            _devices.update { list -> list.filterNot { it.serviceName == serviceInfo.serviceName } }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.d(TAG, "Discovery stopped: $serviceType")
            _searching.value = false
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Start discovery failed: $errorCode")
            _searching.value = false
            stop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Stop discovery failed: $errorCode")
        }
    }


    @Synchronized
    private fun enqueueResolve(info: NsdServiceInfo) {
        pending.addLast(info)
        resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val next = pending.removeFirstOrNull() ?: return
        resolving = true
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed (${serviceInfo.serviceName}): $errorCode")
                    synchronized(this@DeviceDiscovery) {
                        val tries = (retries[serviceInfo.serviceName] ?: 0) + 1
                        retries[serviceInfo.serviceName] = tries
                        if (tries <= MAX_RESOLVE_RETRIES && listener != null) pending.addLast(serviceInfo)
                        resolving = false
                        resolveNext()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Resolved: ${serviceInfo.host}:${serviceInfo.port}")
                    toDevice(serviceInfo)?.let { device ->
                        _devices.update { list ->
                            list.filterNot { it.serviceName == device.serviceName } + device
                        }
                    }
                    synchronized(this@DeviceDiscovery) {
                        retries.remove(serviceInfo.serviceName)
                        resolving = false
                        resolveNext()
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "resolveService threw", e)
            resolving = false
        }
    }

    private fun toDevice(info: NsdServiceInfo): DiscoveredDevice? {
        val inet = info.host ?: return null
        val address = inet.hostAddress ?: return null
        val host = if (inet is Inet6Address) "[${address.substringBefore('%')}]" else address

        val txt = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8).orEmpty() }
        val port = info.port.takeIf { it > 0 } ?: txt["port"]?.toIntOrNull() ?: return null

        return DiscoveredDevice(
            serviceName = info.serviceName,
            host = host,
            port = port,
            login = txt["login"].orEmpty(),
            version = txt["version"].orEmpty(),
        )
    }

    private companion object {
        const val TAG = "mDNS"
        const val SERVICE_TYPE = "_hwgdreqs._tcp."
        const val MAX_RESOLVE_RETRIES = 2
    }
}
