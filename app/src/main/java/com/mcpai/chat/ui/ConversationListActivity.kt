package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.mcpai.chat.data.ChatDatabase
import com.mcpai.chat.data.ChatRepository
import kotlinx.coroutines.launch

/**
 * Lists existing conversations so the user can switch between them or start a new one.
 *
 * This is the app's entry point: MainActivity opens a specific conversation when launched
 * with a `conversation_id` extra, otherwise it creates (or reuses) the latest conversation.
 */
class ConversationListActivity : ComponentActivity() {

    private lateinit var repo: ChatRepository

    private val summaries = mutableStateOf<List<ChatDatabase.ConversationSummary>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = ChatRepository.get(applicationContext)
        setContent {
            com.mcpai.chat.ui.theme.McpAiChatTheme {
                ConversationListScreen(
                    summaries = summaries.value,
                    onOpen = ::openConversation,
                    onNewChat = ::startNewConversation,
                    onDelete = ::deleteConversation,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload every time this screen comes back into focus: a conversation
        // created in MainActivity is not visible until then.
        lifecycleScope.launch { summaries.value = repo.allConversations() }
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

    private fun deleteConversation(id: Long) {
        lifecycleScope.launch {
            repo.deleteConversation(id)
            summaries.value = repo.allConversations()
        }
    }
}
