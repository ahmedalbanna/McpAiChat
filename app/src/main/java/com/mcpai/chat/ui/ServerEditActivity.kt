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
import com.mcpai.chat.mcp.ServerUrlProblem
import com.mcpai.chat.mcp.ServerUrlValidator
import com.mcpai.chat.mcp.SpkiPinPolicy
import com.mcpai.chat.util.ChatError
import com.mcpai.chat.util.ErrorClassifier
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
    private var existingConfig: McpServerConfig? = null

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
                existingConfig = config
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

    /**
     * The configuration as the form currently describes it.
     *
     * Fields the form does not show — the SPKI pin, custom headers, the
     * enabled flag — are carried over from the stored configuration, so
     * editing a server never silently drops them. The pin is kept only while
     * the server stays trusted and points at the same host.
     */
    private fun currentConfig(): McpServerConfig {
        val existing = existingConfig
        val url = HttpMcpTransport.normaliseUrl(urlField.text.toString().trim())
        val isTrusted = trustedSwitch.isChecked
        return McpServerConfig(
            id = existingId ?: UUID.randomUUID().toString(),
            name = nameField.text.toString().trim().ifEmpty { "MCP server" },
            url = url,
            enabled = existing?.enabled ?: true,
            authHeaderName = authHeaderNameField.text.toString().trim().takeIf { it.isNotEmpty() },
            authHeaderValue = authHeaderValueField.text.toString().trim().takeIf { it.isNotEmpty() },
            timeoutSeconds = timeoutField.text.toString().trim().toIntOrNull() ?: 30,
            customHeaders = existing?.customHeaders ?: emptyMap(),
            isTrusted = isTrusted,
            spkiPin = SpkiPinPolicy.pinAfterEdit(existing, url, isTrusted),
        )
    }

    private fun save() {
        val url = urlField.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, R.string.label_server_url, Toast.LENGTH_SHORT).show()
            return
        }
        val problem = validateUrl(url)
        if (problem != null) {
            Toast.makeText(this, problem, Toast.LENGTH_LONG).show()
            return
        }
        settings.addServer(currentConfig())
        finish()
    }

    /**
     * Returns a user-facing problem with [rawUrl], or null when the URL is
     * acceptable. HTTPS is required; plain HTTP is only allowed for loopback
     * addresses, where local development servers live.
     */
    private fun validateUrl(rawUrl: String): String? =
        when (ServerUrlValidator.validate(rawUrl)) {
            null -> null
            ServerUrlProblem.NOT_HTTP -> getString(R.string.error_url_scheme, rawUrl)
            ServerUrlProblem.MISSING_HOST -> getString(R.string.error_url_scheme, rawUrl)
            ServerUrlProblem.HTTPS_REQUIRED -> getString(
                R.string.error_url_https_required,
                rawUrl.substringAfter("://").substringBefore('/').substringBefore(':'),
            )
        }

    private fun test() {
        val config = currentConfig()
        val button = findViewById<MaterialButton>(R.id.testButton)
        button.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val client = McpClient(config.transport)
                    val info = client.initialize()
                    val tools = client.listTools()
                    client.close()
                    "${info.name} ${info.version} · ${tools.size} tools"
                }
            }
            button.isEnabled = true
            result.fold(
                onSuccess = { outcome ->
                    Toast.makeText(this@ServerEditActivity, outcome, Toast.LENGTH_LONG).show()
                },
                onFailure = { e ->
                    val error = ErrorClassifier.classify(e, config.name)
                    when (error) {
                        is ChatError.SpkiMismatch -> {
                            androidx.appcompat.app.AlertDialog.Builder(this@ServerEditActivity)
                                .setTitle(R.string.error_spki_mismatch_title)
                                .setMessage(getString(R.string.error_spki_mismatch_message, config.name))
                                .setPositiveButton(R.string.error_spki_retrust) { _, _ ->
                                    settings.addServer(config.copy(isTrusted = false, spkiPin = null))
                                }
                                .setNegativeButton(R.string.action_cancel, null)
                                .show()
                        }
                        is ChatError.ServerUnavailable -> {
                            androidx.appcompat.app.AlertDialog.Builder(this@ServerEditActivity)
                                .setTitle(R.string.error_server_unavailable_title)
                                .setMessage(getString(R.string.error_server_unavailable_message, config.name, error.message))
                                .setPositiveButton(R.string.error_retry) { _, _ -> test() }
                                .setNegativeButton(R.string.action_cancel, null)
                                .show()
                        }
                        else -> {
                            Toast.makeText(this@ServerEditActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
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