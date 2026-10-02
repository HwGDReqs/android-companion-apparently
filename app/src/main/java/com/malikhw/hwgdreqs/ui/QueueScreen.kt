package com.malikhw.hwgdreqs.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malikhw.hwgdreqs.QueueUiState
import com.malikhw.hwgdreqs.R
import com.malikhw.hwgdreqs.network.QueueEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    state: QueueUiState,
    address: String,
    onUnauthorized: () -> Unit,
    onDevices: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSubmitActionPassword: (String) -> Unit,
    onDismissAuthPrompt: () -> Unit,
    onDismissActionError: () -> Unit,
) {
    LaunchedEffect(state.unauthorized) {
        if (state.unauthorized) onUnauthorized()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.queue_title))
                        if (state.loaded) {
                            Text(
                                text = stringResource(R.string.queue_subtitle_fmt, state.entries.size, address),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onDevices) {
                        Text(stringResource(R.string.devices))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.connectionLost) {
                Text(
                    text = stringResource(R.string.queue_connection_lost),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.background,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.queue_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.entries) { entry ->
                        QueueItem(entry, onCopy, onDelete)
                    }
                }
            }
        }
    }

    if (state.authPrompt) {
        var password by remember(state.authPrompt, state.wrongPassword) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismissAuthPrompt,
            title = {
                Text(
                    stringResource(
                        if (state.wrongPassword) R.string.wrong_password_title
                        else R.string.action_auth_title
                    )
                )
            },
            text = {
                Column {
                    Text(stringResource(R.string.action_auth_message))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.action_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSubmitActionPassword(password) },
                    enabled = password.isNotBlank(),
                ) { Text(stringResource(R.string.submit)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissAuthPrompt) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    state.actionError?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissActionError,
            title = { Text(stringResource(R.string.action_failed_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissActionError) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueItem(
    entry: QueueEntry,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showDetails by remember(entry.id) { mutableStateOf(false) }
    var showBadRequesterReason by remember(entry.id) { mutableStateOf(false) }
    val contentColor = if (entry.badRequester) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showDetails = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.badRequester) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(difficultyIconResource(entry.difficulty)),
                    contentDescription = entry.difficulty,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = entry.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                platformIconResource(entry.platform)?.let { icon ->
                    Image(
                        painter = painterResource(icon),
                        contentDescription = entry.platform,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(24.dp),
                    )
                }
                if (entry.badRequester) {
                    IconButton(
                        onClick = { showBadRequesterReason = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = stringResource(R.string.bad_requester_warning),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.queue_by_fmt, entry.author),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.badRequester) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.queue_from_fmt, entry.requester),
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.badRequester) contentColor else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { onCopy(entry.id) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.copy_level_id),
                    )
                }
                IconButton(onClick = { onDelete(entry.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete_entry),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showBadRequesterReason) {
        AlertDialog(
            onDismissRequest = { showBadRequesterReason = false },
            title = { Text(stringResource(R.string.bad_requester_title)) },
            text = {
                Text(entry.badRequesterReason.ifBlank { stringResource(R.string.bad_requester_no_reason) })
            },
            confirmButton = {
                TextButton(onClick = { showBadRequesterReason = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(entry.name.ifBlank { stringResource(R.string.queue_entry_details) }) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.details.forEach { (key, value) ->
                        Column {
                            Text(
                                key,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}

private fun difficultyIconResource(difficulty: String): Int = when {
    difficulty.endsWith("Demon", ignoreCase = true) -> R.drawable.demon
    difficulty.equals("Auto", ignoreCase = true) -> R.drawable.auto
    difficulty.equals("Easy", ignoreCase = true) -> R.drawable.easy
    difficulty.equals("Normal", ignoreCase = true) -> R.drawable.normal
    difficulty.equals("Hard", ignoreCase = true) -> R.drawable.hard
    difficulty.equals("Harder", ignoreCase = true) -> R.drawable.harder
    difficulty.equals("Insane", ignoreCase = true) -> R.drawable.insane
    else -> R.drawable.unrated
}

private fun platformIconResource(platform: String): Int? = when (platform.lowercase()) {
    "twitch" -> R.drawable.ic_platform_twitch
    "youtube" -> R.drawable.ic_platform_youtube
    else -> null
}
