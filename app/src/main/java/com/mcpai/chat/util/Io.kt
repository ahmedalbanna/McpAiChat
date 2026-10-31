package com.mcpai.chat.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads the whole stream as UTF-8.
 *
 * `InputStream.readText()` is a Kotlin extension that is not resolvable with the
 * toolchain used by this build, and `readAllBytes()` needs API 33, so the read
 * is done explicitly.
 */
fun InputStream.readUtf8(): String {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        out.write(chunk, 0, read)
    }
    return out.toString("UTF-8")
}
