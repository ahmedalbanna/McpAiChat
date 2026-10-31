package com.mcpai.chat.llm

import com.mcpai.chat.mcp.McpTool
import com.mcpai.chat.util.readUtf8
import com.mcpai.chat.util.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat-completions provider with Server-Sent-Events streaming
 * and native tool/function calling.
 *
 * The same code also works against OpenAI-compatible gateways (Ollama's
 * `/v1` shim, OpenRouter, LiteLLM, vLLM, Together, Groq…) by changing the base URL.
 */
class OpenAiProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 180_000,
) {

    /**
     * Streams one assistant turn.
     *
     * @param messages  conversation so far, in OpenAI wire format
     * @param tools     MCP tools exposed to the model
     * @param onEvent   receives text / tool-call chunks as they arrive
     */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        tools: List<McpTool>,
        temperature: Double = 0.7,
        onEvent: suspend (LlmEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("temperature", temperature)
            put("messages", JSONArray().also { arr -> messages.forEach { arr.put(it.toJson()) } })
            if (tools.isNotEmpty()) {
                put("tools", JSONArray().also { arr -> tools.forEach { arr.put(it.toOpenAiTool()) } })
                put("tool_choice", "auto")
            }
        }

        val url = URL(chatCompletionsUrl(baseUrl))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.readUtf8().orEmpty()
                onEvent(LlmEvent.Failed("HTTP $code: ${extractErrorMessage(err)}"))
                return@withContext
            }
            readSse(conn, onEvent)
            onEvent(LlmEvent.Done)
        } catch (e: Exception) {
            onEvent(LlmEvent.Failed(e.message ?: "network error"))
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun readSse(conn: HttpURLConnection, onEvent: suspend (LlmEvent) -> Unit) {
        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                parseChunk(json, onEvent)
            }
        } finally {
            runCatching { reader.close() }
        }
    }

    private suspend fun parseChunk(json: JSONObject, onEvent: suspend (LlmEvent) -> Unit) {
        json.optJSONObject("usage")?.let { u ->
            if (u.has("prompt_tokens") || u.has("completion_tokens")) {
                onEvent(LlmEvent.Usage(u.optInt("prompt_tokens"), u.optInt("completion_tokens")))
            }
        }
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
        val delta = choice.optJSONObject("delta") ?: return

        delta.text("content")?.let {
            onEvent(LlmEvent.Text(it))
        }

        // Reasoning models stream their thinking trace next to (or instead of) the
        // answer. Different providers use different field names for it.
        val reasoning = delta.text("reasoning") ?: delta.text("reasoning_content")
        if (reasoning != null) {
            onEvent(LlmEvent.Reasoning(reasoning))
        }

        val toolCalls = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until toolCalls.length()) {
            val tc = toolCalls.optJSONObject(i) ?: continue
            val fn = tc.optJSONObject("function")
            onEvent(
                LlmEvent.ToolCallChunk(
                    index = tc.optInt("index", i),
                    id = tc.text("id"),
                    name = fn?.text("name"),
                    argumentsFragment = fn?.text("arguments").orEmpty(),
                ),
            )
        }
    }

    /**
     * Lists the models the provider exposes, so the settings screen can offer a
     * picker instead of a free-text field.
     */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(modelsUrl(baseUrl))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext emptyList()
                val body = conn.inputStream.readUtf8()
                val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.text("id") }
                    .filter { it.isNotBlank() }
                    .sorted()
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun extractErrorMessage(raw: String): String {
        if (raw.isBlank()) return "no response body"
        return runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("error")?.text("message")
                ?: json.text("message")
                ?: raw.take(300)
        }.getOrDefault(raw.take(300))
    }

    companion object {
        /** Builds `<base>/chat/completions`, tolerating a base that already ends in `/v1`. */
        fun chatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }

        /** Builds `<base>/models`. */
        fun modelsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/models")) trimmed else "$trimmed/models"
        }
    }
}
