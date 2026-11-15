package com.mcpai.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcpai.chat.R
import com.mcpai.chat.ui.theme.chatColors

/**
 * Composer card: voice preview, attachment chip, multiline input with icon
 * controls and a trailing action slot that morphs mic -> send -> stop.
 */
@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    voiceActive: Boolean,
    voiceLevel: Float,
    voiceSeconds: Int,
    attachment: ComposerAttachment?,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onVoiceToggle: () -> Unit,
    onVoiceStop: () -> Unit,
    onVoiceDiscard: () -> Unit = {},
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    /** Editing the last user message: shows an editing strip and a cancel. */
    editingLastMessage: Boolean = false,
    onCancelEdit: () -> Unit = {},
) {
    val canSend = text.isNotBlank() && !sending && !voiceActive
    val haptic = LocalHapticFeedback.current
    var fieldFocused by remember { mutableStateOf(false) }

    // One trailing slot morphs through mic -> send -> stop instead of
    // parking a permanently disabled send button next to the mic.
    val composerAction = when {
        sending -> ComposerAction.StopGenerating
        voiceActive -> ComposerAction.StopVoice
        canSend -> ComposerAction.Send
        else -> ComposerAction.Mic
    }

    // The field owns its selection; the caller owns only the string. When
    // the view-model replaces the text externally (edit pre-fill, suggestion
    // chips, send, cancel) the field reseeds with the cursor at the end.
    // Typing echoes the same text back into the state and never reseeds.
    var fieldValue by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                text,
                androidx.compose.ui.text.TextRange(text.length),
            ),
        )
    }
    androidx.compose.runtime.LaunchedEffect(text) {
        if (text != fieldValue.text) {
            fieldValue = androidx.compose.ui.text.input.TextFieldValue(
                text,
                androidx.compose.ui.text.TextRange(text.length),
            )
        }
    }

    val borderColor by animateValueAsState(
        if (fieldFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        Color.VectorConverter(ColorSpaces.Srgb),
        tween(180),
        label = "composerBorder",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, borderColor, RoundedCornerShape(28.dp)),
    ) {
        Column(modifier = Modifier.animateContentSize(animationSpec = tween(200))) {
            if (editingLastMessage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 9.dp, end = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.editing_message_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                    )
                    IconButton(onClick = onCancelEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            if (voiceActive) {
                RecordingRow(
                    level = voiceLevel,
                    elapsedSeconds = voiceSeconds,
                    onDiscard = onVoiceDiscard,
                    onStop = onVoiceStop,
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
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        onTextChange(it.text)
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (editingLastMessage) {
                                    R.string.hint_edit_message
                                } else {
                                    R.string.hint_message
                                },
                            ),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSend()
                            }
                        },
                    ),
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
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .padding(start = 4.dp),
                )

                composerActionButton(
                    action = composerAction,
                    sending = sending,
                    onSend = onSend,
                    onStop = onStop,
                    onVoiceToggle = onVoiceToggle,
                    onVoiceStop = onVoiceStop,
                    canSend = canSend,
                    haptic = haptic,
                )
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

/** The single trailing action the composer shows right now. */
private enum class ComposerAction {
    Mic,
    Send,
    StopGenerating,
    StopVoice,
}

@Composable
private fun composerActionButton(
    action: ComposerAction,
    sending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onVoiceToggle: () -> Unit,
    onVoiceStop: () -> Unit,
    canSend: Boolean,
    haptic: HapticFeedback,
) {
    val background by animateValueAsState(
        when (action) {
            ComposerAction.Mic -> chatColors.composerIconBg
            ComposerAction.Send -> chatColors.sendBackground
            ComposerAction.StopGenerating -> chatColors.sendBackground
            ComposerAction.StopVoice -> chatColors.voiceBackground
        },
        Color.VectorConverter(ColorSpaces.Srgb),
        tween(180),
        label = "composerActionBg",
    )

    Box(
        modifier = Modifier
            .padding(start = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f, animationSpec = tween(150)) +
                    fadeIn(animationSpec = tween(150)))
                    .togetherWith(
                        scaleOut(targetScale = 0.6f, animationSpec = tween(90)) +
                            fadeOut(animationSpec = tween(90)),
                    )
            },
            label = "composerActionIcon",
        ) { target ->
            IconButton(
                onClick = {
                    when (target) {
                        ComposerAction.Send -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSend()
                        }
                        ComposerAction.StopGenerating -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStop()
                        }
                        ComposerAction.Mic -> onVoiceToggle()
                        ComposerAction.StopVoice -> onVoiceStop()
                    }
                },
                enabled = target != ComposerAction.Send || canSend,
                modifier = Modifier.size(40.dp),
            ) {
                val (icon, description, tint) = when (target) {
                    ComposerAction.Mic -> Triple(
                        Icons.Filled.Mic,
                        stringResource(R.string.action_voice_input),
                        chatColors.composerIconFg,
                    )
                    ComposerAction.Send -> Triple(
                        Icons.Filled.Send,
                        stringResource(R.string.action_send),
                        chatColors.sendForeground,
                    )
                    ComposerAction.StopGenerating -> Triple(
                        Icons.Filled.Stop,
                        stringResource(R.string.action_stop_generating),
                        chatColors.sendForeground,
                    )
                    ComposerAction.StopVoice -> Triple(
                        Icons.Filled.Stop,
                        stringResource(R.string.action_stop_voice_input),
                        chatColors.voiceOn,
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    level: Float,
    elapsedSeconds: Int,
    onDiscard: () -> Unit,
    onStop: () -> Unit,
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
            LevelBars(level = level, color = chatColors.voiceForeground)
            Text(
                text = stringResource(R.string.voice_recording),
                style = MaterialTheme.typography.bodySmall,
                color = chatColors.voiceForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            Text(
                text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                style = MaterialTheme.typography.labelSmall,
                color = chatColors.voiceForeground,
                modifier = Modifier.padding(end = 4.dp),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(chatColors.composerIconBg),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onDiscard, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_discard_recording),
                        tint = chatColors.composerIconFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(chatColors.voiceBackground),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
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

/** Five equalizer bars driven by the live microphone loudness. */
@Composable
private fun LevelBars(level: Float, color: androidx.compose.ui.graphics.Color) {
    val animated = remember { Animatable(0f) }
    val latestLevel by rememberUpdatedState(level)
    LaunchedEffect(Unit) {
        while (true) {
            animated.animateTo(latestLevel, tween(80))
            // Dip between level updates so bursts read as pulses, not flicker.
            animated.animateTo((latestLevel * 0.55f).coerceAtLeast(0.05f), tween(160))
        }
    }
    Canvas(modifier = Modifier.size(width = 26.dp, height = 20.dp)) {
        val factors = listOf(0.45f, 0.8f, 1f, 0.7f, 0.4f)
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val minH = 4.dp.toPx()
        factors.forEachIndexed { index, factor ->
            val barHeight = minH + (size.height - minH) * factor * animated.value
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(index * (barWidth + gap), size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
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
