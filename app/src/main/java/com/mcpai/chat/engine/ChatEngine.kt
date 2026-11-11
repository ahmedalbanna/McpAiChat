package com.mcpai.chat.engine

import com.mcpai.chat.llm.ChatMessage
import com.mcpai.chat.llm.LlmEvent
import com.mcpai.chat.llm.OpenAiProvider
import com.mcpai.chat.llm.ToolCall
import com.mcpai.chat.mcp.McpRegistry
import com.mcpai.chat.mcp.McpTool
import org.json.JSONObject

/**
 * Drives one conversation turn: the model, the MCP tools, and the loop that
 * connects them.
 *
 * ```
 *  user text
 *     |
 *     v
 *  LLM stream --(no tool_calls)--> final answer
 *     |
 *     +--(tool_calls)--> MCP tools/call --> tool result --> back to the LLM
 * ```
 *
 * The loop is bounded by [maxToolIterations] so a confused model cannot spin.
 */
class ChatEngine(
    private val registry: McpRegistry,
    private val providerFactory: () -> OpenAiProvider?,
) {

    /** A tool call as it is presented to the UI. */
    data class ToolCallEvent(
        val id: String,
        val name: String,
        val arguments: String,
    )

    /** How a tool call ended. */
    enum class ToolOutcome { Completed, Failed, Declined }

    /** Outcome of a turn. */
    data class TurnResult(
        val finalText: String,
        val toolCalls: List<ToolCallEvent>,
        val error: String? = null,
        /** The assistant turn's `tool_calls` array as OpenAI wire JSON, if any. */
        val assistantToolCallsJson: String? = null,
    )

    suspend fun sendTurn(
        userText: String,
        history: List<ChatMessage>,
        tools: List<McpTool>,
        systemPrompt: String?,
        temperature: Double,
        onTextDelta: suspend (String) -> Unit,
        onReasoningDelta: suspend (String) -> Unit,
        onAssistantTurn: suspend (text: String, toolCallsJson: String?) -> Unit,
        onToolCall: suspend (ToolCallEvent) -> Unit,
        onToolResult: suspend (ToolCallEvent, String, ToolOutcome) -> Unit,
        onToolApproval: suspend (ToolCallEvent) -> Boolean,
        onStatus: suspend (String) -> Unit,
    ): TurnResult {
        val provider = providerFactory()
            ?: return TurnResult("", emptyList(), "No AI provider configured. Open Settings and set an API key.")

        val messages = mutableListOf<ChatMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            messages += ChatMessage(role = "system", content = systemPrompt)
        }
        messages += history
        messages += ChatMessage(role = "user", content = userText)

        val executedTools = mutableListOf<ToolCallEvent>()
        var finalText = ""
        var lastError: String? = null
        var assistantToolCallsJson: String? = null

        // Tools the user declined during this turn. A model that keeps asking
        // for a declined tool would otherwise loop the approval dialog; after
        // the first decline the answer is fixed for the rest of the turn.
        val declinedTools = DeclinedToolGuard()

        repeat(maxToolIterations) { iteration ->
            val assistantText = StringBuilder()
            val pending = linkedMapOf<Int, ToolCall>()

            provider.streamChat(
                messages = messages,
                tools = tools,
                temperature = temperature,
            ) { event ->
                when (event) {
                    is LlmEvent.Text -> {
                        assistantText.append(event.delta)
                        onTextDelta(event.delta)
                    }

                    is LlmEvent.Reasoning -> onReasoningDelta(event.delta)

                    is LlmEvent.ToolCallChunk -> {
                        val existing = pending[event.index]
                        val merged = if (existing == null) {
                            ToolCall(
                                id = event.id ?: "call_${event.index}_${System.nanoTime()}",
                                name = event.name.orEmpty(),
                                argumentsJson = event.argumentsFragment,
                            )
                        } else {
                            existing.copy(
                                id = event.id ?: existing.id,
                                name = event.name ?: existing.name,
                                argumentsJson = existing.argumentsJson + event.argumentsFragment,
                            )
                        }
                        pending[event.index] = merged
                    }

                    is LlmEvent.Failed -> lastError = event.message
                    is LlmEvent.Usage, LlmEvent.Done -> Unit
                }
            }

            if (lastError != null) {
                return TurnResult(finalText, executedTools, lastError, assistantToolCallsJson)
            }

            val calls = pending.values.filter { it.name.isNotBlank() }

            // One assistant turn = one provider response. It is reported before the
            // tools run so the conversation keeps the order
            //   assistant(tool_calls) -> tool result -> assistant(final answer).
            val assistantToolMessage = ChatMessage(
                role = "assistant",
                content = assistantText.toString(),
                toolCalls = calls,
            )
            val turnToolCallsJson = if (calls.isEmpty()) {
                null
            } else {
                assistantToolMessage.toJson().optJSONArray("tool_calls")?.toString()
            }
            onAssistantTurn(assistantText.toString(), turnToolCallsJson)
            assistantToolCallsJson = turnToolCallsJson

            if (calls.isEmpty()) {
                finalText = assistantText.toString()
                return TurnResult(finalText, executedTools, null, assistantToolCallsJson)
            }

            // The assistant turn that requested the tools has to stay in the history,
            // otherwise the provider rejects the following `tool` messages.
            messages += assistantToolMessage

            for (call in calls) {
                val uiEvent = ToolCallEvent(call.id, call.name, call.argumentsJson)
                onToolCall(uiEvent)

                var text: String
                var outcome: ToolOutcome

                if (declinedTools.shouldSkip(call.name)) {
                    text = "The user already declined to run this tool earlier in this turn."
                    outcome = ToolOutcome.Declined
                    onToolResult(uiEvent, text, outcome)
                    executedTools += uiEvent
                    messages += ChatMessage(
                        role = "tool",
                        content = text,
                        toolCallId = call.id,
                    )
                    continue
                }

                onStatus("Waiting for approval to run ${call.name}…")

                if (!onToolApproval(uiEvent)) {
                    // The model still needs a `tool` message with this id, and it needs
                    // to know the call did not happen so it does not assume a result.
                    text = "The user declined to run this tool."
                    outcome = ToolOutcome.Declined
                    declinedTools.recordDecline(call.name)
                } else {
                    onStatus("Calling ${call.name}…")
                    val result = runCatching { registry.callTool(call.name, call.parsedArguments()) }
                    if (result.isSuccess) {
                        text = result.getOrThrow().content
                        val failed = result.getOrThrow().isError
                        outcome = if (failed) ToolOutcome.Failed else ToolOutcome.Completed
                    } else {
                        text = "Tool call failed: ${result.exceptionOrNull()?.message}"
                        outcome = ToolOutcome.Failed
                    }
                }

                onToolResult(uiEvent, text, outcome)
                executedTools += uiEvent
                messages += ChatMessage(
                    role = "tool",
                    content = text,
                    toolCallId = call.id,
                )
            }

            if (iteration == maxToolIterations - 1) {
                finalText = "Reached the tool-call limit ($maxToolIterations) for this turn."
            }
        }

        return TurnResult(finalText, executedTools, lastError, assistantToolCallsJson)
    }

    companion object {
        const val maxToolIterations = 8
    }
}
