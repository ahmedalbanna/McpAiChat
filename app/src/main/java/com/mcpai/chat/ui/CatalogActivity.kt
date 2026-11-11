package com.mcpai.chat.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import com.mcpai.chat.mcp.FreeMcpCatalog
import com.mcpai.chat.ui.theme.McpAiChatTheme

/**
 * Browsable catalogue of free, no-authentication remote MCP servers (Compose).
 *
 * The list was produced by probing every anonymous endpoint in the
 * awesome-remote-mcp-servers directory with a real `initialize` + `tools/list`
 * handshake, so every entry here is known to answer.
 */
class CatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            McpAiChatTheme {
                CatalogScreen(
                    onAdd = { entry ->
                        // The result is handed back to the caller instead of writing the
                        // settings here: finishing during a click would leak the touch
                        // to the activity underneath and open the wrong screen.
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_URL, entry.url)
                                .putExtra(EXTRA_NAME, entry.name),
                        )
                        finish()
                    },
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
    }
}
