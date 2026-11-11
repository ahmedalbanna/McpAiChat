package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mcpai.chat.R
import com.mcpai.chat.ui.theme.chatColors

/** Form state for the server editor. */
data class ServerEditForm(
    val name: String = "",
    val url: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val timeout: String = "30",
    val trusted: Boolean = false,
    val isExisting: Boolean = false,
)

/**
 * Adds or edits one MCP server (Compose): name, URL, optional auth header,
 * timeout and trusted-domain toggle, with a real `initialize` + `tools/list`
 * probe available.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    form: ServerEditForm,
    testInFlight: Boolean,
    onFormChange: (ServerEditForm) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                if (form.isExisting) R.string.title_edit_server else R.string.action_new_server,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.server_edit_subtitle),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
            ) {
                IntroHero()
                ConnectionCard(form, onFormChange)
                AuthenticationCard(form, onFormChange)
                AdvancedCard(form, onFormChange)

                FilledTonalButton(
                    onClick = onTest,
                    enabled = !testInFlight,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(if (testInFlight) R.string.testing_server else R.string.action_test))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                if (form.isExisting) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = chatColors.toolAccent,
                        )
                    }
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
                imageVector = Icons.Filled.Dns,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.server_edit_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.server_edit_intro_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String?,
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
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                )
            }
            content()
        }
    }
}

@Composable
private fun ConnectionCard(form: ServerEditForm, onFormChange: (ServerEditForm) -> Unit) {
    SectionCard(
        title = stringResource(R.string.connection_card_title),
        subtitle = stringResource(R.string.connection_card_subtitle),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(stringResource(R.string.label_server_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.url,
            onValueChange = { onFormChange(form.copy(url = it)) },
            label = { Text(stringResource(R.string.label_server_url)) },
            supportingText = { Text(stringResource(R.string.server_url_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun AuthenticationCard(form: ServerEditForm, onFormChange: (ServerEditForm) -> Unit) {
    var masked by remember { mutableStateOf(true) }

    SectionCard(
        title = "Authentication",
        subtitle = stringResource(R.string.authentication_card_subtitle),
    ) {
        OutlinedTextField(
            value = form.authHeaderName,
            onValueChange = { onFormChange(form.copy(authHeaderName = it)) },
            label = { Text(stringResource(R.string.label_auth_header_name)) },
            singleLine = true,
            placeholder = { Text("Authorization") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.authHeaderValue,
            onValueChange = { onFormChange(form.copy(authHeaderValue = it)) },
            label = { Text(stringResource(R.string.label_auth_header_value)) },
            singleLine = true,
            visualTransformation = if (masked) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            trailingIcon = {
                IconButton(onClick = { masked = !masked }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Lock,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun AdvancedCard(form: ServerEditForm, onFormChange: (ServerEditForm) -> Unit) {
    SectionCard(
        title = stringResource(R.string.advanced_card_title),
        subtitle = stringResource(R.string.advanced_card_subtitle),
    ) {
        OutlinedTextField(
            value = form.timeout,
            onValueChange = { onFormChange(form.copy(timeout = it)) },
            label = { Text(stringResource(R.string.label_timeout_seconds)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_trusted_domain),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.trusted_domain_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = form.trusted,
                onCheckedChange = { onFormChange(form.copy(trusted = it)) },
            )
        }
    }
}
