package com.mcpai.chat.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * MCP "stdio" transport: launches a local process that speaks newline-delimited
 * JSON-RPC on stdin/stdout.
 *
 * Useful when the app runs on a device that also hosts an MCP server binary
 * (for example a rooted device or a Termux session) and when the same client
 * code is reused on a desktop JVM.
 */
class StdioMcpTransport(
    private val command: List<String>,
    private val workingDir: String? = null,
    private val environment: Map<String, String> = emptyMap(),
) : McpTransport {

    override val displayName: String = command.firstOrNull() ?: "stdio"

    private var process: Process? = null
    private var reader: BufferedReader? = null

    private fun ensureStarted(): Process {
        process?.let { if (it.isAlive) return it }
        val pb = ProcessBuilder(command).apply {
            if (workingDir != null) directory(java.io.File(workingDir))
        }
        pb.environment().putAll(environment)
        val p = pb.start()
        process = p
        reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
        return p
    }

    override suspend fun send(message: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val p = ensureStarted()
        p.outputStream.use { out ->
            out.write(message.toString().toByteArray(Charsets.UTF_8))
            out.write('\n'.code)
            out.flush()
        }
        val r = reader ?: throw McpTransportException("stdio reader unavailable")
        var answer: JSONObject? = null
        while (answer == null) {
            val line = r.readLine() ?: throw McpTransportException("stdio server closed the stream")
            if (line.isBlank()) continue
            val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (json.has("result") || json.has("error")) answer = json
        }
        answer
    }

    override fun close() {
        runCatching { process?.destroy() }
        process = null
        reader = null
    }
}
