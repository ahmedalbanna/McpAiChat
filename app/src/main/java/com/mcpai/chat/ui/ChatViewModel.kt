package com.mcpai.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mcpai.chat.data.ChatEvent
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.StoredMessage
import kotlinx.coroutines.Job
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
 * Owns the conversation turn so rotation and configuration changes do not kill
 * it.
 *
 * The activity lifecycle is short-lived; the turn is not. All work happens in
 * [viewModelScope], the message list is a single [StateFlow] the UI paints,
 * and the tool-approval request is a state value rather than a collector —
 * a recreated activity simply re-collects it and shows the same dialog.
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

    private val _approval = MutableStateFlow<ChatEvent.ToolApprovalRequested?>(null)
    val approval: StateFlow<ChatEvent.ToolApprovalRequested?> = _approval.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /**
     * Rows to paint, including a temporary "Thinking…" marker after the last
     * persisted turn while the provider is between model calls. It is never
     * persisted and disappears as soon as the streamed assistant row (or a
     * running tool row) takes over.
     */
    val displayedRows: StateFlow<List<ChatRow>> =
        combine(_rows, _sending) { rows, sending ->
            if (shouldShowThinking(rows, sending)) {
                rows + ChatRow.Thinking(PENDING_THINKING_ID)
            } else {
                rows
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _attachment = MutableStateFlow<ComposerAttachment?>(null)
    val attachment: StateFlow<ComposerAttachment?> = _attachment.asStateFlow()

    private var selectedAttachmentPath: String? = null
    private var selectedAttachmentName: String? = null

    private var turnJob: Job? = null

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
        repo.newConversation()
        reloadHistory()
    }

    fun clearConversation() {
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
        if (text.isBlank() || turnJob?.isActive == true) return

        val attachmentPath = selectedAttachmentPath
        val attachmentName = selectedAttachmentName
        selectedAttachmentPath = null
        selectedAttachmentName = null
        _attachment.value = attachmentName?.let { ComposerAttachment(it, sending = true) }

        // Start clean: the previous turn's error stays visible until this send.
        _status.value = null
        _sending.value = true
        turnJob = viewModelScope.launch {
            var lastError: String? = null
            try {
                repo.send(text, attachmentPath, attachmentName).collect { event ->
                    when (event) {
                    is ChatEvent.UserMessage ->
                        _rows.value += ChatRow.User(event.id, event.text)

                    is ChatEvent.AssistantStarted ->
                        _rows.value += ChatRow.Assistant(event.id, "", true)

                    is ChatEvent.AssistantDelta ->
                        _rows.value = _rows.value.map { row ->
                            if (row is ChatRow.Assistant && row.id == event.id) {
                                row.copy(text = row.text + event.delta)
                            } else row
                        }

                    is ChatEvent.ReasoningDelta ->
                        _rows.value = _rows.value.map { row ->
                            if (row is ChatRow.Assistant && row.id == event.id) {
                                row.copy(reasoning = row.reasoning + event.delta)
                            } else row
                        }

                    is ChatEvent.AssistantDone ->
                        _rows.value = _rows.value.map { row ->
                            if (row is ChatRow.Assistant && row.id == event.id) {
                                row.copy(streaming = false)
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

                    is ChatEvent.ToolApprovalRequested -> _approval.value = event

                    is ChatEvent.Status -> _status.value = event.message
                    is ChatEvent.Error -> {
                        lastError = event.message
                        _status.value = event.message
                    }
                    }
                }
            } finally {
                // Only transient progress clears at turn end. An error stays on
                // screen until the next send, otherwise it flashes and vanishes.
                if (lastError == null) _status.value = null
                _attachment.value = null
                _sending.value = false
            }
        }
    }

    fun approve(request: ChatEvent.ToolApprovalRequested, approved: Boolean, trust: Boolean, autoApprove: Boolean) {
        if (_approval.value?.id != request.id) return
        _approval.value = null
        repo.approveToolCall(approved = approved, trustServer = trust, autoApproveTool = autoApprove)
    }

    override fun onCleared() {
        turnJob?.cancel()
        super.onCleared()
    }

    /** Whether the next row should be an ephemeral working indicator. */
    private fun shouldShowThinking(rows: List<ChatRow>, sending: Boolean): Boolean {
        if (!sending || rows.isEmpty()) return false
        return when (val last = rows.last()) {
            // The assistant bubble/row itself renders the thinking label while it streams.
            is ChatRow.Assistant -> !last.streaming
            // Running tool cards already carry their own progress indicator.
            is ChatRow.Tool -> !last.running
            else -> true
        }
    }

    private fun StoredMessage.toRow(): ChatRow = when (role) {
        StoredMessage.ROLE_USER -> ChatRow.User(id, content, attachmentPath, attachmentName)
        StoredMessage.ROLE_TOOL -> ChatRow.Tool(
            id = id,
            name = toolName.orEmpty(),
            args = toolArgs.orEmpty(),
            result = toolResult,
            isError = toolError,
            running = status == StoredMessage.STATUS_PENDING,
            declined = status == StoredMessage.STATUS_DECLINED,
        )

        else -> ChatRow.Assistant(id, content, status == StoredMessage.STATUS_STREAMING, reasoning.orEmpty())
    }
}
