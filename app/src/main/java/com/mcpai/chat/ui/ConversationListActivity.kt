package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatDatabase
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.util.Markdown
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lists existing conversations so the user can switch between them or start a new one.
 *
 * This is the app's entry point: MainActivity opens a specific conversation when launched
 * with a `conversation_id` extra, otherwise it creates (or reuses) the latest conversation.
 */
class ConversationListActivity : AppCompatActivity() {

    private lateinit var repo: ChatRepository
    private lateinit var adapter: ConversationAdapter
    private lateinit var conversationSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_list)
        repo = ChatRepository.get(applicationContext)
        conversationSummary = findViewById(R.id.conversationSummary)

        adapter = ConversationAdapter { id ->
            openConversation(id)
        }

        val convList = findViewById<RecyclerView>(R.id.convList)
        convList.layoutManager = LinearLayoutManager(this@ConversationListActivity)
        convList.adapter = adapter

        findViewById<MaterialButton>(R.id.newChatButton).setOnClickListener {
            startNewConversation()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload every time this screen comes back into focus: a conversation
        // created in MainActivity is not visible until then.
        lifecycleScope.launch { reload(adapter) }
    }

    private fun openConversation(id: Long) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                putExtra("conversation_id", id)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
    }

    private fun startNewConversation() = openConversation(repo.newConversation())

    private fun reload(adapter: ConversationAdapter) {
        lifecycleScope.launch {
            val summaries = repo.allConversations()
            adapter.submit(summaries)
            conversationSummary.text = resources.getQuantityString(
                R.plurals.conversation_count,
                summaries.size,
                summaries.size,
            )
            findViewById<TextView>(R.id.emptyView).visibility =
                if (summaries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Adapter for the conversation list. */
    private class ConversationAdapter(
        private val onClick: (Long) -> Unit,
    ) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

        private var items: List<ChatDatabase.ConversationSummary> = emptyList()

        fun submit(summaries: List<ChatDatabase.ConversationSummary>) {
            this.items = summaries
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            holder.title.text = item.title.takeIf { it.isNotBlank() } ?: context.getString(R.string.untitled_conversation)
            holder.preview.text = item.lastMessage
                ?.takeIf { it.isNotBlank() }
                ?.let { Markdown.plain(it).take(140) }
                ?: context.getString(R.string.no_message_preview)
            holder.updated.text = formatTime(item.updatedAt)
            holder.itemView.setOnClickListener { onClick(item.id) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.convTitle)
            val preview: TextView = view.findViewById(R.id.convPreview)
            val updated: TextView = view.findViewById(R.id.convUpdated)
        }
    }
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
