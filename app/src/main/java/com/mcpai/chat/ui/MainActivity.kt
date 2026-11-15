package com.mcpai.chat.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mcpai.chat.BuildConfig
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.Settings
import com.mcpai.chat.data.StoredMessage
import com.mcpai.chat.util.ChatError
import com.mcpai.chat.util.ErrorClassifier
import com.mcpai.chat.ui.theme.McpAiChatTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The conversation screen (Compose).
 *
 * Renders the streaming assistant answer, the grouped MCP tool calls and the
 * status banner, and owns the lifecycle of one conversation turn.
 */
class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<ChatViewModel>()
    private val repo: ChatRepository get() = viewModel.repo

    /** True while the microphone is capturing a voice note. */
    private val voiceListening = mutableStateOf(false)

    /** Live "11 tools" style subtitle for the toolbar. */
    private val toolsSubtitle = mutableStateOf("")

    /** Picks a document from the device (no runtime permissions needed). */
    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val displayName = uri.lastPathSegment?.substringAfterLast('/')?.let(Uri::decode)
                viewModel.selectAttachment(
                    uri.toString(),
                    displayName?.takeUnless { it.isBlank() } ?: uri.toString(),
                )
            }
        }

    /** Requests RECORD_AUDIO permission for voice recording. */
    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording() else Toast.makeText(this, R.string.voice_error_permission, Toast.LENGTH_SHORT).show()
        }

    private var voiceRecorder: VoiceRecorder? = null

    /** Ticks the elapsed-seconds counter while recording. */
    private var voiceTickJob: kotlinx.coroutines.Job? = null

    /** Live microphone loudness (0..1) for the composer's level meter. */
    private val voiceRms = mutableStateOf(0f)

    /** Elapsed recording seconds for the composer's timer. */
    private val voiceSeconds = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Debug builds only: apply the intent-seeded configuration FIRST, so a
        // fresh profile can seed base_url/api_key/mcp_url (used by the device
        // UI tests) before the empty-servers redirect decides where to go.
        // Must run before any settings reads to take effect.
        applyDebugConfigFromIntent()

        // If no MCP server is configured yet, send the user to add one first.
        val settings = Settings.get(applicationContext)
        if (settings.servers().isEmpty()) {
            startActivity(Intent(this, ServersActivity::class.java))
            finish()
            return
        }

        // Open a specific conversation if one was passed via intent extras.
        val intentConversationId = intent.getLongExtra("conversation_id", -1L)

        setContent {
            McpAiChatTheme {
                ChatScreen(
                    viewModel = viewModel,
                    toolsSubtitle = toolsSubtitle.value,
                    voiceActive = voiceListening.value,
                    voiceLevel = voiceRms.value,
                    voiceSeconds = voiceSeconds.value,
                    onOpenServers = { startActivity(Intent(this, ServersActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onNewConversation = viewModel::newConversation,
                    onClearConversation = viewModel::clearConversation,
                    onShareConversation = ::shareConversation,
                    onPickAttachment = { pickFileLauncher.launch(arrayOf("*/*")) },
                    onVoiceToggle = ::toggleVoiceInput,
                    onVoiceStop = ::stopVoiceInput,
                    onVoiceDiscard = ::discardVoiceInput,
                )
            }
        }

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

        // Show how many MCP tools are live, so a silent model is easy to diagnose.
        lifecycleScope.launch {
            combine(repo.tools, repo.serverProbes) { tools, probes -> tools to probes }
                .collect { (tools, probes) ->
                    toolsSubtitle.value = when {
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

    override fun onResume() {
        super.onResume()
        // The conversation this screen shows can be deleted from the list
        // while this activity is behind it (or while the process was dead).
        // Switch to a fresh one instead of writing orphan messages into a
        // deleted conversation.
        val id = repo.currentConversationId()
        if (id != 0L && !repo.conversationExists(id)) {
            viewModel.loadConversation(-1)
        }
    }

    /**
     * Debug-only launcher configuration, so a device can be pointed at a local
     * server and provider without tapping through the settings screens.
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

    private fun toggleVoiceInput() {
        if (voiceListening.value) {
            stopVoiceInput()
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recordAudioLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        startRecording()
    }

    /** Starts a real microphone recording that will be attached as a WAV. */
    private fun startRecording() {
        val recorder = VoiceRecorder(this, lifecycleScope) { level -> voiceRms.value = level }
        if (!recorder.start()) {
            Toast.makeText(this, R.string.voice_error_record, Toast.LENGTH_SHORT).show()
            return
        }
        voiceRecorder = recorder
        voiceRms.value = 0f
        voiceSeconds.value = 0
        voiceListening.value = true
        voiceTickJob?.cancel()
        voiceTickJob = lifecycleScope.launch {
            while (voiceListening.value) {
                kotlinx.coroutines.delay(1000)
                voiceSeconds.value++
            }
        }
    }

    /** Stops the recording and attaches the WAV to the composer. */
    private fun stopVoiceInput() {
        if (!voiceListening.value) return
        voiceListening.value = false
        voiceTickJob?.cancel()
        voiceTickJob = null
        val recorder = voiceRecorder
        voiceRecorder = null
        if (recorder == null) return
        lifecycleScope.launch {
            val recording = kotlinx.coroutines.withContext(
                kotlinx.coroutines.NonCancellable,
            ) { recorder.finish() }
            voiceRms.value = 0f
            if (recording != null) {
                viewModel.selectAttachment(recording.path, recording.name)
            } else {
                Toast.makeText(this@MainActivity, R.string.voice_error_record, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Stops the recording and throws it away. */
    private fun discardVoiceInput() {
        if (!voiceListening.value) return
        voiceListening.value = false
        voiceTickJob?.cancel()
        voiceTickJob = null
        voiceRecorder?.cancel()
        voiceRecorder = null
        voiceRms.value = 0f
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
        // recording that outlives it keeps the mic open in the background.
        voiceRecorder?.cancel()
        voiceRecorder = null
        voiceListening.value = false
        voiceRms.value = 0f
        voiceTickJob?.cancel()
        voiceTickJob = null
        super.onPause()
    }

    override fun onDestroy() {
        // The approval dialog and the turn job are owned by the composer and
        // the view-model respectively; both survive this activity being
        // destroyed. The repository is owned by ChatApplication for the whole
        // process.
        voiceRecorder?.cancel()
        voiceRecorder = null
        super.onDestroy()
    }
}
