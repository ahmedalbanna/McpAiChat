package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.mcpai.chat.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

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
    val editingLastMessage by viewModel.editingLastMessage.collectAsStateWithLifecycle()
    val reasoningExpanded by viewModel.reasoningExpanded.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

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
        // enableEdgeToEdge sets decorFitsSystemWindows(false), so adjustResize
        // does NOT shrink the window when the IME opens — the IME would draw
        // over the composer. imePadding() on the composer (not the whole
        // column) keeps it above the keyboard; the list keeps its space and
        // stays behind the IME, like the reference apps. The bottom stays
        // flush for the same reason as the top: this class of device reports
        // a wildly wrong navigation-bar inset, so no bottom padding is added
        // for the (rarely-empty) navigation bar.
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
            // An error takes precedence over the transient status line: it
            // stays until dismissed or until the next send/regenerate.
            error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp, end = 2.dp, bottom = 2.dp),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                        )
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.retryLastTurn()
                            },
                        ) {
                            Text(stringResource(R.string.error_retry))
                        }
                        IconButton(onClick = viewModel::dismissError) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_dismiss),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            } ?: status?.let { message ->
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
                    reasoningExpanded = reasoningExpanded,
                    onToggleToolGroup = viewModel::toggleToolGroup,
                    onToggleReasoning = viewModel::toggleReasoning,
                    sending = sending,
                    onRegenerate = viewModel::regenerateLast,
                    onEditLastMessage = viewModel::startEditLastMessage,
                )
                if (rows.isEmpty()) {
                    EmptyChatState(onSuggestion = viewModel::setComposerText)
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
                onStop = viewModel::cancelTurn,
                editingLastMessage = editingLastMessage,
                onCancelEdit = viewModel::cancelEditLastMessage,
                modifier = Modifier
                    .imePadding()
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
    reasoningExpanded: Set<Long>,
    onToggleToolGroup: (Long) -> Unit,
    onToggleReasoning: (Long) -> Unit,
    sending: Boolean,
    onRegenerate: () -> Unit,
    onEditLastMessage: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Screen-lifetime scope for scroll-actor work started from clicks. The
    // FAB unmounts itself when it flips pinnedToBottom, so it may not own
    // the coroutine that performs the jump.
    val listScope = androidx.compose.runtime.rememberCoroutineScope()
    // Whether the list is parked at the end. Starts true so the first content
    // emission always opens at the newest rows; afterwards it tracks the
    // user's scroll position exactly, so scrolling up during streaming never
    // gets yanked back to the bottom.
    var pinnedToBottom by remember { mutableStateOf(true) }
    var didInitialScroll by remember { mutableStateOf(false) }

    // Snap to the absolute end. scrollToItem(lastIndex) parks the item's top
    // at the viewport start, which is wrong for rows taller than the screen;
    // a huge scrollBy is clamped by the scroll actor at the content boundary
    // instead, regardless of item heights.
    suspend fun snapToEnd() {
        // A scrollBy issued before the rows are measured is consumed by
        // nothing, so wait for the first layout pass (rows reload from the
        // database on fresh open). Bounded to ~2s of frames; falls back to a
        // pending-request scroll if layout never completes.
        var tries = 0
        while (listState.layoutInfo.visibleItemsInfo.isEmpty() && tries < 120) {
            androidx.compose.runtime.withFrameNanos { }
            tries++
        }
        if (listState.layoutInfo.visibleItemsInfo.isEmpty() && rows.isNotEmpty()) {
            listState.scrollToItem(rows.size - 1)
            return
        }
        listState.scrollBy(1_000_000f)
    }

    // LazyListState.canScrollForward is derived from the last VISIBLE item's
    // index, so an assistant row taller than the viewport (few-item
    // conversation with no tool calls) keeps it false everywhere and the
    // jump-to-latest FAB never appears. Instead, scroll position (snapshot
    // backed) drives a geometry check against the live layout: parked means
    // the last item's bottom has reached the viewport end.
    androidx.compose.runtime.LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            pinnedToBottom = lastVisible == null ||
                (lastVisible.index == info.totalItemsCount - 1 &&
                    lastVisible.offset + lastVisible.size <= info.viewportEndOffset)
        }
    }

    // First emission: jump to the end (a freshly opened history shows the newest).
    androidx.compose.runtime.LaunchedEffect(rows.size) {
        if (!didInitialScroll && rows.isNotEmpty()) {
            didInitialScroll = true
            snapToEnd()
        }
    }

    // The IME shrinks the viewport without changing the scroll offset, so a
    // list parked at the end would show a cut-off tail after the keyboard
    // opens (or closes). Re-snap whenever the IME height changes.
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    androidx.compose.runtime.LaunchedEffect(imeBottom) {
        if (didInitialScroll && pinnedToBottom && rows.isNotEmpty()) {
            snapToEnd()
        }
    }

    // Afterwards: pin only while parked at the end (stream-aware).
    androidx.compose.runtime.LaunchedEffect(rows.size, rows.lastOrNull()) {
        if (didInitialScroll && pinnedToBottom && rows.isNotEmpty()) {
            snapToEnd()
        }
    }

    // Interleave a date divider before the first row of each calendar day.
    val uiItems = remember(rows) {
        val result = ArrayList<UiItem>(rows.size + 2)
        val zone = java.time.ZoneId.systemDefault()
        var previousDay: Long? = null
        for (row in rows) {
            val day = com.mcpai.chat.util.DayDividers.epochDayOf(row.createdAt, zone)
            if (com.mcpai.chat.util.DayDividers.needsDivider(previousDay, day)) {
                result += UiItem.Divider(day)
            }
            previousDay = day
            result += UiItem.Row(row)
        }
        result
    }

    // Regenerate belongs to the newest answered assistant row only; earlier
    // answers are frozen history. The edit action belongs to the newest
    // user row for the same reason.
    val lastAssistantId = rows
        .lastOrNull { it is ChatRow.Assistant && !it.streaming && it.text.isNotBlank() }
        ?.let { (it as ChatRow.Assistant).id }
    val lastUserId = rows.lastOrNull { it is ChatRow.User }?.let { (it as ChatRow.User).id }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(uiItems, key = { item ->
                when (item) {
                    is UiItem.Divider -> "d${item.dayEpoch}"
                    is UiItem.Row -> "r${item.row.key()}"
                }
            }) { item ->
                when (item) {
                    is UiItem.Divider -> DayDivider(item.dayEpoch)
                    is UiItem.Row -> when (val row = item.row) {
                        is ChatRow.User -> UserMessageRow(
                            row,
                            showEdit = row.id == lastUserId && !sending,
                            onEdit = onEditLastMessage,
                        )
                        is ChatRow.Assistant -> AssistantMessageRow(
                            row,
                            reasoningExpanded = row.id in reasoningExpanded,
                            onToggleReasoning = onToggleReasoning,
                            showRegenerate = row.id == lastAssistantId && !sending,
                            onRegenerate = onRegenerate,
                        )
                        is ChatRow.ToolGroup -> ToolGroupRow(row, onToggleToolGroup)
                        is ChatRow.Thinking -> ThinkingRow()
                        is ChatRow.Tool -> ToolGroupRow(ChatRow.ToolGroup(row.id, listOf(row), expanded = false, createdAt = row.createdAt), onToggleToolGroup)
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !pinnedToBottom,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            androidx.compose.material3.FilledTonalIconButton(
                onClick = {
                    // Optimistic pin: the scroll-position collector below
                    // confirms the end from the next layout pass on.
                    pinnedToBottom = true
                    if (rows.isNotEmpty()) {
                        listScope.launch { snapToEnd() }
                    }
                },
                modifier = Modifier.padding(end = 14.dp, bottom = 12.dp).size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(R.string.action_jump_to_latest),
                )
            }
        }
    }
}

@Composable
private fun EmptyChatState(onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.suggestion_tools),
        stringResource(R.string.suggestion_ai_news),
        stringResource(R.string.suggestion_mcp_spec),
        stringResource(R.string.suggestion_email),
    )
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
        // Suggestion chips pre-fill the composer; sending stays explicit. One
        // horizontally scrollable row keeps them from stacking one per line.
        Row(
            modifier = Modifier
                .padding(top = 22.dp)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            suggestions.forEach { suggestion ->
                androidx.compose.material3.AssistChip(
                    onClick = { onSuggestion(suggestion) },
                    label = { Text(suggestion) },
                )
            }
        }
    }
}

/** A date divider and a message row in the lazy list. */
private sealed interface UiItem {
    /** epochDay of the day a divider labels. */
    data class Divider(val dayEpoch: Long) : UiItem

    data class Row(val row: ChatRow) : UiItem
}

/**
 * Hairline-separated date chip shown above the first message of each
 * calendar day: today and yesterday get labels, anything older a date.
 */
@Composable
private fun DayDivider(dayEpoch: Long) {
    val today = java.time.LocalDate.now().toEpochDay()
    val label = com.mcpai.chat.util.DayDividers.formatDayLabel(
        dayEpoch,
        today,
        todayLabel = stringResource(R.string.day_today),
        yesterdayLabel = stringResource(R.string.day_yesterday),
        formatter = { day ->
            val date = java.util.Date.from(
                java.time.LocalDate.ofEpochDay(day)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant(),
            )
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(date)
        },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/** Reads [createdAt] from any row kind. */
private val ChatRow.createdAt: Long
    get() = when (this) {
        is ChatRow.User -> createdAt
        is ChatRow.Assistant -> createdAt
        is ChatRow.Tool -> createdAt
        is ChatRow.Thinking -> createdAt
        is ChatRow.ToolGroup -> createdAt
    }

/** Stable lazy-list keys for [ChatRow]. */
private fun ChatRow.key(): String = when (this) {
    is ChatRow.User -> "u$id"
    is ChatRow.Assistant -> "a$id"
    is ChatRow.Tool -> "t$id"
    is ChatRow.Thinking -> "thinking"
    is ChatRow.ToolGroup -> "g$id"
}
