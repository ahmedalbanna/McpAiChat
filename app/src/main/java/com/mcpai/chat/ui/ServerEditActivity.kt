package com.mcpai.chat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
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
 * Fields: name, URL, auth header name/value, timeout seconds, trusted domain toggle.
 */
class ServerEditActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var authHeaderNameField: EditText
    private lateinit var authHeaderValueField: EditText
    private lateinit var timeoutField: EditText
    private lateinit var trustedSwitch: MaterialSwitch
    private var existingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_edit)
        settings = Settings.get(applicationContext)

        nameField = findViewById(R.id.nameField)
        urlField = findViewById(R.id.urlField)
        authHeaderNameField = findViewById(R.id.authHeaderNameField)
        authHeaderValueField = findViewById(R.id.authHeaderValueField)
        timeoutField = findViewById(R.id.timeoutField)
        trustedSwitch = findViewById(R.id.trustedSwitch)

        existingId = intent.getStringExtra(EXTRA_ID)
        if (existingId != null) {
            settings.servers().firstOrNull { it.id == existingId }?.let { config ->
                nameField.setText(config.name)
                urlField.setText(config.url)
                authHeaderNameField.setText(config.authHeaderName.orEmpty())
                authHeaderValueField.setText(config.authHeaderValue.orEmpty())
                timeoutField.setText(config.timeoutSeconds.toString())
                trustedSwitch.isChecked = config.isTrusted
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
        authHeaderName = authHeaderNameField.text.toString().trim().takeIf { it.isNotEmpty() },
        authHeaderValue = authHeaderValueField.text.toString().trim().takeIf { it.isNotEmpty() },
        timeoutSeconds = timeoutField.text.toString().trim().toIntOrNull() ?: 30,
        customHeaders = emptyMap(),
        isTrusted = trustedSwitch.isChecked,
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