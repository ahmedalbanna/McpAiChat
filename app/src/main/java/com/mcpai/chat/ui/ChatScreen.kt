package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.mcpai.chat.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The conversation screen: streaming assistant answers, grouped tool calls,
 * ephemeral thinking markers, the status banner and the composer card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    toolsSubtitle: String,
    voiceActive: Boolean,
    voiceTranscript: String?,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
    onClearConversation: () -> Unit,
    onShareConversation: () -> Unit,
    onPickAttachment: () -> Unit,
    onVoiceToggle: () -> Unit,
    onVoiceStop: () -> Unit,
) {
    val rows by viewModel.displayedRows.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val approvalRequest by viewModel.approval.collectAsStateWithLifecycle()
    val text by viewModel.composerText.collectAsStateWithLifecycle()

    val canSend = text.isNotBlank() && !sending && !voiceActive

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModel.setComposerText("")
        viewModel.send(t)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
    ) { _ ->
        // Ignore Scaffold's innerPadding: the status/navigation bar insets on
        // this class of device are reported wildly (hundreds of phantom px),
        // which would leave the conversation half the screen tall. The M3
        // TopAppBar consumes the status bar itself; the bottom stays flush
        // like the legacy XML layout did.
        // No bottom padding: adjustResize already shrinks the window when the
        // IME opens, and this class of device reports a wildly wrong
        // navigation-bar inset (hundreds of phantom pixels) that would leave
        // the composer floating above a dead zone.
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            ChatTopBar(
                subtitle = toolsSubtitle,
                onOpenServers = onOpenServers,
                onOpenSettings = onOpenSettings,
                onNewConversation = onNewConversation,
                onClearConversation = onClearConversation,
                onShareConversation = onShareConversation,
            )
            status?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    rows = rows,
                    onToggleToolGroup = viewModel::toggleToolGroup,
                )
                if (rows.isEmpty()) {
                    EmptyChatState()
                }
            }

            ChatComposer(
                text = text,
                onTextChange = viewModel::setComposerText,
                onSend = ::send,
                sending = sending,
                voiceActive = voiceActive,
                voiceTranscript = voiceTranscript,
                attachment = attachment,
                onAttach = onPickAttachment,
                onClearAttachment = viewModel::clearAttachment,
                onVoiceToggle = onVoiceToggle,
                onVoiceStop = onVoiceStop,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            )
        }
    }

    approvalRequest?.let { request ->
        ToolApprovalDialog(
            request = request,
            onAnswer = { approved, trust, autoApprove ->
                viewModel.approve(request, approved = approved, trust = trust, autoApprove = autoApprove)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    subtitle: String,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewConversation: () -> Unit,
    onClearConversation: () -> Unit,
    onShareConversation: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenServers) {
                Icon(Icons.Filled.Dns, contentDescription = stringResource(R.string.action_servers))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_settings))
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_chat))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_chat)) },
                        onClick = { menuOpen = false; onNewConversation() },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_clear_chat)) },
                        onClick = { menuOpen = false; onClearConversation() },
                        leadingIcon = { Icon(Icons.Filled.ClearAll, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share_conversation)) },
                        onClick = { menuOpen = false; onShareConversation() },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun MessageList(
    rows: List<ChatRow>,
    onToggleToolGroup: (Long) -> Unit,
) {
    val listState = rememberLazyListState()

    // Keep the viewport pinned to the newest rows, exactly like the legacy
    // behavior: a streaming token, a tool completion or a new message always
    // re-anchors to the end.
    androidx.compose.runtime.LaunchedEffect(rows.size, rows.lastOrNull()) {
        if (rows.isNotEmpty()) {
            listState.scrollToItem(rows.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(rows, key = { it.key() }) { row ->
            when (row) {
                is ChatRow.User -> UserMessageRow(row)
                is ChatRow.Assistant -> AssistantMessageRow(row)
                is ChatRow.ToolGroup -> ToolGroupRow(row, onToggleToolGroup)
                is ChatRow.Thinking -> ThinkingRow()
                is ChatRow.Tool -> ToolGroupRow(ChatRow.ToolGroup(row.id, listOf(row), expanded = false), onToggleToolGroup)
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ChatBubble,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.empty_conversation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** Stable lazy-list keys for [ChatRow]. */
private fun ChatRow.key(): String = when (this) {
    is ChatRow.User -> "u$id"
    is ChatRow.Assistant -> "a$id"
    is ChatRow.Tool -> "t$id"
    is ChatRow.Thinking -> "thinking"
    is ChatRow.ToolGroup -> "g$id"
}
