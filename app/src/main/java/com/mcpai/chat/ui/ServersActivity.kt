package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val repo = ChatRepository(applicationContext)
            val configs = settings.servers()
            val probes = repo.refreshServers(configs)
            val probeMap = probes.associateBy { serverIdOf(it) }
            adapter.submit(configs, probeMap)
            emptyView.visibility = if (configs.isEmpty()) View.VISIBLE else View.GONE
            repo.close()
        }
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
