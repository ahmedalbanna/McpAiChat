package com.mcpai.chat.ui

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mcpai.chat.data.ChatDatabase
import com.mcpai.chat.data.ChatRepository
import com.mcpai.chat.data.Settings
import com.mcpai.chat.data.SqliteToolApprovalStore
import com.mcpai.chat.llm.CannedOpenAiServer
import com.mcpai.chat.mcp.CannedMcpServer
import com.mcpai.chat.mcp.McpServerConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * End-to-end tool-approval flow on the device:
 *
 *   canned OpenAI SSE server  ->  engine  ->  approval dialog  ->  canned MCP server
 *
 * The app runs in its real process with Settings seeded to the two loopback
 * fixtures, so the test exercises the same code path a real provider would:
 * the streamed tool call, the approval UI, the MCP call and the transcript.
 * Everything it changes is restored by the rig's finally block.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ToolApprovalFlowDeviceTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    /** The one repository for this process; the app uses the same instance. */
    private val repo by lazy { ChatRepository.get(ApplicationProvider.getApplicationContext()) }

    /** The canned environment; seeded before the activity launches. */
    private val rig = CannedRigRule()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(rig).around(composeRule)

    @Test
    fun approveRunsTheTool() {
        waitForTools()
        openNewConversation()
        sendMessage("Use the echo tool and show its output.")

        waitForDialog()
        // The dialog spells out who wants what, with which arguments.
        composeRule.onNodeWithText("Run this tool?").assertExists()
        composeRule.onNodeWithText(DEBUG_SERVER_NAME).assertExists()
        composeRule.onNodeWithText("canned echo", substring = true).assertExists()

        composeRule.onNodeWithText("Allow").performClick()

        waitForSubstring("Echoed")
        assertEquals(1, rig.mcpServer.toolsCallCount.get())
    }

    @Test
    fun denyDoesNotRunTheTool() {
        waitForTools()
        openNewConversation()
        sendMessage("Use the echo tool and show its output.")

        waitForDialog()
        composeRule.onNodeWithText("Deny").performClick()

        // The declined row sits inside the collapsed tool group, so don't
        // assert on visible text here; the engine still completes the turn
        // with the decline in history, and the tool itself never ran.
        waitForSubstring("Echoed")
        assertEquals(0, rig.mcpServer.toolsCallCount.get())
    }

    @Test
    fun alwaysAllowPersistsAcrossConversations() {
        waitForTools()
        openNewConversation()
        sendMessage("Use the echo tool and show its output.")

        waitForDialog()
        // Switch 0 is session trust; switch 1 is the persistent auto-approve.
        val switches = onAllNodesWithRole(Role.Switch)
        switches[1].assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText("Allow").performClick()
        waitForSubstring("Echoed")
        assertEquals(1, rig.mcpServer.toolsCallCount.get())

        // A fresh conversation must skip the dialog for this server + tool.
        openNewConversation()
        sendMessage("Use the echo tool again.")
        waitForSubstring("Echoed")
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Run this tool?").fetchSemanticsNodes().size,
        )
        assertEquals(2, rig.mcpServer.toolsCallCount.get())
    }

    private fun openNewConversation() {
        // Prove the tap actually opened a fresh conversation: the repository
        // swaps its id when it does. Without this check a missed tap silently
        // makes the auto-approve assertion test the wrong thing.
        val before = repo.currentConversationId()
        // The toolbar icon opens a dropdown; the menu item starts the chat.
        composeRule.onNodeWithContentDescription("New conversation").performClick()
        composeRule.onNodeWithText("New conversation").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repo.currentConversationId() != before
        }
    }

    private fun sendMessage(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
        composeRule.onNodeWithContentDescription("Send").performClick()
    }

    private fun waitForTools() {
        // refreshServers runs in the background on onCreate; wait until the
        // canned echo tool is live so the turn's tools are deterministic.
        // Any connected tool is not enough: the rig's server must be one of
        // them or the tool call goes down a different path.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            repo.tools.value.any { it.name == "echo" }
        }
    }

    private fun waitForDialog() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("Run this tool?").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForSubstring(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun onAllNodesWithRole(role: Role) =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))

    companion object {
        /** Id of the server added by the test rig; also the approval-store key. */
        const val DEBUG_SERVER_ID = "canned-approval-test-server"
        const val DEBUG_SERVER_NAME = "Canned test server"
    }
}

/**
 * Seed/restore wrapper: seeds Settings and starts the fixtures before the
 * activity launches, and restores everything it touched afterwards.
 *
 * Without the outer rule the app would start on the user's real provider and
 * servers and open the user's latest conversation.
 */
private class CannedRigRule : TestRule {
    val mcpServer = CannedMcpServer()
    val openaiServer = CannedOpenAiServer()

    private var apiKey: String? = null
    private var baseUrl: String? = null
    private var model: String? = null
    private var systemPrompt: String? = null
    private var toolsEnabled: Boolean? = null
    private var confirmToolCalls: Boolean? = null
    private var activeServerId: String? = null
    private var serversBackup: List<McpServerConfig> = emptyList()
    private var conversationsBefore: Set<Long> = emptySet()

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val ctx = ApplicationProvider.getApplicationContext<Context>()
                val settings = Settings.get(ctx)
                apiKey = settings.openAiApiKey
                baseUrl = settings.openAiBaseUrl
                model = settings.model
                systemPrompt = settings.systemPrompt
                toolsEnabled = settings.toolsEnabled
                confirmToolCalls = settings.confirmToolCalls
                activeServerId = settings.activeServerId
                serversBackup = settings.servers()
                conversationsBefore = ChatDatabase(ctx).allConversations().map { it.id }.toSet()

                mcpServer.start()
                openaiServer.start()
                settings.openAiBaseUrl = openaiServer.url()
                settings.openAiApiKey = "canned-test-key"
                settings.model = "canned-model"
                settings.systemPrompt = "Canned test system prompt."
                settings.toolsEnabled = true
                settings.confirmToolCalls = true
                settings.addServer(
                    McpServerConfig(
                        id = ToolApprovalFlowDeviceTest.DEBUG_SERVER_ID,
                        name = ToolApprovalFlowDeviceTest.DEBUG_SERVER_NAME,
                        url = mcpServer.url(),
                    ),
                )

                try {
                    base.evaluate()
                } finally {
                    restore(ctx, settings)
                }
            }

            private fun restore(ctx: Context, settings: Settings) {
                runCatching {
                    apiKey?.let { settings.openAiApiKey = it }
                    baseUrl?.let { settings.openAiBaseUrl = it }
                    model?.let { settings.model = it }
                    systemPrompt?.let { settings.systemPrompt = it }
                    toolsEnabled?.let { settings.toolsEnabled = it }
                    confirmToolCalls?.let { settings.confirmToolCalls = it }
                    settings.activeServerId = activeServerId
                    settings.saveServers(serversBackup)

                    val db = ChatDatabase(ctx)
                    SqliteToolApprovalStore(db)
                        .revoke(ToolApprovalFlowDeviceTest.DEBUG_SERVER_ID, "echo")
                    db.allConversations()
                        .filter { it.id !in conversationsBefore }
                        .forEach { db.deleteConversation(it.id) }
                }
                runCatching { mcpServer.stop() }
                runCatching { openaiServer.stop() }
            }
        }
}
