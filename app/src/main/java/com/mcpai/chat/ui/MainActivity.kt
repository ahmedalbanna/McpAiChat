package com.mcpai.chat.ui

import android.app.Activity
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The conversation screen.
 *
 * Renders the streaming assistant answer, the MCP tool cards and the status
 * banner, and owns the lifecycle of one conversation turn.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repo: ChatRepository
    private lateinit var adapter: ChatAdapter
    private lateinit var list: RecyclerView
    private lateinit var input: EditText
    private lateinit var attachButton: MaterialButton
    private lateinit var micButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var statusBanner: TextView
    private lateinit var emptyView: TextView

    private var turnJob: Job? = null

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

        repo = ChatRepository(applicationContext)

        // Open a specific conversation if one was passed via intent extras.
        val intentConversationId = intent.getLongExtra("conversation_id", -1L)
        if (intentConversationId > 0) {
            repo.setConversationId(intentConversationId)
        }

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
                    repo.newConversation(); reload(); true
                }

                R.id.action_clear_chat -> {
                    repo.clearConversation(); reload(); true
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

        lifecycleScope.launch {
            if (intentConversationId > 0) {
                // conversation_id was set via setConversationId above; just load it.
                reload()
            } else {
                repo.openConversation()
                reload()
            }
            repo.refreshServers()
        }
        // Show how many MCP tools are live, so a silent model is easy to diagnose.
        lifecycleScope.launch {
            repo.tools.collect { tools ->
                val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
                toolbar.subtitle = if (tools.isEmpty()) {
                    getString(R.string.no_tools_connected)
                } else {
                    resources.getQuantityString(R.plurals.tools_connected, tools.size, tools.size)
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

    private fun reload() {
        lifecycleScope.launch {
            val rows = repo.loadHistory().map { it.toRow() }
            adapter.submit(rows)
            emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            if (rows.isNotEmpty()) list.scrollToPosition(rows.size - 1)
        }
    }

    private fun StoredMessage.toRow(): ChatRow = when (role) {
        StoredMessage.ROLE_USER -> ChatRow.User(
            id = id,
            text = content,
            attachmentPath = attachmentPath,
            attachmentName = attachmentName,
        )
        StoredMessage.ROLE_TOOL -> ChatRow.Tool(
            id = id,
            name = toolName.orEmpty(),
            args = toolArgs.orEmpty(),
            result = toolResult,
            isError = toolError,
            running = status == StoredMessage.STATUS_PENDING,
            declined = status == StoredMessage.STATUS_DECLINED,
        )

        else -> ChatRow.Assistant(
            id = id,
            text = content,
            streaming = status == StoredMessage.STATUS_STREAMING,
            reasoning = reasoning.orEmpty(),
        )
    }

    private fun send() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        if (turnJob?.isActive == true) return

        input.setText("")
        emptyView.visibility = View.GONE
        sendButton.isEnabled = false

        val attachmentPath = selectedAttachmentUri?.toString()
        val attachmentName = selectedAttachmentUri?.lastPathSegment
        selectedAttachmentUri = null

        turnJob = lifecycleScope.launch {
            repo.send(text, attachmentPath, attachmentName).collect { event ->
                when (event) {
                    is ChatEvent.UserMessage -> {
                        adapter.add(ChatRow.User(event.id, event.text))
                        list.scrollToPosition(adapter.itemCount - 1)
                    }

                    is ChatEvent.AssistantStarted -> {
                        adapter.add(ChatRow.Assistant(event.id, "", true))
                    }

                    is ChatEvent.AssistantDelta -> {
                        adapter.appendAssistantText(event.id, event.delta)
                        list.scrollToPosition(adapter.itemCount - 1)
                    }

                    is ChatEvent.ReasoningDelta -> {
                        adapter.appendReasoning(event.id, event.delta)
                    }

                    is ChatEvent.AssistantDone -> {
                        adapter.markAssistantFinished(event.id)
                    }

                    is ChatEvent.ToolStarted -> {
                        adapter.add(ChatRow.Tool(event.id, event.name, event.args, null, false, true))
                        list.scrollToPosition(adapter.itemCount - 1)
                    }

                    is ChatEvent.ToolFinished -> {
                        adapter.completeTool(event.id, event.result, event.isError, event.declined)
                    }

                    is ChatEvent.ToolApprovalRequested -> askToolApproval(event)

                    is ChatEvent.Status -> {
                        statusBanner.text = event.message
                        statusBanner.visibility = View.VISIBLE
                    }

                    is ChatEvent.Error -> {
                        statusBanner.text = event.message
                        statusBanner.visibility = View.VISIBLE
                    }
                }
            }
            statusBanner.visibility = View.GONE
            sendButton.isEnabled = true
        }
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
        container.addView(trustBox)

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
                repo.approveToolCall(approved = true, trustServer = trustBox.isChecked)
            }
            .setNegativeButton(R.string.tool_approval_deny) { _, _ ->
                repo.approveToolCall(approved = false, trustServer = false)
            }
            .setOnCancelListener {
                // Dismissing the dialog is a denial, never an approval.
                repo.approveToolCall(approved = false, trustServer = false)
            }
            .create()

        approvalDialog = dialog
        dialog.show()
    }

    private fun prettyArgs(raw: String): String = runCatching {
        org.json.JSONObject(raw).toString(2)
    }.getOrDefault(raw)

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

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.cancel()
        speechRecognizer = null
        repo.close()
    }
}
