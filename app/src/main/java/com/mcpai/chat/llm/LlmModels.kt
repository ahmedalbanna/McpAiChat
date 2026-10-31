package com.mcpai.chat.llm

import org.json.JSONArray
import org.json.JSONObject

/** A single message in the OpenAI chat-completions wire format. */
data class ChatMessage(
    val role: String,                 // system | user | assistant | tool
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        if (toolCallId != null) put("tool_call_id", toolCallId)
        if (toolCalls.isNotEmpty()) {
            put("tool_calls", JSONArray().also { arr ->
                toolCalls.forEach { tc ->
                    arr.put(JSONObject().apply {
                        put("id", tc.id)
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", tc.name)
                            put("arguments", tc.argumentsJson)
                        })
                    })
                }
            })
        }
    }
}

/** A tool invocation requested by the model. */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
) {
    fun parsedArguments(): JSONObject = runCatching { JSONObject(argumentsJson) }.getOrElse { JSONObject() }
}

/** Streaming events produced by the model provider. */
sealed interface LlmEvent {
    /** A chunk of assistant text. */
    data class Text(val delta: String) : LlmEvent

    /**
     * A chunk of the model's reasoning trace.
     *
     * Reasoning models (`reasoning: true`) stream their thinking separately from
     * the answer, in `delta.reasoning` or `delta.reasoning_content`.
     */
    data class Reasoning(val delta: String) : LlmEvent

    /** A partial tool call; `argumentsFragment` accumulates across chunks. */
    data class ToolCallChunk(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsFragment: String,
    ) : LlmEvent

    /** The provider reported token usage. */
    data class Usage(val promptTokens: Int, val completionTokens: Int) : LlmEvent

    /** The provider rejected or failed the request. */
    data class Failed(val message: String) : LlmEvent

    /** The stream ended normally. */
    data object Done : LlmEvent
}
