package com.mcpai.chat.data

/**
 * A single tool's auto-approval state for a given server.
 */
data class ToolApproval(
    val serverId: String,
    val toolName: String,
    val isAutoApproved: Boolean,
)

/**
 * Stores and retrieves auto-approval decisions for MCP tools.
 *
 * Each decision is per (server, tool) pair. An auto-approved tool is executed
 * without showing the approval dialog to the user.
 */
interface ToolApprovalStore {
    /** Whether [toolName] on [serverId] is auto-approved. */
    fun isAutoApproved(serverId: String, toolName: String): Boolean

    /** Sets the auto-approval state for [toolName] on [serverId]. */
    fun setAutoApproved(serverId: String, toolName: String, approved: Boolean)

    /** Returns all tool approvals for [serverId]. */
    fun allForServer(serverId: String): List<ToolApproval>

    /** Removes the auto-approval entry for [toolName] on [serverId]. */
    fun revoke(serverId: String, toolName: String)
}

/** Production implementation backed by the `tool_approvals` SQLite table. */
class SqliteToolApprovalStore(
    private val db: ChatDatabase,
) : ToolApprovalStore {

    override fun isAutoApproved(serverId: String, toolName: String): Boolean {
        db.readableDatabase.query(
            "tool_approvals",
            arrayOf("is_auto_approved"),
            "server_id = ? AND tool_name = ?",
            arrayOf(serverId, toolName),
            null, null, null,
        ).use { c ->
            if (c.moveToFirst()) {
                return c.getInt(c.getColumnIndexOrThrow("is_auto_approved")) == 1
            }
        }
        return false
    }

    override fun setAutoApproved(serverId: String, toolName: String, approved: Boolean) {
        val values = android.content.ContentValues().apply {
            put("server_id", serverId)
            put("tool_name", toolName)
            put("is_auto_approved", if (approved) 1 else 0)
        }
        val updated = db.writableDatabase.update(
            "tool_approvals", values,
            "server_id = ? AND tool_name = ?", arrayOf(serverId, toolName),
        )
        if (updated == 0) {
            db.writableDatabase.insert("tool_approvals", null, values)
        }
    }

    override fun allForServer(serverId: String): List<ToolApproval> {
        val out = mutableListOf<ToolApproval>()
        db.readableDatabase.query(
            "tool_approvals",
            arrayOf("server_id", "tool_name", "is_auto_approved"),
            "server_id = ?",
            arrayOf(serverId),
            null, null, "tool_name ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out += ToolApproval(
                    serverId = c.getString(c.getColumnIndexOrThrow("server_id")),
                    toolName = c.getString(c.getColumnIndexOrThrow("tool_name")),
                    isAutoApproved = c.getInt(c.getColumnIndexOrThrow("is_auto_approved")) == 1,
                )
            }
        }
        return out
    }

    override fun revoke(serverId: String, toolName: String) {
        setAutoApproved(serverId, toolName, false)
    }
}

/** In-memory fake for JVM unit tests. */
class FakeToolApprovalStore : ToolApprovalStore {
    private val approvals = mutableMapOf<String, MutableMap<String, Boolean>>()

    override fun isAutoApproved(serverId: String, toolName: String): Boolean =
        approvals[serverId]?.get(toolName) ?: false

    override fun setAutoApproved(serverId: String, toolName: String, approved: Boolean) {
        approvals.getOrPut(serverId) { mutableMapOf() }[toolName] = approved
    }

    override fun allForServer(serverId: String): List<ToolApproval> =
        approvals[serverId]?.map { (name, approved) ->
            ToolApproval(serverId, name, approved)
        } ?: emptyList()

    override fun revoke(serverId: String, toolName: String) {
        setAutoApproved(serverId, toolName, false)
    }
}