package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcpai.chat.R
import com.mcpai.chat.ui.theme.chatColors

/**
 * Composer card: voice preview, attachment chip, multiline input with icon
 * controls and a send button whose disabled state mirrors canSendMessage().
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    voiceActive: Boolean,
    voiceTranscript: String?,
    attachment: ComposerAttachment?,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onVoiceToggle: () -> Unit,
    onVoiceStop: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = text.isNotBlank() && !sending && !voiceActive

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp)),
    ) {
        Column {
            if (voiceActive) {
                VoicePreviewRow(
                    transcript = voiceTranscript,
                    onVoiceStop = onVoiceStop,
                )
            }
            if (attachment != null) {
                AttachmentPreviewRow(
                    attachment = attachment,
                    onClearAttachment = onClearAttachment,
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(start = 8.dp, top = 6.dp, end = 5.dp, bottom = 6.dp),
            ) {
                composerIconButton(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = stringResource(R.string.action_attach),
                    onClick = onAttach,
                )

                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(stringResource(R.string.hint_message))
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (voiceActive) chatColors.voiceBackground else chatColors.composerIconBg,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onVoiceToggle, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (voiceActive) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = stringResource(
                                if (voiceActive) {
                                    R.string.action_stop_voice_input
                                } else {
                                    R.string.action_voice_input
                                },
                            ),
                            tint = if (voiceActive) chatColors.voiceOn else chatColors.composerIconFg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                sending -> chatColors.sendBackground
                                canSend -> chatColors.sendBackground
                                else -> chatColors.sendDisabledBackground
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { if (sending) onStop() else if (canSend) onSend() },
                        enabled = sending || canSend,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (sending) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = stringResource(R.string.action_stop_generating),
                                tint = chatColors.sendForeground,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = stringResource(R.string.action_send),
                                tint = if (canSend) chatColors.sendForeground else chatColors.sendDisabledForeground,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun composerIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(chatColors.composerIconBg),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = chatColors.composerIconFg,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun VoicePreviewRow(
    transcript: String?,
    onVoiceStop: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = chatColors.voiceContainer,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(44.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = chatColors.voiceForeground,
            )
            Text(
                text = transcript?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.voice_speak_now),
                style = MaterialTheme.typography.bodySmall,
                color = chatColors.voiceForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(chatColors.voiceBackground),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onVoiceStop, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.action_stop_voice_input),
                        tint = chatColors.voiceOn,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewRow(
    attachment: ComposerAttachment,
    onClearAttachment: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = chatColors.composerIconBg,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(40.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
        ) {
            if (attachment.sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (attachment.sending) {
                    stringResource(R.string.sending_attachment, attachment.name)
                } else {
                    attachment.name
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (!attachment.sending) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(chatColors.composerIconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onClearAttachment, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_remove_attachment),
                            tint = chatColors.composerIconFg,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
