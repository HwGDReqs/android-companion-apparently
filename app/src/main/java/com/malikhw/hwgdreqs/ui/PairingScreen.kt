package com.malikhw.hwgdreqs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malikhw.hwgdreqs.PairingState
import com.malikhw.hwgdreqs.R

@Composable
fun PairingScreen(
    deviceName: String,
    state: PairingState,
    onPaired: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    LaunchedEffect(state) {
        if (state is PairingState.Paired) onPaired()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                PairingState.Checking, PairingState.Paired -> {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }

                is PairingState.WaitingForPc -> {
                    Text(
                        stringResource(R.string.pair_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.found_fmt, deviceName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(stringResource(R.string.pair_enter_code), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = state.pin,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 8.sp,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pair_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    ) { Text(stringResource(R.string.cancel)) }
                }

                is PairingState.Failed -> {
                    Text(
                        stringResource(R.string.pair_failed_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.back)) }
                        Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
                    }
                }
            }
        }
    }
}
