package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatEvent
import com.mcpai.chat.ui.theme.chatColors

/**
 * Tool-approval sheet: who (server) wants to run what (tool) with which
 * arguments, spelled out on one screen, with the two persistence options
 * spelled out as switches: session trust and persistent auto-approve.
 * Dismissing the dialog is a denial, never an approval.
 */
@Composable
fun ToolApprovalDialog(
    request: ChatEvent.ToolApprovalRequested,
    onAnswer: (approved: Boolean, trust: Boolean, autoApprove: Boolean) -> Unit,
) {
    var trustServer by remember { mutableStateOf(false) }
    var alwaysAllow by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { onAnswer(false, false, false) }) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header: icon + title + subtitle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(chatColors.composerIconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = stringResource(R.string.tool_approval_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.tool_approval_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Identity card: tool + server
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = request.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontStyle = FontStyle.Normal,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Dns,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = request.serverName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 7.dp),
                            )
                        }
                    }
                }

                // Arguments, bounded
                Text(
                    text = stringResource(R.string.approval_arguments_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                )
                val argsScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chatColors.codeBlockBg)
                        .verticalScroll(argsScroll)
                        .padding(10.dp),
                ) {
                    Text(
                        text = approvalArgsText(request.args),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.8f.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(0.5.dp)
                        .background(chatColors.divider),
                )

                // Switch 1: session trust
                SwitchRow(
                    label = stringResource(R.string.approval_trust_session_label, request.serverName),
                    hint = stringResource(R.string.approval_trust_session_hint),
                    checked = trustServer,
                    onCheckedChange = { trustServer = it },
                )

                // Switch 2: persistent auto-approve
                SwitchRow(
                    label = stringResource(R.string.approval_always_allow_label, request.name),
                    hint = stringResource(R.string.approval_always_allow_hint),
                    checked = alwaysAllow,
                    onCheckedChange = { alwaysAllow = it },
                )

                // Buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                ) {
                    TextButton(onClick = { onAnswer(false, false, false) }) {
                        Text(
                            text = stringResource(R.string.tool_approval_deny),
                            color = chatColors.toolAccent,
                        )
                    }
                    Button(
                        onClick = { onAnswer(true, trustServer, alwaysAllow) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.tool_approval_allow))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Pretty-prints tool arguments, bounded so huge payloads stay readable and cheap. */
internal fun approvalArgsText(raw: String): String {
    val pretty = runCatching { org.json.JSONObject(raw).toString(2) }.getOrDefault(raw)
    return if (pretty.length > 4000) pretty.take(4000) + "\n…" else pretty
}
