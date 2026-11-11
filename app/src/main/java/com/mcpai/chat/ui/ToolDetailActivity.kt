package com.mcpai.chat.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.ui.theme.McpAiChatTheme
import kotlinx.coroutines.launch

/** Lists the tools advertised by the connected MCP servers, with auto-approve toggles. */
class ToolDetailActivity : ComponentActivity() {

    private val tools = mutableStateOf<List<com.mcpai.chat.mcp.McpTool>>(emptyList())
    private val loading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = ChatRepository.get(applicationContext)

        setContent {
            McpAiChatTheme {
                ToolDetailScreen(
                    tools = tools.value,
                    loading = loading.value,
                    isAutoApproved = { name ->
                        repo.serverIdOf(name)?.let { id -> repo.toolApprovals.isAutoApproved(id, name) } ?: false
                    },
                    onToggleAutoApprove = { name, approve ->
                        repo.serverIdOf(name)?.let { id -> repo.toolApprovals.setAutoApproved(id, name, approve) }
                    },
                    onBack = ::finish,
                )
            }
        }

        lifecycleScope.launch {
            repo.refreshServers()
            tools.value = repo.tools.value
            loading.value = false
        }
    }
}
