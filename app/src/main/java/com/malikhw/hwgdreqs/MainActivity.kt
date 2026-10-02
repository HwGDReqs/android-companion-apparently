package com.malikhw.hwgdreqs

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
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
                    onDevices = { finish() },
                    onCopy = { levelId ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Level ID", levelId))
                        Toast.makeText(this, R.string.level_id_copied, Toast.LENGTH_SHORT).show()
                    },
                    onDelete = vm::deleteEntry,
                    onSubmitActionPassword = vm::submitActionPassword,
                    onDismissAuthPrompt = vm::dismissAuthPrompt,
                    onDismissActionError = vm::dismissActionError,
                )
            }
        }
    }
}
