package com.mcpai.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcpai.chat.R
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.mcp.ServerProbe
import com.mcpai.chat.ui.theme.chatColors

/** One server row plus its probe outcome. */
data class ServerRow(
    val config: McpServerConfig,
    val probe: ServerProbe?,
)

/**
 * Manages the MCP servers the app talks to and shows the tools each of them
 * advertises (Compose).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    rows: List<ServerRow>,
    loading: Boolean,
    onToggle: (McpServerConfig, Boolean) -> Unit,
    onOpenServer: (McpServerConfig) -> Unit,
    onAddServer: () -> Unit,
    onOpenCatalog: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        // Manual layout like the chat screen: the M3 TopAppBar consumes the
        // status bar itself and the bottom bar is flush — no insets to dodge.
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.title_servers),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.servers_toolbar_subtitle),
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

            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    IntroHero(rows = rows, loading = loading)

                    if (rows.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(rows, key = { it.config.id }) { row ->
                                ServerCard(row = row, onToggle = onToggle, onOpenServer = onOpenServer)
                            }
                        }
                    }
                }
            }

            BottomActionBar(
                onAddServer = onAddServer,
                onOpenCatalog = onOpenCatalog,
                onExport = onExport,
                onImport = onImport,
            )
        }
    }
}

/**
 * Bottom action bar: Add server (tonal, full width), Catalog, export, import.
 */
@Composable
private fun BottomActionBar(
    onAddServer: () -> Unit,
    onOpenCatalog: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        FilledTonalButton(
            onClick = onAddServer,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_add_server))
        }
        FilledTonalButton(
            onClick = onOpenCatalog,
            modifier = Modifier
                .padding(start = 8.dp)
                .height(48.dp),
        ) {
            Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.title_free_servers))
        }
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onExport),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Upload,
                contentDescription = stringResource(R.string.action_export),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onImport),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = stringResource(R.string.action_import),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntroHero(rows: List<ServerRow>, loading: Boolean) {
    val configs = rows.map { it.config }
    val enabled = configs.count { it.enabled }
    val toolCount = rows.mapNotNull { it.probe as? ServerProbe.Ok }.sumOf { it.toolCount }
    val failures = rows.count { it.probe is ServerProbe.Failed }
    val summary = when {
        configs.isEmpty() -> null
        failures == 0 -> stringResource(R.string.servers_summary, configs.size, enabled, toolCount)
        else -> stringResource(R.string.servers_summary_failures, configs.size, enabled, toolCount, failures)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.servers_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.servers_intro_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 46.dp, top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerCard(
    row: ServerRow,
    onToggle: (McpServerConfig, Boolean) -> Unit,
    onOpenServer: (McpServerConfig) -> Unit,
) {
    val config = row.config

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp),
            )
            .clickable { onOpenServer(config) },
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(chatColors.composerIconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (config.isTrusted) {
                            Surface(
                                color = chatColors.toolOk.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Verified,
                                        contentDescription = stringResource(R.string.badge_trusted),
                                        modifier = Modifier.size(12.dp),
                                        tint = chatColors.toolOk,
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_trusted),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chatColors.toolOk,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = config.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onToggle(config, it) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            StatusRow(probe = row.probe)
        }
    }
}

@Composable
private fun StatusRow(probe: ServerProbe?) {
    val (dotColor, label) = when (probe) {
        is ServerProbe.Ok -> chatColors.toolOk to stringResource(R.string.status_connected, probe.toolCount)
        is ServerProbe.Failed -> chatColors.toolAccent to "${stringResource(R.string.status_failed)}: ${probe.reason.take(72)}"
        null -> chatColors.statusIdle to stringResource(R.string.status_untested)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 56.dp, top = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Dns,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.empty_servers),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
