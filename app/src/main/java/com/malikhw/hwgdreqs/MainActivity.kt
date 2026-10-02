package com.malikhw.hwgdreqs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.malikhw.hwgdreqs.ui.QueueScreen
import com.malikhw.hwgdreqs.ui.theme.HwGDReqsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: QueueViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollLoop() }
        }

        setContent {
            HwGDReqsTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                QueueScreen(
                    state = state,
                    address = "${vm.host}:${vm.port}",
                    onUnauthorized = { finish() },
                )
            }
        }
    }
}
