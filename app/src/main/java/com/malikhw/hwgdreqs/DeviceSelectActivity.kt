package com.malikhw.hwgdreqs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.malikhw.hwgdreqs.ui.DeviceSelectScreen
import com.malikhw.hwgdreqs.ui.theme.HwGDReqsTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DeviceSelectActivity : ComponentActivity() {
    private val vm: DeviceSelectViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.startScan()
                launch {
                    vm.devices.collect { devices ->
                        devices.forEach(vm::checkForExistingPairing)
                    }
                }
                launch {
                    vm.autoConnected.collect { device ->
                        if (device != null) {
                            vm.consumeAutoConnected()
                            startActivity(
                                Intent(this@DeviceSelectActivity, MainActivity::class.java)
                                    .putExtra(Extras.HOST, device.host)
                                    .putExtra(Extras.PORT, device.port)
                            )
                        }
                    }
                }
                try {
                    awaitCancellation()
                } finally {
                    vm.stopScan()
                }
            }
        }

        setContent {
            HwGDReqsTheme {
                DeviceSelectScreen(
                    devicesFlow = vm.devices,
                    searchingFlow = vm.searching,
                    onSelect = { d ->
                        startActivity(
                            Intent(this, PairingActivity::class.java)
                                .putExtra(Extras.HOST, d.host)
                                .putExtra(Extras.PORT, d.port)
                                .putExtra(Extras.NAME, d.serviceName)
                                .putExtra(Extras.LOGIN, d.login)
                        )
                    },
                    onHowToUse = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://hwgdreqs.github.io/mobile")))
                    },
                )
            }
        }
    }
}
