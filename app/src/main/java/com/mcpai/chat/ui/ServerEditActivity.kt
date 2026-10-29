package com.mcpai.chat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.mcpai.chat.R
import com.mcpai.chat.data.Settings
import com.mcpai.chat.mcp.HttpMcpTransport
import com.mcpai.chat.mcp.McpClient
import com.mcpai.chat.mcp.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Adds or edits one MCP server and can probe it with a real `initialize` +
 * `tools/list` round trip.
 *
 * Note: the layout still exposes a single `authField`; it is mapped to
 * `authHeaderValue` with `authHeaderName` defaulting to `Authorization`.
 * The expanded UI (separate name/value/timeout/trusted fields) comes in T-03.
 */
class ServerEditActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var authField: EditText
    private var existingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_edit)
        settings = Settings.get(applicationContext)

        nameField = findViewById(R.id.nameField)
        urlField = findViewById(R.id.urlField)
        authField = findViewById(R.id.authField)

        existingId = intent.getStringExtra(EXTRA_ID)
        if (existingId != null) {
            settings.servers().firstOrNull { it.id == existingId }?.let { config ->
                nameField.setText(config.name)
                urlField.setText(config.url)
                authField.setText(config.authHeaderValue.orEmpty())
                findViewById<MaterialButton>(R.id.deleteButton).visibility = android.view.View.VISIBLE
            }
        }

        findViewById<MaterialButton>(R.id.saveButton).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { test() }
        findViewById<MaterialButton>(R.id.deleteButton).setOnClickListener {
            existingId?.let { settings.removeServer(it) }
            finish()
        }
    }

    private fun currentConfig(): McpServerConfig = McpServerConfig(
        id = existingId ?: UUID.randomUUID().toString(),
        name = nameField.text.toString().trim().ifEmpty { "MCP server" },
        url = HttpMcpTransport.normaliseUrl(urlField.text.toString().trim()),
        enabled = true,
        authHeaderName = Settings.DEFAULT_AUTH_HEADER_NAME,
        authHeaderValue = authField.text.toString().trim().takeIf { it.isNotEmpty() },
        timeoutSeconds = Settings.DEFAULT_TIMEOUT_SECONDS,
        customHeaders = emptyMap(),
        isTrusted = false,
        spkiPin = null,
    )

    private fun save() {
        val url = urlField.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.label_server_url, Toast.LENGTH_SHORT).show()
            return
        }
        settings.addServer(currentConfig())
        finish()
    }

    private fun test() {
        val config = currentConfig()
        val button = findViewById<MaterialButton>(R.id.testButton)
        button.isEnabled = false
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val client = McpClient(config.transport)
                    val info = client.initialize()
                    val tools = client.listTools()
                    client.close()
                    "${info.name} ${info.version} · ${tools.size} tools"
                }.fold(
                    onSuccess = { it },
                    onFailure = { "Failed: ${it.message}" },
                )
            }
            button.isEnabled = true
            Toast.makeText(this@ServerEditActivity, outcome, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val EXTRA_ID = "server_id"

        fun intent(context: Context, config: McpServerConfig?): Intent =
            Intent(context, ServerEditActivity::class.java).apply {
                config?.let { putExtra(EXTRA_ID, it.id) }
            }
    }
}