package com.mcpai.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpai.chat.R
import com.mcpai.chat.ui.markdown.MarkdownContent
import com.mcpai.chat.ui.theme.BubbleTextStyle
import com.mcpai.chat.ui.theme.chatColors

/** User bubble shape: bottom-left clipped like the reference apps. */
private val UserBubbleShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
private val AssistantBubbleShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
private const val MAX_BUBBLE_WIDTH = 560

@Composable
fun UserMessageRow(
    row: ChatRow.User,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 60.dp, top = 5.dp, end = 12.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (!row.attachmentName.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(chatColors.composerIconBg)
                    .padding(start = 10.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = row.attachmentName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.dp).widthIn(max = 260.dp),
                )
            }
        }
        Surface(
            shape = UserBubbleShape,
            color = chatColors.bubbleUser,
            contentColor = chatColors.bubbleUserText,
            modifier = Modifier.widthIn(max = MAX_BUBBLE_WIDTH.dp),
        ) {
            Text(
                text = row.text,
                style = BubbleTextStyle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
fun AssistantMessageRow(
    row: ChatRow.Assistant,
    reasoningExpanded: Boolean,
    onToggleReasoning: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Only the newest assistant row offers a regenerate action. */
    showRegenerate: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 5.dp, end = 60.dp, bottom = 5.dp),
    ) {
        if (row.reasoning.isNotBlank()) {
            ReasoningDisclosure(
                row = row,
                expanded = reasoningExpanded,
                onToggle = onToggleReasoning,
            )
        }

        if (row.text.isEmpty() && row.streaming) {
            // Waiting for the first delta: muted waiting marker, no bubble.
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.ai_thinking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            return
        }

        // An assistant turn that only requested tools renders nothing.
        if (row.text.isEmpty() && !row.streaming && row.reasoning.isEmpty()) return

        Surface(
            shape = AssistantBubbleShape,
            color = chatColors.bubbleAi,
            contentColor = chatColors.bubbleAiText,
            modifier = Modifier
                .widthIn(max = MAX_BUBBLE_WIDTH.dp)
                .border(0.5.dp, chatColors.bubbleAiStroke, AssistantBubbleShape),
        ) {
            if (row.streaming) {
                Text(
                    text = row.text,
                    style = BubbleTextStyle,
                    color = chatColors.bubbleAiText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                )
            } else {
                MarkdownContent(
                    markdown = row.text,
                    textColor = chatColors.bubbleAiText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }

        // Per-message copy of the raw markdown with transient confirmation.
        if (!row.streaming && row.text.isNotEmpty()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            var copied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(copied) {
                if (copied) {
                    kotlinx.coroutines.delay(1800)
                    copied = false
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 2.dp, top = 1.dp),
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        val clipboard = context.applicationContext.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE,
                        ) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                context.getString(R.string.action_copy_message),
                                row.text,
                            ),
                        )
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                        copied = true
                    },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (copied) {
                            Icons.Filled.Done
                        } else {
                            Icons.Filled.ContentCopy
                        },
                        contentDescription = androidx.compose.ui.res.stringResource(
                            if (copied) R.string.action_copied else R.string.action_copy_message,
                        ),
                        modifier = Modifier.size(17.dp),
                        tint = if (copied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                // Regenerate this answer: truncate everything after the last
                // user message and re-run that turn in place.
                if (showRegenerate && onRegenerate != null) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                            )
                            onRegenerate()
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = androidx.compose.ui.res.stringResource(
                                R.string.action_regenerate,
                            ),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (copied) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.action_copied),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Muted inline "Thinking Ns" / "Thought Ns" disclosure above the answer.
 * Collapsed by default; the raw trace renders only while expanded, indented
 * and muted. Live turns tick the seconds; completed turns show the frozen
 * duration; history rows (never timed) show a plain "Thought" label.
 */
@Composable
private fun ReasoningDisclosure(
    row: ChatRow.Assistant,
    expanded: Boolean,
    onToggle: (Long) -> Unit,
) {
    val started = row.reasoningStartedAtMs
    val live = row.streaming && row.reasoningMs == null
    var tick by androidx.compose.runtime.remember(started) { androidx.compose.runtime.mutableStateOf(1L) }
    if (live && started != null) {
        androidx.compose.runtime.LaunchedEffect(started) {
            while (true) {
                tick = com.mcpai.chat.util.ThoughtSeconds.of(System.currentTimeMillis() - started)
                kotlinx.coroutines.delay(500)
            }
        }
    }
    val seconds = row.reasoningMs?.let { com.mcpai.chat.util.ThoughtSeconds.of(it) }
        ?: if (started != null) tick else null
    val label = when {
        live -> if (seconds != null) {
            androidx.compose.ui.res.stringResource(R.string.thinking_duration, seconds)
        } else {
            androidx.compose.ui.res.stringResource(R.string.thinking_label)
        }
        seconds != null -> androidx.compose.ui.res.stringResource(R.string.thought_duration, seconds)
        else -> androidx.compose.ui.res.stringResource(R.string.thought_label)
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 2.dp, bottom = if (expanded) 2.dp else 6.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle(row.id) }
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Text(
                text = row.reasoning.trim(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 1.dp, bottom = 6.dp),
            )
        }
    }
}

/** Ephemeral waiting marker between model turns: muted ticker, no chrome. */
@Composable
fun ThinkingRow(modifier: Modifier = Modifier) {
    val startedAt = androidx.compose.runtime.remember { System.currentTimeMillis() }
    var seconds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            seconds = com.mcpai.chat.util.ThoughtSeconds.of(System.currentTimeMillis() - startedAt)
            kotlinx.coroutines.delay(500)
        }
    }
    Text(
        text = androidx.compose.ui.res.stringResource(R.string.thinking_duration, seconds),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    )
}

/**
 * Compact tool-call run: one collapsible marker with
 * `Tools used: N · Calls: M`, collapsed by default. Expanding reveals a
 * minimal name/status list; raw arguments and results never render here.
 */
@Composable
fun ToolGroupRow(
    row: ChatRow.ToolGroup,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val anyRunning = row.tools.any { it.running }
    val toolCount = row.tools.distinctBy { it.name }.size
    val callCount = row.tools.size
    val summary = androidx.compose.ui.res.stringResource(
        R.string.tools_used_calls,
        toolCount,
        callCount,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 7.dp, end = 60.dp, bottom = 7.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clickable { onToggle(row.id) },
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(chatColors.composerIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = if (row.expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f, fill = false),
            )
            if (anyRunning) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(start = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(
            visible = row.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val runningLabel = androidx.compose.ui.res.stringResource(R.string.label_running)
                val doneLabel = androidx.compose.ui.res.stringResource(R.string.label_done)
                val errorLabel = androidx.compose.ui.res.stringResource(R.string.label_error)
                val declinedLabel = androidx.compose.ui.res.stringResource(R.string.label_declined)
                row.tools.forEach { tool ->
                    Text(
                        text = callLine(tool, runningLabel, doneLabel, errorLabel, declinedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.4f.sp,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun callLine(
    tool: ChatRow.Tool,
    runningLabel: String,
    doneLabel: String,
    errorLabel: String,
    declinedLabel: String,
): String {
    val status = when {
        tool.running -> runningLabel
        tool.declined -> declinedLabel
        tool.isError -> errorLabel
        else -> doneLabel
    }
    if (!tool.running && !tool.isError && !tool.declined) {
        return "• ${tool.name} · $status"
    }
    val detail = tool.result
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(80)
        .orEmpty()
    return if (detail.isEmpty()) {
        "• ${tool.name} · $status"
    } else {
        "• ${tool.name} · $status · $detail"
    }
}
