package com.mcpai.chat.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

    /** True while the microphone is streaming to the recognizer. */
    private val voiceListening = mutableStateOf(false)
    private val voiceTranscript = mutableStateOf<String?>("")

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

    /** Requests RECORD_AUDIO permission for voice input. */
    private val recordAudioLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening() else Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If no MCP server is configured yet, send the user to add one first.
        val settings = Settings.get(applicationContext)
        if (settings.servers().isEmpty()) {
            startActivity(Intent(this, ServersActivity::class.java))
            finish()
            return
        }

        // Open a specific conversation if one was passed via intent extras.
        val intentConversationId = intent.getLongExtra("conversation_id", -1L)
        applyDebugConfigFromIntent()

        setContent {
            McpAiChatTheme {
                ChatScreen(
                    viewModel = viewModel,
                    toolsSubtitle = toolsSubtitle.value,
                    voiceActive = voiceListening.value,
                    voiceTranscript = voiceTranscript.value,
                    onOpenServers = { startActivity(Intent(this, ServersActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onNewConversation = viewModel::newConversation,
                    onClearConversation = viewModel::clearConversation,
                    onShareConversation = ::shareConversation,
                    onPickAttachment = { pickFileLauncher.launch(arrayOf("*/*")) },
                    onVoiceToggle = ::toggleVoiceInput,
                    onVoiceStop = ::stopVoiceInput,
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
        startListening()
    }

    /**
     * Starts dictation and keeps it live until the user stops or recognition
     * ends. Recognized speech is handed to the composer as a spoken suffix;
     * the caller pastes it through the IME-side state.
     */
    private fun startListening() {
        speechRecognizer?.cancel()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        voiceListening.value = true
        voiceTranscript.value = getString(R.string.voice_speak_now)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle) {
                voiceTranscript.value = getString(R.string.voice_speak_now)
            }

            override fun onBeginningOfSpeech() {
                voiceTranscript.value = getString(R.string.voice_speak_now)
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}

            override fun onEndOfSpeech() {
                voiceTranscript.value = getString(R.string.voice_finalizing)
            }

            override fun onError(error: Int) {
                finishVoiceInput()
                Toast.makeText(this@MainActivity, "Speech recognition error: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                if (matches.isNotEmpty()) {
                    val spoken = matches[0]
                    // Append to whatever the composer already holds.
                    appendSpokenText(spoken)
                }
                finishVoiceInput()
            }

            override fun onPartialResults(results: Bundle) {
                val partial = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank() && voiceListening.value) {
                    voiceTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceInput() {
        val recognizer = speechRecognizer ?: return
        voiceTranscript.value = getString(R.string.voice_finalizing)
        recognizer.stopListening()
    }

    /** Ends dictation and restores the normal composer state. */
    private fun finishVoiceInput() {
        speechRecognizer?.cancel()
        speechRecognizer = null
        voiceListening.value = false
    }

    /** Appends the spoken suffix to the composer draft. */
    private fun appendSpokenText(spoken: String) {
        val current = viewModel.composerText.value.orEmpty().trim()
        viewModel.setComposerText(if (current.isNotEmpty()) "$current $spoken" else spoken)
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
        speechRecognizer = null
        voiceListening.value = false
        super.onPause()
    }

    override fun onDestroy() {
        // The approval dialog and the turn job are owned by the composer and
        // the view-model respectively; both survive this activity being
        // destroyed. The repository is owned by ChatApplication for the whole
        // process.
        speechRecognizer?.cancel()
        speechRecognizer = null
        super.onDestroy()
    }
}
