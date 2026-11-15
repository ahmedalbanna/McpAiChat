package com.mcpai.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mcpai.chat.data.ChatEvent
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.StoredMessage

import timber.log.Timber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI state for a file attached to the next message. */
data class ComposerAttachment(
    val name: String,
    /** True after the user taps Send and until the turn ends. */
    val sending: Boolean,
)

/**
 * Owns the conversation UI state. The turn itself lives in the repository,
 * so rotation and configuration changes do not kill it — and neither does
 * navigating back to the conversation list.
 *
 * The activity lifecycle is short-lived; the turn is not. This ViewModel is
 * only the UI projection: the message list is a single [StateFlow] the UI
 * paints, and the tool-approval request is repo state the activity mirrors —
 * a recreated activity re-reads it and shows the same dialog.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val PENDING_THINKING_ID = -1L
    }

    val repo: ChatRepository = ChatRepository.get(application)

    private val _rows = MutableStateFlow<List<ChatRow>>(emptyList())
    val rows: StateFlow<List<ChatRow>> = _rows.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    /** A turn that ended in failure. Cleared on send/regenerate and by the banner's dismiss. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Group ids (first tool-call row id) the user has expanded on this screen. */
    private val expandedToolGroups = MutableStateFlow<Set<Long>>(emptySet())

    /** Reasoning disclosures (assistant row id) the user has expanded on this screen. */
    private val expandedReasoning = MutableStateFlow<Set<Long>>(emptySet())
    val reasoningExpanded: StateFlow<Set<Long>> = expandedReasoning.asStateFlow()

    // The turn's job lives in the repository, not in this ViewModel: the
    // activity may be destroyed and recreated (navigation, configuration)
    // without killing the answer. This ViewModel is only the UI projection.

    /** The approval the engine is waiting on; restored here on recreate. */
    val approval: StateFlow<ChatEvent.ToolApprovalRequested?> = repo.pendingApproval

    /** True while a turn is running, from this process's perspective. */
    val sending: StateFlow<Boolean> = repo.sending

    /**
     * Rows to paint: consecutive tool calls are coalesced into one compact
     * [ChatRow.ToolGroup] per run, collapsed by default, with a temporary
     * "Thinking…" marker appended while the provider is between model calls.
     * Neither the grouping nor the thinking row is persisted.
     */
    val displayedRows: StateFlow<List<ChatRow>> =
        combine(_rows, repo.sending, expandedToolGroups) { rows, sending, expanded ->
            val grouped = coalesceToolRuns(rows, expanded)
            if (shouldShowThinking(grouped, sending)) {
                grouped + ChatRow.Thinking(PENDING_THINKING_ID)
            } else {
                grouped
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cancels the in-flight turn. Partial streamed text stays in the DB. */
    fun cancelTurn() = repo.cancelTurn()

    private val _attachment = MutableStateFlow<ComposerAttachment?>(null)
    val attachment: StateFlow<ComposerAttachment?> = _attachment.asStateFlow()

    /** The composer's current draft text. The view-model owns it so the voice
     * path can append and rotation cannot lose it. */
    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    /** True while the user is editing the last user message in the composer. */
    private val _editingLastMessage = MutableStateFlow(false)
    val editingLastMessage: StateFlow<Boolean> = _editingLastMessage.asStateFlow()

    fun setComposerText(value: String) {
        _composerText.value = value
    }

    private var selectedAttachmentPath: String? = null
    private var selectedAttachmentName: String? = null

    init {
        // The turn's events are owned by the repository; this process-global
        // collector projects them onto the screen's rows. A recreated
        // activity re-attaches through a fresh ViewModel.
        viewModelScope.launch {
            repo.turnEvents.collect { handleTurnEvent(it) }
        }
    }

    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            if (conversationId > 0) {
                repo.setConversationId(conversationId)
            } else {
                repo.openConversation()
            }
            reloadHistory()
        }
    }

    fun reloadHistory() {
        viewModelScope.launch {
            _rows.value = repo.loadHistory().map { it.toRow() }
        }
    }

    fun newConversation() {
        _editingLastMessage.value = false
        repo.newConversation()
        reloadHistory()
    }

    fun clearConversation() {
        _editingLastMessage.value = false
        repo.clearConversation()
        reloadHistory()
    }

    /** Stores a file as the pending composer attachment. */
    fun selectAttachment(path: String, name: String) {
        if (_attachment.value?.sending == true) return
        selectedAttachmentPath = path
        selectedAttachmentName = name
        _attachment.value = ComposerAttachment(name, sending = false)
    }

    /** Removes the pending attachment. A sending attachment is deliberately immutable. */
    fun clearAttachment() {
        if (_attachment.value?.sending == true) return
        selectedAttachmentPath = null
        selectedAttachmentName = null
        _attachment.value = null
    }

    fun send(text: String) {
        if (text.isBlank() || repo.sending.value) return

        val attachmentPath = selectedAttachmentPath
        val attachmentName = selectedAttachmentName
        val editing = _editingLastMessage.value
        val attachmentChanged = attachmentName != null
        selectedAttachmentPath = null
        selectedAttachmentName = null
        _attachment.value = attachmentName?.let { ComposerAttachment(it, sending = true) }

        _error.value = null
        _status.value = null
        _editingLastMessage.value = false
        if (editing) {
            viewModelScope.launch {
                val flow = repo.editAndRerunLast(text, attachmentPath, attachmentName, attachmentChanged)
                if (flow == null) {
                    _attachment.value = null
                    return@launch
                }
                // Show the edited bubble immediately; the incoming events
                // then append the fresh assistant row.
                _rows.value = repo.loadHistory().map { it.toRow() }
                repo.startTurn(flow)
            }
        } else {
            repo.startTurn(repo.send(text, attachmentPath, attachmentName))
        }
    }

    /**
     * Re-runs the last user turn after truncating everything after it.
     * The previous attempt's assistant and tool rows are deleted; the user
     * bubble stays. Backs both the error banner's Retry and the assistant
     * row's Regenerate button.
     */
    fun regenerateLast() {
        if (repo.sending.value) return
        viewModelScope.launch {
            val flow = repo.regenerate()
            if (flow == null) return@launch
            // Show the truncated history first; incoming events then append
            // the fresh assistant row.
            _rows.value = repo.loadHistory().map { it.toRow() }
            _error.value = null
            _status.value = null
            repo.startTurn(flow)
        }
    }

    /** Pre-fills the composer with the last user message's text. */
    fun startEditLastMessage() {
        if (repo.sending.value) return
        val lastUser = _rows.value.lastOrNull { it is ChatRow.User } as? ChatRow.User ?: return
        _error.value = null
        _status.value = null
        setComposerText(lastUser.text)
        _editingLastMessage.value = true
    }

    /** Exits edit mode and clears the draft. */
    fun cancelEditLastMessage() {
        _editingLastMessage.value = false
        setComposerText("")
    }

    /** Retry is the same operation as regenerate: both re-run the last user turn. */
    fun retryLastTurn() = regenerateLast()

    /** Clears a failure banner. Stays gone until the next send or regenerate. */
    fun dismissError() {
        _error.value = null
    }

    private fun handleTurnEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.UserMessage ->
                _rows.value += ChatRow.User(event.id, event.text)

            is ChatEvent.AssistantStarted ->
                _rows.value += ChatRow.Assistant(event.id, "", true)

            is ChatEvent.AssistantDelta ->
                _rows.value = _rows.value.map { row ->
                    if (row is ChatRow.Assistant && row.id == event.id) {
                        row.copy(
                            text = row.text + event.delta,
                            reasoningMs = row.reasoningMs
                                ?: row.reasoningStartedAtMs?.let { System.currentTimeMillis() - it },
                        )
                    } else row
                }

            is ChatEvent.ReasoningDelta ->
                _rows.value = _rows.value.map { row ->
                    if (row is ChatRow.Assistant && row.id == event.id) {
                        row.copy(
                            reasoning = row.reasoning + event.delta,
                            reasoningStartedAtMs = row.reasoningStartedAtMs
                                ?: System.currentTimeMillis(),
                        )
                    } else row
                }

            is ChatEvent.AssistantDone ->
                _rows.value = _rows.value.map { row ->
                    if (row is ChatRow.Assistant && row.id == event.id) {
                        row.copy(
                            streaming = false,
                            reasoningMs = row.reasoningMs
                                ?: row.reasoningStartedAtMs?.let { System.currentTimeMillis() - it },
                        )
                    } else row
                }

            is ChatEvent.ToolStarted ->
                _rows.value += ChatRow.Tool(event.id, event.name, event.args, null, false, true)

            is ChatEvent.ToolFinished ->
                _rows.value = _rows.value.map { row ->
                    if (row is ChatRow.Tool && row.id == event.id) {
                        row.copy(
                            result = event.result,
                            isError = event.isError,
                            running = false,
                            declined = event.declined,
                        )
                    } else row
                }

            // The approval is repo state; the dialog reads it directly.
            is ChatEvent.ToolApprovalRequested -> Unit

            is ChatEvent.Status -> _status.value = event.message

            is ChatEvent.Error -> _error.value = event.message

            is ChatEvent.TurnEnded -> {
                // Only transient progress clears at turn end. An error stays on
                // screen until the next send, otherwise it flashes and vanishes.
                if (_error.value == null) _status.value = null
                // Never leave a streaming bubble or a spinner on a row whose
                // turn was cancelled or just ended.
                val finishedAt = System.currentTimeMillis()
                _rows.value = _rows.value.map { row ->
                    when (row) {
                        is ChatRow.Assistant -> if (row.streaming) {
                            row.copy(
                                streaming = false,
                                reasoningMs = row.reasoningMs
                                    ?: row.reasoningStartedAtMs?.let { finishedAt - it },
                            )
                        } else {
                            row
                        }
                        is ChatRow.Tool -> if (row.running) row.copy(running = false) else row
                        else -> row
                    }
                }
                _attachment.value = null
            }
        }
    }

    fun approve(request: ChatEvent.ToolApprovalRequested, approved: Boolean, trust: Boolean, autoApprove: Boolean) {
        if (repo.pendingApproval.value?.id != request.id) {
            Timber.tag("Mcp.Approval").w("ignoring stale answer: %s (pending: %s)", request.name, repo.pendingApproval.value?.name)
            return
        }
        repo.approveToolCall(approved = approved, trustServer = trust, autoApproveTool = autoApprove)
    }

    override fun onCleared() {
        // The turn keeps running in the repository; do not cancel it here.
        super.onCleared()
    }

    /** Flips one coalesced tool-call group between collapsed and expanded. */
    fun toggleToolGroup(groupId: Long) {
        expandedToolGroups.value = expandedToolGroups.value.let { current ->
            if (groupId in current) current - groupId else current + groupId
        }
    }

    /** Flips one reasoning disclosure between collapsed and expanded. */
    fun toggleReasoning(rowId: Long) {
        expandedReasoning.value = expandedReasoning.value.let { current ->
            if (rowId in current) current - rowId else current + rowId
        }
    }

    /**
     * Coalesces runs of consecutive tool rows into a [ChatRow.ToolGroup].
     * A tool separated from another tool by any other row opens a new group.
     */
    private fun coalesceToolRuns(rows: List<ChatRow>, expanded: Set<Long>): List<ChatRow> {
        val result = ArrayList<ChatRow>(rows.size)
        var pending = mutableListOf<ChatRow.Tool>()

        fun flush() {
            if (pending.isEmpty()) return
            val first = pending.first()
            result += ChatRow.ToolGroup(
                id = first.id,
                tools = pending.toList(),
                expanded = first.id in expanded,
                createdAt = first.createdAt,
            )
            pending = mutableListOf()
        }

        for (row in rows) {
            if (row is ChatRow.Tool) {
                pending += row
            } else {
                flush()
                result += row
            }
        }
        flush()
        return result
    }

    /** Whether the next row should be an ephemeral working indicator. */
    private fun shouldShowThinking(rows: List<ChatRow>, sending: Boolean): Boolean {
        if (!sending || rows.isEmpty()) return false
        return when (val last = rows.last()) {
            // The assistant bubble/row itself renders the thinking label while it streams.
            is ChatRow.Assistant -> !last.streaming
            // A running tool group carries its own progress indicator.
            is ChatRow.ToolGroup -> last.tools.none { it.running }
            is ChatRow.Tool -> !last.running
            else -> true
        }
    }

    private fun StoredMessage.toRow(): ChatRow = when (role) {
        StoredMessage.ROLE_USER ->
            ChatRow.User(id, content, attachmentPath, attachmentName, createdAt = createdAt)
        StoredMessage.ROLE_TOOL -> ChatRow.Tool(
            id = id,
            name = toolName.orEmpty(),
            args = toolArgs.orEmpty(),
            result = toolResult,
            isError = toolError,
            running = status == StoredMessage.STATUS_PENDING,
            declined = status == StoredMessage.STATUS_DECLINED,
            createdAt = createdAt,
        )

        else -> ChatRow.Assistant(
            id,
            content,
            status == StoredMessage.STATUS_STREAMING,
            reasoning.orEmpty(),
            createdAt = createdAt,
        )
    }
}
