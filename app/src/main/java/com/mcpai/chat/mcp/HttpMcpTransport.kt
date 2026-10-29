package com.mcpai.chat.mcp

import com.mcpai.chat.util.readUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * MCP "Streamable HTTP" transport.
 *
 * Speaks the HTTP flavour of the Model Context Protocol:
 *  - one POST per JSON-RPC message
 *  - `Accept: application/json, text/event-stream` so the server may answer either way
 *  - the `Mcp-Session-Id` header returned by `initialize` is echoed on every later call
 *  - notifications are answered with `202 Accepted` and no body
 *
 * No third-party HTTP dependency is used: [HttpURLConnection] is enough and keeps
 * the APK small.
 *
 * When [spkiPin] is set (trusted domain), the server's certificate SPKI hash is
 * verified after the TLS handshake. A mismatch throws [SpkiPinMismatchException].
 */
class HttpMcpTransport(
    internal val baseUrl: String,
    internal val headers: Map<String, String> = emptyMap(),
    internal val connectTimeoutMs: Int = 15_000,
    internal val readTimeoutMs: Int = 120_000,
    internal val spkiPin: String? = null,
) : McpTransport {

    override val displayName: String = baseUrl

    @Volatile
    var sessionId: String? = null
        private set

    override suspend fun send(message: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = URL(normaliseUrl(baseUrl))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json, text/event-stream")
            setRequestProperty("MCP-Protocol-Version", McpClient.PROTOCOL_VERSION)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
        }

        try {
            conn.outputStream.use { it.write(message.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            conn.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }

            if (code in 200..299) {
                // Verify SPKI pin before trusting the response body.
                spkiPin?.let { storedPin ->
                    val currentPin = extractSpkiPin(conn) ?: throw SpkiPinMismatchException(
                        stored = storedPin,
                        current = "<no certificate>",
                    )
                    val result = SpkiPinVerifier.verify(storedPin, currentPin)
                    if (result is SpkiPinResult.Mismatch) {
                        throw SpkiPinMismatchException(stored = result.stored, current = result.current)
                    }
                }

                val contentType = conn.contentType.orEmpty()
                if (contentType.contains("text/event-stream", ignoreCase = true)) {
                    readSseResponse(conn)
                } else {
                    val body = conn.inputStream.readUtf8()
                    if (body.isBlank()) JSONObject() else JSONObject(body)
                }
            } else {
                val err = conn.errorStream?.readUtf8().orEmpty()
                throw McpTransportException("HTTP $code from $baseUrl: ${err.take(400)}")
            }
        } catch (e: McpTransportException) {
            throw e
        } catch (e: Exception) {
            throw McpTransportException("Cannot reach MCP server $baseUrl: ${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Extracts the SPKI public-key hash (SHA-256 of SubjectPublicKeyInfo, base64-encoded
     * with `sha256:` prefix) from the server's TLS certificate. Returns null when the
     * connection is not HTTPS or no certificates are available.
     */
    private fun extractSpkiPin(conn: HttpURLConnection): String? {
        if (conn.url.protocol != "https") return null
        val httpsConn = conn as? HttpsURLConnection ?: return null
        val certs = httpsConn.serverCertificates
        if (certs.isEmpty()) return null
        val cert = certs[0]
        if (cert !is X509Certificate) return null
        val publicKey: java.security.PublicKey = cert.publicKey
        val spkiBytes = publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(spkiBytes)
        return "sha256:${Base64.getEncoder().encodeToString(hash)}"
    }

    /**
     * Reads an SSE stream until a JSON-RPC response for our request shows up.
     *
     * Server-initiated requests and notifications that appear in the stream are
     * skipped: this transport is used for request/response calls, and a stray
     * notification must not be mistaken for the reply.
     */
    private fun readSseResponse(conn: HttpURLConnection): JSONObject {
        val reader = BufferedReader(InputStreamReader(openStream(conn), Charsets.UTF_8))
        var result: JSONObject? = null
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                if (payload.isEmpty()) continue
                val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                if (json.has("result") || json.has("error")) {
                    result = json
                    break
                }
            }
        } finally {
            runCatching { reader.close() }
        }
        return result ?: JSONObject()
    }

    private fun openStream(conn: HttpURLConnection) =
        if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
            GZIPInputStream(conn.inputStream)
        } else {
            conn.inputStream
        }

    override fun close() {
        sessionId = null
    }

    companion object {
        /** Accepts `host:port`, `http://host:port` and full `/mcp` endpoints alike. */
        fun normaliseUrl(raw: String): String {
            val trimmed = raw.trim()
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            return if (withScheme.endsWith("/")) withScheme.dropLast(1) else withScheme
        }
    }
}

/** Transport-level failure (network, HTTP status, malformed reply). */
open class McpTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Thrown when the observed SPKI pin does not match the stored pin for a trusted domain. */
class SpkiPinMismatchException(
    val stored: String,
    val current: String,
) : McpTransportException(
    "SPKI pin mismatch for $current — stored: $stored. The server's certificate has changed.",
)