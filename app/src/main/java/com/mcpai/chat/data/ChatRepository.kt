package com.mcpai.chat.data

import com.mcpai.chat.util.text
import android.content.Context
import com.mcpai.chat.engine.ChatEngine
import com.mcpai.chat.llm.ChatMessage
import com.mcpai.chat.llm.OpenAiProvider
import com.mcpai.chat.mcp.McpRegistry
import com.mcpai.chat.mcp.McpServerConfig
import com.mcpai.chat.mcp.McpTool
import com.mcpai.chat.mcp.ServerProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** UI-facing events produced by a conversation turn. */
sealed interface ChatEvent {
    data class UserMessage(
        val id: Long,
        val text: String,
        val attachmentPath: String? = null,
        val attachmentName: String? = null,
    ) : ChatEvent
    data class AssistantStarted(val id: Long) : ChatEvent
    data class AssistantDelta(val id: Long, val delta: String) : ChatEvent
    data class ReasoningDelta(val id: Long, val delta: String) : ChatEvent
    data class AssistantDone(val id: Long, val text: String) : ChatEvent
    data class ToolStarted(val id: Long, val name: String, val args: String) : ChatEvent
    data class ToolFinished(
        val id: Long,
        val name: String,
        val args: String,
        val result: String,
        val isError: Boolean,
        val declined: Boolean = false,
    ) : ChatEvent

    /** The model asked to run a tool; the UI must answer with [approveToolCall]. */
    data class ToolApprovalRequested(
        val id: Long,
        val name: String,
        val args: String,
        val serverName: String,
    ) : ChatEvent

    data class Status(val message: String) : ChatEvent
    data class Error(val message: String) : ChatEvent
}

/**
 * Application-level facade: owns the database, the settings, the live MCP
 * connections and the chat engine, and turns everything into a stream of
 * [ChatEvent]s the UI can render.
 */
class ChatRepository(context: Context) {

    val settings: Settings = Settings.get(context)
    private val db = ChatDatabase(context)
    private val registry = McpRegistry()
    val toolApprovals: ToolApprovalStore = SqliteToolApprovalStore(db)

    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    private val _serverProbes = MutableStateFlow<List<ServerProbe>>(emptyList())
    val serverProbes: StateFlow<List<ServerProbe>> = _serverProbes.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    /**
     * The user's answer to the one pending tool-approval request.
     *
     * A single channel is enough because the engine waits for an answer before
     * requesting the next one.
     */
    private val approvalAnswers = Channel<ApprovalDecision>()

    /**
     * Servers the user trusted for this session, so a trusted server is not
     * re-confirmed on every tool call.
     *
     * Deliberately in memory: trust should not survive a restart, and the user
     * should see the confirmation again after coming back to the app.
     */
    private val sessionTrustedServers = mutableSetOf<String>()

    private var conversationId: Long = 0

    /** A user decision on a tool-approval request. */
    data class ApprovalDecision(
        val approved: Boolean,
        val trustServer: Boolean,
        val autoApproveTool: Boolean = false,
    )

    val engine = ChatEngine(registry) {
        if (!settings.hasApiKey()) null
        else OpenAiProvider(
            baseUrl = settings.openAiBaseUrl,
            apiKey = settings.openAiApiKey,
            model = settings.model,
        )
    }

    /** Loads (or creates) the active conversation. */
    suspend fun openConversation(): Long = withContext(Dispatchers.IO) {
        conversationId = db.latestConversationId() ?: db.createConversation()
        conversationId
    }

    fun currentConversationId(): Long = conversationId

    /** Switches to an existing conversation. */
    fun setConversationId(id: Long) {
        conversationId = id
    }

    fun allConversations(): List<ChatDatabase.ConversationSummary> = db.allConversations()

    /** Reconnects every enabled MCP server and refreshes the tool catalogue. */
    suspend fun refreshServers(configs: List<McpServerConfig> = settings.servers()): List<ServerProbe> =
        withContext(Dispatchers.IO) {
            val (routed, probes) = registry.connect(configs)
            _tools.value = routed.map { it.tool }
            _serverProbes.value = probes
            probes
        }

    suspend fun loadHistory(): List<StoredMessage> = withContext(Dispatchers.IO) {
        db.messages(conversationId)
    }

    fun newConversation(): Long {
        conversationId = db.createConversation()
        return conversationId
    }

    fun clearConversation() {
        db.clearConversation(conversationId)
    }

    /**
     * Answers the pending [ChatEvent.ToolApprovalRequested].
     *
     * @param approved    false means the tool call is abandoned and the model is
     *                    told it did not run
     * @param trustServer true also skips confirmation for this server for the
     *                    rest of the session
     */
    fun approveToolCall(approved: Boolean, trustServer: Boolean, autoApproveTool: Boolean = false) {
        approvalAnswers.trySend(ApprovalDecision(approved, trustServer, autoApproveTool))
    }

    /** Servers trusted without confirmation for the current session. */
    fun trustedServers(): Set<String> = sessionTrustedServers.toSet()

    /** Returns the server id that advertises [toolName], or null. */
    fun serverIdOf(toolName: String): String? = registry.serverIdOf(toolName)

    /**
     * Runs one turn and emits UI events.
     *
     * The assistant row is written to the database as it streams, so a rotation
     * or a crash never loses the partial answer.
     */
    fun send(userText: String, attachmentPath: String? = null, attachmentName: String? = null): Flow<ChatEvent> = channelFlow {
        val userId = withContext(Dispatchers.IO) {
            db.insertMessage(
                StoredMessage(
                    conversationId = conversationId,
                    role = StoredMessage.ROLE_USER,
                    content = userText,
                    attachmentPath = attachmentPath,
                    attachmentName = attachmentName,
                ),
            )
        }
        // Auto-set conversation title from the first user message.
        withContext(Dispatchers.IO) {
            val count = db.readableDatabase.query(
                "messages", arrayOf("count(*) as c"), "conversation_id = ?",
                arrayOf(conversationId.toString()), null, null, null,
            ).use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("c")) else 0
            }
            if (count == 1) {
                db.updateConversationTitle(conversationId, userText.take(50))
            }
        }
        send(ChatEvent.UserMessage(userId, userText, attachmentPath, attachmentName))

        // Lazy connect: connect MCP servers on first message, not at app startup.
        if (_tools.value.isEmpty() && settings.servers().isNotEmpty()) {
            refreshServers()
        }

        val history = withContext(Dispatchers.IO) { db.messages(conversationId) }
            .filter { it.id != userId }
            .let { TurnHistory.replay(it, settings.historyLimit) }
            .map { it.toLlmMessage() }

        val buffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        val toolMessageIds = mutableMapOf<String, Long>()
        // The assistant row that currently receives streamed text. It is closed by
        // `onAssistantTurn`, and the next provider turn opens a new one.
        var currentAssistantId: Long? = null

        suspend fun openAssistantRow(): Long {
            val existing = currentAssistantId
            if (existing != null) return existing
            val id = withContext(Dispatchers.IO) {
                db.insertMessage(
                    StoredMessage(
                        conversationId = conversationId,
                        role = StoredMessage.ROLE_ASSISTANT,
                        content = "",
                        status = StoredMessage.STATUS_STREAMING,
                    ),
                )
            }
            currentAssistantId = id
            send(ChatEvent.AssistantStarted(id))
            return id
        }

        val result = engine.sendTurn(
            userText = userText,
            history = history,
            tools = if (settings.toolsEnabled) _tools.value else emptyList(),
            systemPrompt = settings.systemPrompt,
            temperature = settings.temperature.toDouble(),
            onTextDelta = { delta ->
                buffer.append(delta)
                val id = openAssistantRow()
                send(ChatEvent.AssistantDelta(id, delta))
            },
            onReasoningDelta = { delta ->
                reasoningBuffer.append(delta)
                val id = openAssistantRow()
                send(ChatEvent.ReasoningDelta(id, delta))
            },
            onAssistantTurn = { text, toolCallsJson ->
                val id = openAssistantRow()
                withContext(Dispatchers.IO) {
                    db.updateMessage(
                        id,
                        text,
                        StoredMessage.STATUS_DONE,
                        toolCallsJson,
                    )
                    if (reasoningBuffer.isNotEmpty()) {
                        db.updateReasoning(id, reasoningBuffer.toString())
                    }
                }
                send(ChatEvent.AssistantDone(id, text))
                currentAssistantId = null
                buffer.clear()
                reasoningBuffer.clear()
            },
            onToolCall = { call ->
                val id = withContext(Dispatchers.IO) {
                    db.insertMessage(
                        StoredMessage(
                            conversationId = conversationId,
                            role = StoredMessage.ROLE_TOOL,
                            content = "",
                            status = StoredMessage.STATUS_PENDING,
                            toolName = call.name,
                            toolCallId = call.id,
                            toolArgs = call.arguments,
                        ),
                    )
                }
                toolMessageIds[call.id] = id
                send(ChatEvent.ToolStarted(id, call.name, call.arguments))
            },
            onToolResult = { call, text, outcome ->
                val id = toolMessageIds[call.id]
                if (id != null) {
                    withContext(Dispatchers.IO) {
                        db.updateToolResult(
                            id,
                            text,
                            outcome == ChatEngine.ToolOutcome.Failed,
                            when (outcome) {
                                ChatEngine.ToolOutcome.Completed -> StoredMessage.STATUS_DONE
                                ChatEngine.ToolOutcome.Failed -> StoredMessage.STATUS_ERROR
                                ChatEngine.ToolOutcome.Declined -> StoredMessage.STATUS_DECLINED
                            },
                        )
                    }
                }
                send(
                    ChatEvent.ToolFinished(
                        id = id ?: 0L,
                        name = call.name,
                        args = call.arguments,
                        result = text,
                        isError = outcome == ChatEngine.ToolOutcome.Failed,
                        declined = outcome == ChatEngine.ToolOutcome.Declined,
                    ),
                )
            },
            onToolApproval = { call ->
                val serverName = registry.serverNameOf(call.name) ?: "unknown server"
                val serverId = registry.serverIdOf(call.name)

                // Persistent auto-approval: if the user has marked this tool as
                // auto-approved for this server, skip the dialog entirely.
                val isAutoApproved = serverId != null && toolApprovals.isAutoApproved(serverId, call.name)

                val needsApproval =
                    !isAutoApproved && settings.confirmToolCalls && serverName !in sessionTrustedServers

                if (!needsApproval) {
                    true
                } else {
                    send(
                        ChatEvent.ToolApprovalRequested(
                            id = toolMessageIds[call.id] ?: 0L,
                            name = call.name,
                            args = call.arguments,
                            serverName = serverName,
                        ),
                    )
                    val decision = approvalAnswers.receive()
                    if (decision.trustServer) sessionTrustedServers += serverName
                    if (decision.autoApproveTool && serverId != null) {
                        toolApprovals.setAutoApproved(serverId, call.name, true)
                    }
                    decision.approved
                }
            },
            onStatus = { message ->
                _status.value = message
                send(ChatEvent.Status(message))
            },
        )

        // A turn that ended without an explicit assistant turn (transport error).
        currentAssistantId?.let { id ->
            withContext(Dispatchers.IO) {
                db.updateMessage(
                    id,
                    buffer.toString(),
                    if (result.error != null) StoredMessage.STATUS_ERROR else StoredMessage.STATUS_DONE,
                )
            }
            send(ChatEvent.AssistantDone(id, buffer.toString()))
        }
        if (result.error != null) send(ChatEvent.Error(result.error))
        _status.value = ""
    }.flowOn(Dispatchers.Default)

    /**
     * Rebuilds the OpenAI wire messages from the stored history.
     *
     * The assistant turn that requested the tools has to carry its `tool_calls`
     * again, otherwise the provider rejects the `tool` messages that follow it.
     */
    private fun StoredMessage.toLlmMessage(): ChatMessage = when (role) {
        StoredMessage.ROLE_USER -> ChatMessage(role = "user", content = content)

        StoredMessage.ROLE_ASSISTANT -> {
            val calls = toolCallsJson
                ?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
                ?.let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        val fn = o.optJSONObject("function")
                        com.mcpai.chat.llm.ToolCall(
                            id = o.text("id").orEmpty(),
                            name = fn?.text("name").orEmpty(),
                            argumentsJson = fn?.text("arguments").orEmpty(),
                        )
                    }
                }
                .orEmpty()
            ChatMessage(role = "assistant", content = content, toolCalls = calls)
        }

        StoredMessage.ROLE_TOOL -> ChatMessage(
            role = "tool",
            content = toolResult ?: content,
            toolCallId = toolCallId,
        )

        else -> ChatMessage(role = role, content = content)
    }

    fun close() {
        registry.closeAll()
        db.close()
    }
}
