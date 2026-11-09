package com.mcpai.chat.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.mcpai.chat.BuildConfig
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatEvent
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.Settings
import com.mcpai.chat.data.StoredMessage
import com.mcpai.chat.util.ChatError
import com.mcpai.chat.util.ErrorClassifier
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The conversation screen.
 *
 * Renders the streaming assistant answer, the MCP tool cards and the status
 * banner, and owns the lifecycle of one conversation turn.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<ChatViewModel>()
    private val repo: ChatRepository get() = viewModel.repo
    private lateinit var adapter: ChatAdapter
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var attachButton: MaterialButton
    private lateinit var micButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var statusBanner: TextView
    private lateinit var emptyView: TextView

    /** URI of the file the user has attached to the next message. */
    private var selectedAttachmentUri: Uri? = null

    /** Picks a document from the device (no runtime permissions needed). */
    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedAttachmentUri = uri
                val name = uri.lastPathSegment ?: uri.toString()
                Toast.makeText(this, "Attached: $name", Toast.LENGTH_SHORT).show()
            }
        }

    /** Requests RECORD_AUDIO permission for voice input. */
    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If no MCP server is configured yet, send the user to add one first.
        val settings = Settings.get(applicationContext)
        if (settings.servers().isEmpty()) {
            startActivity(Intent(this, ServersActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Open a specific conversation if one was passed via intent extras.
        val intentConversationId = intent.getLongExtra("conversation_id", -1L)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_servers -> {
                    startActivity(Intent(this, ServersActivity::class.java)); true
                }

                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }

                R.id.action_new_chat -> {
                    viewModel.newConversation(); true
                }

                R.id.action_clear_chat -> {
                    viewModel.clearConversation(); true
                }

                R.id.action_share_conversation -> {
                    shareConversation(); true
                }

                else -> false
            }
        }

        adapter = ChatAdapter()
        list = findViewById(R.id.messageList)
        list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        list.adapter = adapter

        input = findViewById(R.id.inputField)
        attachButton = findViewById(R.id.attachButton)
        micButton = findViewById(R.id.micButton)
        sendButton = findViewById(R.id.sendButton)
        statusBanner = findViewById(R.id.statusBanner)
        emptyView = findViewById(R.id.emptyView)

        sendButton.setOnClickListener { send() }
        attachButton.setOnClickListener { pickFileLauncher.launch(arrayOf("*/*")) }
        micButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            } else {
                startListening()
            }
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send(); true
            } else {
                false
            }
        }

        applyDebugConfigFromIntent()

        // The view-model owns openConversation() and the history load, so a
        // rotation does not re-open and the rows it already has stay put.
        viewModel.loadConversation(intentConversationId)

        // Warm the MCP tool catalogue in the background so the first message
        // does not pay for the handshake. Servers that are already connected
        // are reused, so revisiting this screen costs nothing.
        runCatching {
            if (repo.settings.servers().any { it.enabled }) {
                lifecycleScope.launch { repo.refreshServers() }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rows.collectLatest { rows ->
                    adapter.submit(rows)
                    emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    if (rows.isNotEmpty()) list.scrollToPosition(rows.size - 1)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.status.collect { message ->
                    if (message == null) {
                        statusBanner.visibility = View.GONE
                    } else {
                        statusBanner.text = message
                        statusBanner.visibility = View.VISIBLE
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.approval.collect { request ->
                    if (request != null) askToolApproval(request)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sending.collect { sending -> sendButton.isEnabled = !sending }
            }
        }

        // Show how many MCP tools are live, so a silent model is easy to diagnose.
        // Both flows are watched: a server exposing no tools never changes the
        // tool list, so watching tools alone could not tell "still connecting"
        // from "connected but empty".
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.tools, repo.serverProbes) { tools, probes -> tools to probes }
                    .collect { (tools, probes) ->
                        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
                        toolbar.subtitle = when {
                            repo.settings.servers().none { it.enabled } ->
                                getString(R.string.no_servers_enabled)

                            tools.isNotEmpty() ->
                                resources.getQuantityString(R.plurals.tools_connected, tools.size, tools.size)

                            probes.isNotEmpty() -> getString(R.string.no_tools_connected)
                            else -> getString(R.string.connecting_servers)
                        }
                    }
            }
        }
    }

    /**
     * Debug-only launcher configuration, so a device can be pointed at a local
     * server and provider without tapping through the settings screens.
     *
     * ```
     * adb shell am start -n com.mcpai.chat/.ui.MainActivity \
     *   --es api_key sk-... --es base_url http://192.168.8.21:8098/v1 \
     *   --es model mock-model --es mcp_url http://192.168.8.21:8099/mcp
     * ```
     */
    private fun applyDebugConfigFromIntent() {
        if (!BuildConfig.DEBUG) return
        val extras = intent.extras ?: return
        val settings = repo.settings

        extras.getString("api_key")?.let { settings.openAiApiKey = it }
        extras.getString("base_url")?.let { settings.openAiBaseUrl = it }
        extras.getString("model")?.let { settings.model = it }
        extras.getString("system_prompt")?.let { settings.systemPrompt = it }
        extras.getString("mcp_url")?.let { url ->
            if (url.isNotBlank()) {
                settings.addServer(
                    com.mcpai.chat.mcp.McpServerConfig(
                        id = "debug-server",
                        name = "debug server",
                        url = url,
                    ),
                )
            }
        }
    }

    private fun send() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        input.setText("")
        emptyView.visibility = View.GONE

        val attachmentPath = selectedAttachmentUri?.toString()
        val attachmentName = selectedAttachmentUri?.lastPathSegment
        selectedAttachmentUri = null

        viewModel.send(text, attachmentPath, attachmentName)
    }

    /** The one active approval dialog, so a second request cannot stack on top. */
    private var approvalDialog: AlertDialog? = null

    /**
     * Asks the user whether the model's tool call may run.
     *
     * The turn is suspended on the repository side until the user answers, so
     * this is the single point where a third-party MCP server gets executed.
     */
    private fun askToolApproval(request: ChatEvent.ToolApprovalRequested) {
        if (approvalDialog?.isShowing == true) return

        statusBanner.text = getString(R.string.tool_approval_title)
        statusBanner.visibility = View.VISIBLE

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val trustBox = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
            text = getString(R.string.tool_approval_trust, request.serverName)
            isChecked = false
        }
        val autoApproveBox = com.google.android.material.checkbox.MaterialCheckBox(this).apply {
            text = getString(R.string.label_auto_approve)
            isChecked = false
        }
        container.addView(trustBox)
        container.addView(autoApproveBox)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.tool_approval_title)
            .setMessage(
                getString(
                    R.string.tool_approval_message,
                    request.name,
                    request.serverName,
                    prettyArgs(request.args),
                ),
            )
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(R.string.tool_approval_allow) { _, _ ->
                viewModel.approve(request, approved = true, trust = trustBox.isChecked, autoApprove = autoApproveBox.isChecked)
            }
            .setNegativeButton(R.string.tool_approval_deny) { _, _ ->
                viewModel.approve(request, approved = false, trust = false, autoApprove = false)
            }
            .setOnCancelListener {
                // Dismissing the dialog is a denial, never an approval.
                viewModel.approve(request, approved = false, trust = false, autoApprove = false)
            }
            .create()

        approvalDialog = dialog
        dialog.show()
    }

    private fun prettyArgs(raw: String): String = runCatching {
        org.json.JSONObject(raw).toString(2)
    }.getOrDefault(raw)

    /** Shows a specific error dialog based on the exception type (T-07). */
    private fun showErrorDialog(exception: Throwable, serverName: String) {
        val error = ErrorClassifier.classify(exception, serverName)
        when (error) {
            is ChatError.SpkiMismatch -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.error_spki_mismatch_title)
                    .setMessage(getString(R.string.error_spki_mismatch_message, serverName.ifBlank { "Server" }))
                    .setPositiveButton(R.string.error_spki_retrust) { _, _ ->
                        // Re-trust: mark as untrusted so user can re-add trust.
                        // The actual re-extraction happens on next connection attempt.
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
            is ChatError.ServerUnavailable -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.error_server_unavailable_title)
                    .setMessage(getString(R.string.error_server_unavailable_message, serverName.ifBlank { "Server" }, error.message))
                    .setPositiveButton(R.string.error_retry) { _, _ ->
                        lifecycleScope.launch {
                            try { repo.refreshServers() } catch (_: Exception) {}
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
            is ChatError.Unknown -> {
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Starts speech recognition and inserts results into the input field. */
    private fun startListening() {
        speechRecognizer?.cancel()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {
                micButton.isEnabled = false
                micButton.alpha = 0.5f
                statusBanner.text = "Listening…"
                statusBanner.visibility = View.VISIBLE
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {
                micButton.isEnabled = true
                micButton.alpha = 1f
                statusBanner.visibility = View.GONE
            }
            override fun onError(error: Int) {
                micButton.isEnabled = true
                micButton.alpha = 1f
                statusBanner.visibility = View.GONE
                speechRecognizer?.cancel()
                speechRecognizer = null
                Toast.makeText(this@MainActivity, "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                if (matches.isNotEmpty()) {
                    val spoken = matches[0]
                    val current = input.text.toString()
                    input.setText(if (current.isNotEmpty()) "$current $spoken" else spoken)
                    input.setSelection(input.text.length)
                }
                micButton.isEnabled = true
                micButton.alpha = 1f
                statusBanner.visibility = View.GONE
                speechRecognizer?.cancel()
                speechRecognizer = null
            }
            override fun onPartialResults(results: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    /** Exports the current conversation as Markdown and shares it via the Android share sheet. */
    private fun shareConversation() {
        lifecycleScope.launch {
            val messages = repo.loadHistory()
            val title = repo.allConversations()
                .firstOrNull { it.id == repo.currentConversationId() }
                ?.title
                ?.takeIf { it.isNotBlank() } ?: "Conversation"

            val sb = StringBuilder()
            sb.append("# ").append(title).append("\n\n")
            sb.append("Exported from MCP AI Chat on ")
            sb.append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())).append("\n\n")

            for (msg in messages) {
                when (msg.role) {
                    StoredMessage.ROLE_USER -> {
                        sb.append("**User:** ").append(msg.content).append("\n\n")
                        if (msg.attachmentName?.isNotBlank() == true) {
                            sb.append("*Attachment: *").append(msg.attachmentName).append("*\n\n")
                        }
                    }
                    StoredMessage.ROLE_ASSISTANT -> {
                        sb.append("**Assistant:** ").append(msg.content).append("\n\n")
                        if (msg.reasoning?.isNotBlank() == true) {
                            sb.append("*Reasoning:* ").append(msg.reasoning).append("\n\n")
                        }
                    }
                    StoredMessage.ROLE_TOOL -> {
                        sb.append("**Tool:** ").append(msg.toolName ?: "unknown").append("\n")
                        sb.append("Arguments: ").append(msg.toolArgs ?: "{}").append("\n")
                        sb.append("Result: ").append(msg.toolResult ?: "").append("\n\n")
                    }
                }
            }

            val markdown = sb.toString()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
            startActivity(Intent.createChooser(intent, "Share conversation"))
        }
    }

    override fun onPause() {
        // Stop the microphone as soon as the user leaves the screen; a
        // recognition session that outlives it keeps the recognizer alive and
        // announces a banner the user cannot see.
        speechRecognizer?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        approvalDialog?.dismiss()
        approvalDialog = null
        speechRecognizer?.cancel()
        speechRecognizer = null
        // The turn job lives in ChatViewModel, so it survives this activity
        // being destroyed. The repository is owned by ChatApplication for the
        // whole process; closing it here would close the database under the
        // coroutine that is writing the partial answer, and would discard every
        // MCP session.
        super.onDestroy()
    }
}
