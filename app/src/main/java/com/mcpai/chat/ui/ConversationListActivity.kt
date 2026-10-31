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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mcpai.chat.R
import com.mcpai.chat.data.ChatDatabase
import com.mcpai.chat.data.ChatRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_list)
        repo = ChatRepository(applicationContext)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { /* no-op */ }

        val adapter = ConversationAdapter { id ->
            // Switch to the selected conversation in MainActivity.
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("conversation_id", id)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        val convList = findViewById<RecyclerView>(R.id.convList)
        convList.layoutManager = LinearLayoutManager(this@ConversationListActivity)
        convList.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNewChat).setOnClickListener {
            val id = repo.newConversation()
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("conversation_id", id)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        lifecycleScope.launch {
            reload(adapter)
        }
    }

    private fun reload(adapter: ConversationAdapter) {
        lifecycleScope.launch {
            val summaries = repo.allConversations()
            adapter.submit(summaries)
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
            holder.title.text = item.title.takeIf { it.isNotBlank() } ?: "New conversation"
            holder.preview.text = item.lastMessage?.takeIf { it.isNotBlank() } ?: "—"
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
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
