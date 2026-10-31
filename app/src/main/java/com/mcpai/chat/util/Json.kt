package com.mcpai.chat.util

import org.json.JSONObject

/**
 * String helpers that treat an explicit JSON `null` as "absent".
 *
 * `JSONObject.optString(key)` returns the literal text `"null"` when the value
 * is JSON null, which leaks into the UI as visible `null` fragments. Providers
 * that stream reasoning models send `null` for fields they do not use, so every
 * optional string has to be read through these helpers.
 */
fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    if (value === JSONObject.NULL) return null
    val text = value.toString()
    return if (text == "null" || text.isEmpty()) null else text
}

/** Reads a non-blank string, or null. */
fun JSONObject.text(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }
