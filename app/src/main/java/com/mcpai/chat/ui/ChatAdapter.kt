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
 * and a dedicated card for a tool invocation.
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
}

/** Adapter that renders [ChatRow]s and supports incremental text updates. */
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

    fun add(row: ChatRow) {
        rows.add(row)
        notifyItemInserted(rows.size - 1)
    }

    /** Appends streamed text to an existing assistant row. */
    fun appendAssistantText(id: Long, delta: String) {
        val index = rows.indexOfFirst { it is ChatRow.Assistant && it.id == id }
        if (index < 0) return
        val current = rows[index] as ChatRow.Assistant
        rows[index] = current.copy(text = current.text + delta)
        notifyItemChanged(index, PAYLOAD_TEXT)
    }

    /** Appends a chunk of the model's reasoning trace to an assistant row. */
    fun appendReasoning(id: Long, delta: String) {
        val index = rows.indexOfFirst { it is ChatRow.Assistant && it.id == id }
        if (index < 0) return
        val current = rows[index] as ChatRow.Assistant
        rows[index] = current.copy(reasoning = current.reasoning + delta)
        notifyItemChanged(index, PAYLOAD_TEXT)
    }

    /** Marks a tool row as finished. */
    fun completeTool(
        id: Long,
        result: String,
        isError: Boolean,
        declined: Boolean = false,
    ) {
        val index = rows.indexOfFirst { it is ChatRow.Tool && it.id == id }
        if (index < 0) return
        val current = rows[index] as ChatRow.Tool
        rows[index] = current.copy(
            result = result,
            isError = isError,
            running = false,
            declined = declined,
        )
        notifyItemChanged(index)
    }

    /** Ends the streaming state of an assistant row. */
    fun markAssistantFinished(id: Long) {
        val index = rows.indexOfFirst { it is ChatRow.Assistant && it.id == id }
        if (index < 0) return
        val current = rows[index] as ChatRow.Assistant
        rows[index] = current.copy(streaming = false)
        notifyItemChanged(index, PAYLOAD_TEXT)
    }

    fun rowAt(position: Int): ChatRow? = rows.getOrNull(position)

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ChatRow.User -> TYPE_USER
        is ChatRow.Assistant -> TYPE_ASSISTANT
        is ChatRow.Tool -> TYPE_TOOL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_ASSISTANT -> AssistantHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
            else -> ToolHolder(inflater.inflate(R.layout.item_tool_call, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ChatRow.User -> (holder as UserHolder).bind(row)
            is ChatRow.Assistant -> (holder as AssistantHolder).bind(row)
            is ChatRow.Tool -> (holder as ToolHolder).bind(row)
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
        private val attachmentName: TextView = view.findViewById(R.id.attachmentName)
        fun bind(row: ChatRow.User) {
            bubble.text = row.text
            if (row.attachmentName.isNullOrBlank()) {
                attachmentName.visibility = View.GONE
            } else {
                attachmentName.visibility = View.VISIBLE
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
            bubble.visibility = View.VISIBLE
            bubble.setText(row.text.toStyledText(), TextView.BufferType.SPANNABLE)

            if (row.reasoning.isEmpty()) {
                reasoning.visibility = View.GONE
            } else {
                reasoning.visibility = View.VISIBLE
                val trace = row.reasoning.trim()
                reasoning.text = if (trace.length > 400) "… " + trace.takeLast(400) else trace
            }
        }

        /**
         * Renders Markdown, dropping straight back to the raw text while the
         * stream is still open so a half-written `**bold` never flickers.
         */
        private fun String.toStyledText(): CharSequence =
            if (this == "…") this else MarkdownStyler.render(this, itemView.context)
    }

    class ToolHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.toolName)
        private val state: TextView = view.findViewById(R.id.toolState)
        private val args: TextView = view.findViewById(R.id.args)
        private val result: TextView = view.findViewById(R.id.result)
        private val resultLabel: TextView = view.findViewById(R.id.resultLabel)
        private val dot: View = view.findViewById(R.id.statusDot)

        fun bind(row: ChatRow.Tool) {
            name.text = row.name
            args.text = prettify(row.args)
            when {
                row.running -> {
                    state.setText(R.string.label_running)
                    dot.setBackgroundResource(R.drawable.dot_running)
                    result.visibility = View.GONE
                    resultLabel.visibility = View.GONE
                }

                row.declined -> {
                    state.setText(R.string.label_declined)
                    dot.setBackgroundResource(R.drawable.dot_declined)
                    showResult(row.result.orEmpty())
                }

                row.isError -> {
                    state.setText(R.string.label_error)
                    dot.setBackgroundResource(R.drawable.dot_error)
                    showResult(row.result.orEmpty())
                }

                else -> {
                    state.setText(R.string.label_done)
                    dot.setBackgroundResource(R.drawable.dot_done)
                    showResult(row.result.orEmpty())
                }
            }
        }

        private fun showResult(text: String) {
            resultLabel.visibility = View.VISIBLE
            result.visibility = View.VISIBLE
            result.text = if (text.length > 1200) text.take(1200) + "…" else text
        }

        private fun prettify(raw: String): String = runCatching {
            org.json.JSONObject(raw).toString(2)
        }.getOrDefault(raw)
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_TOOL = 2
        private const val PAYLOAD_TEXT = "text"
    }
}
