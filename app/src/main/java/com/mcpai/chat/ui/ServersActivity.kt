package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.Settings
import com.mcpai.chat.data.SettingsExporter
import com.mcpai.chat.util.ChatError
import com.mcpai.chat.util.ErrorClassifier
import com.mcpai.chat.mcp.HttpMcpTransport
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.mcp.ServerProbe
import com.mcpai.chat.ui.theme.McpAiChatTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages the MCP servers the app talks to and shows the tools each of them
 * advertises (Compose).
 */
class ServersActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private val state = mutableStateOf(ServerRows(emptyList(), loading = true))

    /**
     * Receives a server picked from the free-server catalogue and saves it.
     *
     * The catalogue hands back the URL instead of writing the settings itself:
     * finishing during a click would leak the touch to the activity underneath.
     */
    private val catalogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val url = data.getStringExtra(CatalogActivity.EXTRA_URL).orEmpty()
            if (url.isBlank()) return@registerForActivityResult
            settings.addServer(
                McpServerConfig(
                    id = UUID.randomUUID().toString(),
                    name = data.getStringExtra(CatalogActivity.EXTRA_NAME)
                        ?.takeIf { it.isNotBlank() }
                        ?: url.substringAfterLast('/').ifEmpty { "MCP server" },
                    url = HttpMcpTransport.normaliseUrl(url),
                ),
            )
            refresh()
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            importSettings(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings.get(applicationContext)
        setContent {
            McpAiChatTheme {
                ServersScreen(
                    rows = state.value.rows,
                    loading = state.value.loading,
                    onToggle = { config, enabled ->
                        settings.addServer(config.copy(enabled = enabled))
                        refresh()
                    },
                    onOpenServer = { config ->
                        settings.activeServerId = config.id
                        startActivity(ServerEditActivity.intent(this, config))
                    },
                    onAddServer = {
                        startActivity(ServerEditActivity.intent(this, null))
                    },
                    onOpenCatalog = {
                        catalogLauncher.launch(Intent(this, CatalogActivity::class.java))
                    },
                    onExport = ::exportSettings,
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private data class ServerRows(val rows: List<ServerRow>, val loading: Boolean)

    private fun exportSettings() {
        val exporter = SettingsExporter()
        val json = exporter.export(settings.servers(), includeSensitive = false)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_SUBJECT, "MCP AI Chat server settings")
        }
        startActivity(Intent.createChooser(intent, "Export server settings"))
    }

    private fun importSettings(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: return@launch
                val exporter = SettingsExporter()
                val imported = exporter.import(json)
                for (config in imported) {
                    settings.addServer(config)
                }
                refresh()
                Toast.makeText(this@ServersActivity, "Imported ${imported.size} server(s)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ServersActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val repo = ChatRepository.get(applicationContext)
            val configs = settings.servers()
            try {
                val probes = repo.refreshServers(configs)
                val probeMap = probes.associateBy { serverIdOf(it) }
                state.value = ServerRows(
                    rows = configs.map { ServerRow(it, probeMap[it.id]) },
                    loading = false,
                )

                // Show specific error dialogs for failed probes (T-07).
                for (probe in probes) {
                    if (probe is ServerProbe.Failed) {
                        val config = configs.firstOrNull { it.id == probe.serverId }
                        val reason = probe.reason
                        if (reason.contains("SPKI pin mismatch", ignoreCase = true)) {
                            showSpkiMismatchDialog(config, reason)
                        } else if (
                            reason.contains("Cannot reach", ignoreCase = true) ||
                            reason.contains("Failed to connect", ignoreCase = true)
                        ) {
                            showServerUnavailableDialog(config, reason)
                        }
                    }
                }
            } catch (e: Exception) {
                state.value = ServerRows(
                    rows = configs.map { ServerRow(it, null) },
                    loading = false,
                )
                val error = ErrorClassifier.classify(e)
                when (error) {
                    is ChatError.SpkiMismatch -> showSpkiMismatchDialog(null, error.current)
                    is ChatError.ServerUnavailable -> showServerUnavailableDialog(null, error.message)
                    is ChatError.Unknown -> Toast.makeText(this@ServersActivity, error.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSpkiMismatchDialog(config: McpServerConfig?, reason: String) {
        val serverName = config?.name ?: "Unknown server"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.error_spki_mismatch_title)
            .setMessage(getString(R.string.error_spki_mismatch_message, serverName))
            .setPositiveButton(R.string.error_spki_retrust) { _, _ ->
                // Re-trust: mark the server as no longer trusted so the user
                // can re-add the current pin on the next connection.
                config?.let {
                    settings.addServer(it.copy(isTrusted = false, spkiPin = null))
                    refresh()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showServerUnavailableDialog(config: McpServerConfig?, reason: String) {
        val serverName = config?.name ?: "Unknown server"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.error_server_unavailable_title)
            .setMessage(getString(R.string.error_server_unavailable_message, serverName, reason))
            .setPositiveButton(R.string.error_retry) { _, _ -> refresh() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun serverIdOf(probe: ServerProbe): String = when (probe) {
        is ServerProbe.Ok -> probe.serverId
        is ServerProbe.Failed -> probe.serverId
    }
}
