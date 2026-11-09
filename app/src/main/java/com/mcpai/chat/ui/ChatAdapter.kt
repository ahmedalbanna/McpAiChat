package com.mcpai.chat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mcpai.chat.R

/**
 * One row in the conversation.
 *
 * The row kinds mirror the reference apps: a user bubble, an assistant bubble,
 * and one compact group row for consecutive tool calls.
 */
sealed interface ChatRow {

    data class User(
        val id: Long,
        val text: String,
        val attachmentPath: String? = null,
        val attachmentName: String? = null,
    ) : ChatRow

    data class Assistant(
        val id: Long,
        val text: String,
        val streaming: Boolean,
        val reasoning: String = "",
    ) : ChatRow

    data class Tool(
        val id: Long,
        val name: String,
        val args: String,
        val result: String?,
        val isError: Boolean,
        val running: Boolean,
        val declined: Boolean = false,
    ) : ChatRow

    /** A temporary row shown while the app is waiting for the next model turn. */
    data class Thinking(val id: Long) : ChatRow

    /**
     * Consecutive tool calls grouped behind one compact marker:
     * `Tools used: N · Calls: M`, collapsed by default. The [id] is the first
     * call's row id and is what the expanded-set keys on.
     */
    data class ToolGroup(
        val id: Long,
        val tools: List<Tool>,
        val expanded: Boolean,
    ) : ChatRow
}

/** Adapter that renders [ChatRow]s and supports incremental text updates. */
class ChatAdapter(
    private val onToolGroupToggle: (Long) -> Unit = { },
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val rows = mutableListOf<ChatRow>()

    fun submit(newRows: List<ChatRow>) {
        val prev = rows.toList()
        rows.clear()
        rows.addAll(newRows)

        // Diffs the new state against what is on screen instead of calling
        // `notifyDataSetChanged` on every streamed token: only an inserted row
        // costs work, and a text update in an existing row reuses the payload
        // path that keeps the bubble's spans without re-binding everything.
        val common = minOf(prev.size, newRows.size)
        var changed = false
        for (index in 0 until common) {
            val oldRow = prev[index]
            val newRow = newRows[index]
            if (oldRow === newRow) continue
            if (oldRow is ChatRow.Assistant && newRow is ChatRow.Assistant && oldRow.id == newRow.id) {
                notifyItemChanged(index, PAYLOAD_TEXT)
                changed = true
            } else if (oldRow != newRow) {
                notifyItemRangeChanged(index, 1)
                changed = true
            }
        }
        if (newRows.size > prev.size) {
            notifyItemRangeInserted(prev.size, newRows.size - prev.size)
        } else if (newRows.size < prev.size) {
            notifyItemRangeRemoved(newRows.size, prev.size - newRows.size)
        }
        if (!changed && newRows.size == prev.size && newRows != prev) {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ChatRow.User -> TYPE_USER
        is ChatRow.Assistant -> TYPE_ASSISTANT
        is ChatRow.ToolGroup -> TYPE_TOOL_GROUP
        is ChatRow.Thinking -> TYPE_THINKING
        is ChatRow.Tool -> TYPE_TOOL_GROUP // defensive; raw calls are coalesced upstream
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_ASSISTANT -> AssistantHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
            TYPE_THINKING -> ThinkingHolder(inflater.inflate(R.layout.item_message_thinking, parent, false))
            else -> ToolGroupHolder(
                inflater.inflate(R.layout.item_message_tool_group, parent, false),
                onToolGroupToggle,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ChatRow.User -> (holder as UserHolder).bind(row)
            is ChatRow.Assistant -> (holder as AssistantHolder).bind(row)
            is ChatRow.ToolGroup -> (holder as ToolGroupHolder).bind(row)
            is ChatRow.Thinking -> Unit
            is ChatRow.Tool -> (holder as ToolGroupHolder)
                .bind(ChatRow.ToolGroup(row.id, listOf(row), expanded = false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TEXT) && holder is AssistantHolder) {
            holder.bind(rows[position] as ChatRow.Assistant)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    // ---- holders ------------------------------------------------------------

    class UserHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.bubble)
        private val attachmentPreview: View = view.findViewById(R.id.attachmentPreview)
        private val attachmentName: TextView = view.findViewById(R.id.attachmentName)

        fun bind(row: ChatRow.User) {
            bubble.text = row.text
            if (row.attachmentName.isNullOrBlank()) {
                attachmentPreview.visibility = View.GONE
            } else {
                attachmentPreview.visibility = View.VISIBLE
                attachmentName.text = row.attachmentName
            }
        }
    }

    class AssistantHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.bubble)
        private val reasoning: TextView = view.findViewById(R.id.reasoning)

        fun bind(row: ChatRow.Assistant) {
            // An assistant turn that only requested tools has no text; the row stays
            // in the list to keep the order but takes no space.
            if (row.text.isEmpty() && !row.streaming && row.reasoning.isEmpty()) {
                bubble.visibility = View.GONE
                reasoning.visibility = View.GONE
                return
            }

            if (row.reasoning.isEmpty()) {
                reasoning.visibility = View.GONE
            } else {
                reasoning.visibility = View.VISIBLE
                val trace = row.reasoning.trim()
                reasoning.text = if (trace.length > 400) "… " + trace.takeLast(400) else trace
            }

            if (row.text.isEmpty() && row.streaming) {
                // The provider has opened the assistant turn but no answer text is
                // visible yet: render the working label rather than an empty bubble.
                bubble.visibility = if (row.reasoning.isEmpty()) View.VISIBLE else View.GONE
                bubble.setText(itemView.context.getString(R.string.ai_thinking), TextView.BufferType.NORMAL)
                return
            }

            bubble.visibility = View.VISIBLE
            bubble.setText(row.text.toStyledText(), TextView.BufferType.SPANNABLE)
        }

        /**
         * Renders Markdown, dropping straight back to the raw text while the
         * stream is still open so a half-written `**bold` never flickers.
         */
        private fun String.toStyledText(): CharSequence =
            if (this == "…") this else MarkdownStyler.render(this, itemView.context)
    }

    class ToolGroupHolder(view: View, private val onToggle: (Long) -> Unit) : RecyclerView.ViewHolder(view) {
        private val header: View = view.findViewById(R.id.toolGroupHeader)
        private val chevron: android.widget.ImageView = view.findViewById(R.id.toolGroupChevron)
        private val summary: TextView = view.findViewById(R.id.toolGroupSummary)
        private val progress: android.widget.ProgressBar = view.findViewById(R.id.toolGroupProgress)
        private val details: View = view.findViewById(R.id.toolGroupDetails)
        private val calls: TextView = view.findViewById(R.id.toolGroupCalls)

        fun bind(row: ChatRow.ToolGroup) {
            val context = itemView.context
            val toolCount = row.tools.map { it.name }.distinct().size
            val callCount = row.tools.size
            val anyRunning = row.tools.any { it.running }

            summary.text = context.getString(R.string.tools_used_calls, toolCount, callCount)
            progress.visibility = if (anyRunning) View.VISIBLE else View.GONE
            chevron.setImageResource(
                if (row.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right,
            )
            details.visibility = if (row.expanded) View.VISIBLE else View.GONE
            calls.text = row.tools.joinToString("\n") { callLine(it, context) }

            header.setOnClickListener { onToggle(row.id) }
            header.contentDescription = summary.text
        }

        private fun callLine(tool: ChatRow.Tool, context: android.content.Context): String {
            val status = when {
                tool.running -> context.getString(R.string.label_running)
                tool.declined -> context.getString(R.string.label_declined)
                tool.isError -> context.getString(R.string.label_error)
                else -> context.getString(R.string.label_done)
            }
            // Keep the expanded list readable: successful calls are a name and a
            // status; only failures and declines carry a short reason.
            if (!tool.running && !tool.isError && !tool.declined) {
                return "• ${tool.name} · $status"
            }
            val detail = tool.result
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.take(80)
                .orEmpty()
            return if (detail.isEmpty()) {
                "• ${tool.name} · $status"
            } else {
                "• ${tool.name} · $status · $detail"
            }
        }
    }

    class ThinkingHolder(view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_TOOL_GROUP = 2
        private const val TYPE_THINKING = 3
        private const val PAYLOAD_TEXT = "text"
    }
}
