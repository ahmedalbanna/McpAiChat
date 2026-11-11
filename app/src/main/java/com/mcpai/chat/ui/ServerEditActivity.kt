package com.mcpai.chat.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
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
import com.mcpai.chat.ui.theme.McpAiChatTheme
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
    private var existingId: String? = null
    private var existingConfig: McpServerConfig? = null
    private val form = mutableStateOf(ServerEditForm())
    private val testInFlight = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings.get(applicationContext)
        existingId = intent.getStringExtra(EXTRA_ID)

        if (existingId != null) {
            settings.servers().firstOrNull { it.id == existingId }?.let { config ->
                existingConfig = config
                form.value = ServerEditForm(
                    name = config.name,
                    url = config.url,
                    authHeaderName = config.authHeaderName.orEmpty(),
                    authHeaderValue = config.authHeaderValue.orEmpty(),
                    timeout = config.timeoutSeconds.toString(),
                    trusted = config.isTrusted,
                    isExisting = true,
                )
            }
        }

        setContent {
            McpAiChatTheme {
                ServerEditScreen(
                    form = form.value,
                    testInFlight = testInFlight.value,
                    onFormChange = { form.value = it },
                    onBack = ::finish,
                    onSave = ::save,
                    onTest = ::test,
                    onDelete = {
                        existingId?.let { settings.removeServer(it) }
                        finish()
                    },
                )
            }
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
        val f = form.value
        val url = HttpMcpTransport.normaliseUrl(f.url.trim())
        return McpServerConfig(
            id = existingId ?: UUID.randomUUID().toString(),
            name = f.name.trim().ifEmpty { "MCP server" },
            url = url,
            enabled = existing?.enabled ?: true,
            authHeaderName = f.authHeaderName.trim().takeIf { it.isNotEmpty() },
            authHeaderValue = f.authHeaderValue.trim().takeIf { it.isNotEmpty() },
            timeoutSeconds = f.timeout.trim().toIntOrNull() ?: 30,
            customHeaders = existing?.customHeaders ?: emptyMap(),
            isTrusted = f.trusted,
            spkiPin = SpkiPinPolicy.pinAfterEdit(existing, url, f.trusted),
        )
    }

    private fun save() {
        val url = form.value.url.trim()
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
        if (testInFlight.value) return
        testInFlight.value = true
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
            testInFlight.value = false
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
