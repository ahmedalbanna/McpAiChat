package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.mcpai.chat.R
import com.mcpai.chat.ui.theme.chatColors

/** Form state for the provider settings editor. */
data class ProviderForm(
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val toolsEnabled: Boolean = true,
    val confirmToolCalls: Boolean = true,
    val historyLimit: Int = 40,
)

/**
 * AI provider settings (Compose): key, base URL, model picker with refresh,
 * system prompt, temperature, MCP tool toggles, context window and save.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    form: ProviderForm,
    models: List<String>,
    loadingModels: Boolean,
    onFormChange: (ProviderForm) -> Unit,
    onRefreshModels: () -> Unit,
    onOpenToolDetail: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    var modelsExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.action_settings), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_toolbar_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IntroHero()

                SectionCard(
                    icon = Icons.Filled.Tune,
                    title = "Provider",
                    subtitle = stringResource(R.string.provider_card_subtitle),
                ) {
                    OutlinedTextField(
                        value = form.apiKey,
                        onValueChange = { onFormChange(form.copy(apiKey = it)) },
                        label = { Text(stringResource(R.string.label_api_key)) },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) {
                                        Icons.Filled.VisibilityOff
                                    } else {
                                        Icons.Filled.Visibility
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.baseUrl,
                        onValueChange = { onFormChange(form.copy(baseUrl = it)) },
                        label = { Text(stringResource(R.string.label_base_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = modelsExpanded,
                            onExpandedChange = { modelsExpanded = !modelsExpanded },
                            modifier = Modifier.weight(1f),
                        ) {
                            OutlinedTextField(
                                value = form.model,
                                onValueChange = { onFormChange(form.copy(model = it)) },
                                readOnly = true,
                                label = { Text(stringResource(R.string.label_model)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelsExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                            )
                            ExposedDropdownMenu(
                                expanded = modelsExpanded,
                                onDismissRequest = { modelsExpanded = false },
                            ) {
                                models.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            onFormChange(form.copy(model = model))
                                            modelsExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = onRefreshModels,
                            enabled = !loadingModels,
                            modifier = Modifier.padding(start = 8.dp).height(56.dp),
                        ) {
                            if (loadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                SectionCard(
                    icon = Icons.Filled.ChatBubble,
                    title = stringResource(R.string.conversation_card_title),
                    subtitle = stringResource(R.string.conversation_card_subtitle),
                ) {
                    OutlinedTextField(
                        value = form.systemPrompt,
                        onValueChange = { onFormChange(form.copy(systemPrompt = it)) },
                        label = { Text(stringResource(R.string.label_system_prompt)) },
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.label_temperature),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Slider(
                        value = form.temperature,
                        onValueChange = { onFormChange(form.copy(temperature = it)) },
                        valueRange = 0f..2f,
                        steps = 19,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                SectionCard(
                    icon = Icons.Filled.Build,
                    title = "Tools",
                    subtitle = stringResource(R.string.tools_card_subtitle),
                ) {
                    SwitchRow(
                        label = stringResource(R.string.label_enabled_tools),
                        checked = form.toolsEnabled,
                        onCheckedChange = { onFormChange(form.copy(toolsEnabled = it)) },
                    )
                    SwitchRow(
                        label = stringResource(R.string.label_confirm_tools),
                        hint = stringResource(R.string.label_confirm_tools_hint),
                        checked = form.confirmToolCalls,
                        onCheckedChange = { onFormChange(form.copy(confirmToolCalls = it)) },
                    )
                    FilledTonalButton(
                        onClick = onOpenToolDetail,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
                    ) {
                        Text(stringResource(R.string.action_manage_tools))
                    }
                }

                SectionCard(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.memory_card_title),
                    subtitle = stringResource(R.string.memory_card_subtitle),
                ) {
                    Text(
                        text = stringResource(R.string.label_history, form.historyLimit),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = form.historyLimit.toFloat(),
                        onValueChange = { onFormChange(form.copy(historyLimit = it.toInt())) },
                        valueRange = 4f..120f,
                        steps = 28,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.label_history_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 6.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_save))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun IntroHero() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.settings_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.settings_intro_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp),
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(text = title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
