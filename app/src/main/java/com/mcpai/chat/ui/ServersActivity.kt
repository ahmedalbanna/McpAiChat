package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.Settings
import com.mcpai.chat.data.SettingsExporter
import com.mcpai.chat.util.ChatError
import com.mcpai.chat.util.ErrorClassifier
import com.mcpai.chat.mcp.HttpMcpTransport
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.mcp.ServerProbe
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages the MCP servers the app talks to and shows the tools each of them
 * advertises.
 */
class ServersActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var adapter: ServerAdapter
    private lateinit var emptyView: TextView

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
        setContentView(R.layout.activity_servers)
        settings = Settings.get(applicationContext)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = ServerAdapter(
            onToggle = { config, enabled ->
                settings.addServer(config.copy(enabled = enabled))
                refresh()
            },
            onClick = { config ->
                settings.activeServerId = config.id
                startActivity(ServerEditActivity.intent(this, config))
            },
        )

        findViewById<RecyclerView>(R.id.serverList).apply {
            layoutManager = LinearLayoutManager(this@ServersActivity)
            adapter = this@ServersActivity.adapter
        }
        emptyView = findViewById(R.id.emptyView)

        findViewById<MaterialButton>(R.id.addButton).setOnClickListener {
            startActivity(ServerEditActivity.intent(this, null))
        }

        findViewById<MaterialButton>(R.id.catalogButton).setOnClickListener {
            catalogLauncher.launch(Intent(this, CatalogActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.exportButton).setOnClickListener {
            exportSettings()
        }

        findViewById<MaterialButton>(R.id.importButton).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain"))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

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
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@launch
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
            val repo = ChatRepository(applicationContext)
            val configs = settings.servers()
            try {
                val probes = repo.refreshServers(configs)
                val probeMap = probes.associateBy { serverIdOf(it) }
                adapter.submit(configs, probeMap)
                emptyView.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE

                // Show specific error dialogs for failed probes (T-07).
                for (probe in probes) {
                    if (probe is ServerProbe.Failed) {
                        val config = configs.firstOrNull { it.id == probe.serverId }
                        val reason = probe.reason
                        if (reason.contains("SPKI pin mismatch", ignoreCase = true)) {
                            showSpkiMismatchDialog(config, reason)
                        } else if (reason.contains("Cannot reach", ignoreCase = true) ||
                            reason.contains("Failed to connect", ignoreCase = true)) {
                            showServerUnavailableDialog(config, reason)
                        }
                    }
                }
            } catch (e: Exception) {
                val error = ErrorClassifier.classify(e)
                when (error) {
                    is ChatError.SpkiMismatch -> showSpkiMismatchDialog(null, error.current)
                    is ChatError.ServerUnavailable -> showServerUnavailableDialog(null, error.message)
                    is ChatError.Unknown -> Toast.makeText(this@ServersActivity, error.message, Toast.LENGTH_LONG).show()
                }
            }
            repo.close()
        }
    }

    private fun showSpkiMismatchDialog(config: McpServerConfig?, reason: String) {
        val serverName = config?.name ?: "Unknown server"
        AlertDialog.Builder(this)
            .setTitle(R.string.error_spki_mismatch_title)
            .setMessage(getString(R.string.error_spki_mismatch_message, serverName))
            .setPositiveButton(R.string.error_spki_retrust) { _, _ ->
                // Re-trust: update the SPKI pin to the current value.
                // The current pin is in the exception; for probe failures we
                // re-extract from the server. For now, mark as untrusted so
                // the user can re-add trust manually.
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
        AlertDialog.Builder(this)
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

/** RecyclerView adapter for the server list. */
class ServerAdapter(
    private val onToggle: (McpServerConfig, Boolean) -> Unit,
    private val onClick: (McpServerConfig) -> Unit,
) : RecyclerView.Adapter<ServerAdapter.Holder>() {

    private var configs: List<McpServerConfig> = emptyList()
    private var probes: Map<String, ServerProbe> = emptyMap()

    fun submit(configs: List<McpServerConfig>, probes: Map<String, ServerProbe>) {
        this.configs = configs
        this.probes = probes
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = configs.size

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val config = configs[position]
        holder.name.text = config.name
        holder.url.text = config.url
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = config.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked -> onToggle(config, checked) }
        holder.itemView.setOnClickListener { onClick(config) }
        holder.trustedBadge.visibility = if (config.isTrusted) View.VISIBLE else View.GONE
        holder.status.text = when (val probe = probes[config.id]) {
            is ServerProbe.Ok -> "Connected · ${probe.toolCount} tools"
            is ServerProbe.Failed -> "Failed: ${probe.reason.take(120)}"
            null -> "Not tested"
        }
    }

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.serverName)
        val url: TextView = view.findViewById(R.id.serverUrl)
        val status: TextView = view.findViewById(R.id.serverStatus)
        val enabled: MaterialSwitch = view.findViewById(R.id.enabledSwitch)
        val trustedBadge: TextView = view.findViewById(R.id.trustedBadge)
    }
}
