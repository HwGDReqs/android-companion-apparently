package com.malikhw.hwgdreqs

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malikhw.hwgdreqs.ui.PairingScreen
import com.malikhw.hwgdreqs.ui.theme.HwGDReqsTheme

class PairingActivity : ComponentActivity() {
    private val vm: PairingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HwGDReqsTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                PairingScreen(
                    deviceName = vm.deviceName,
                    state = state,
                    onPaired = ::openQueue,
                    onRetry = vm::begin,
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun openQueue() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(Extras.HOST, vm.host)
                .putExtra(Extras.PORT, vm.port)
        )
        finish()
    }
}
