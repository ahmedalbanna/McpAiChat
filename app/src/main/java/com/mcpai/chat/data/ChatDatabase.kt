package com.mcpai.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local conversation store.
 *
 * Plain SQLite is used on purpose: it keeps the dependency graph small and the
 * build fast. The schema mirrors the message model of the reference apps
 * (role / content / status / tool metadata).
 */
class ChatDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL DEFAULT '',
                reasoning TEXT,
                status TEXT NOT NULL DEFAULT 'done',
                tool_name TEXT,
                tool_call_id TEXT,
                tool_args TEXT,
                tool_result TEXT,
                tool_calls TEXT,
                tool_error INTEGER NOT NULL DEFAULT 0,
                attachment_path TEXT,
                attachment_name TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_messages_conv ON messages(conversation_id)")

        // Tool auto-approval state (T-05): per (server, tool) pair.
        db.execSQL(
            """
            CREATE TABLE tool_approvals (
                server_id TEXT NOT NULL,
                tool_name TEXT NOT NULL,
                is_auto_approved INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (server_id, tool_name)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE messages ADD COLUMN attachment_path TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN attachment_name TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tool_approvals (
                    server_id TEXT NOT NULL,
                    tool_name TEXT NOT NULL,
                    is_auto_approved INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (server_id, tool_name)
                )
                """.trimIndent(),
            )
        }
    }

    // ---- conversations -----------------------------------------------------

    fun createConversation(title: String = ""): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title)
            put("created_at", now)
            put("updated_at", now)
        }
        return writableDatabase.insert("conversations", null, values)
    }

    fun updateConversationTitle(id: Long, title: String) {
        val values = ContentValues().apply {
            put("title", title)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("conversations", values, "id = ?", arrayOf(id.toString()))
    }

    fun latestConversationId(): Long? {
        readableDatabase.query(
            "conversations", arrayOf("id"), null, null, null, null, "updated_at DESC", "1",
        ).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    /** All conversations, newest first, with last-message preview. */
    data class ConversationSummary(
        val id: Long,
        val title: String,
        val updatedAt: Long,
        val lastMessage: String?,
    )

    fun allConversations(): List<ConversationSummary> {
        val out = mutableListOf<ConversationSummary>()
        readableDatabase.query(
            "conversations", null, null, null, null, null, "updated_at DESC",
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow("id"))
                val title = c.getString(c.getColumnIndexOrThrow("title"))
                val updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
                // Last message preview from messages table.
                val preview = readableDatabase.query(
                    "messages", arrayOf("content"), "conversation_id = ?",
                    arrayOf(id.toString()), null, null, "id DESC", "1",
                ).use { mc ->
                    if (mc.moveToFirst()) mc.getString(mc.getColumnIndexOrThrow("content")) else null
                }
                out += ConversationSummary(id, title, updatedAt, preview)
            }
        }
        return out
    }

    fun touchConversation(id: Long, title: String?) {
        val values = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
            if (title != null) put("title", title)
        }
        writableDatabase.update("conversations", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteConversation(id: Long) {
        writableDatabase.delete("messages", "conversation_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("conversations", "id = ?", arrayOf(id.toString()))
    }

    /** True while the conversation row exists. */
    fun conversationExists(id: Long): Boolean {
        readableDatabase.query(
            "conversations", arrayOf("id"), "id = ?", arrayOf(id.toString()),
            null, null, null, "1",
        ).use { c ->
            return c.moveToFirst()
        }
    }

    /** The lowest message id in the conversation, or null when it is empty. */
    fun firstMessageId(conversationId: Long): Long? {
        readableDatabase.query(
            "messages", arrayOf("MIN(id)"), "conversation_id = ?",
            arrayOf(conversationId.toString()), null, null, null,
        ).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }

    /** Replaces the attachment path/name of a message. */
    fun updateAttachment(id: Long, path: String?, name: String?) {
        val values = ContentValues().apply {
            put("attachment_path", path)
            put("attachment_name", name)
        }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    // ---- messages -----------------------------------------------------------

    fun insertMessage(message: StoredMessage): Long {
        val values = ContentValues().apply {
            put("conversation_id", message.conversationId)
            put("role", message.role)
            put("content", message.content)
            put("reasoning", message.reasoning)
            put("status", message.status)
            put("tool_name", message.toolName)
            put("tool_call_id", message.toolCallId)
            put("tool_args", message.toolArgs)
            put("tool_result", message.toolResult)
            put("tool_calls", message.toolCallsJson)
            put("tool_error", if (message.toolError) 1 else 0)
            put("attachment_path", message.attachmentPath)
            put("attachment_name", message.attachmentName)
            put("created_at", message.createdAt)
        }
        val id = writableDatabase.insert("messages", null, values)
        touchConversation(message.conversationId, null)
        return id
    }

    fun updateMessage(id: Long, content: String, status: String, toolCallsJson: String? = null) {
        val values = ContentValues().apply {
            put("content", content)
            put("status", status)
            if (toolCallsJson != null) put("tool_calls", toolCallsJson)
        }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    /** Stores the reasoning trace of a streaming answer. */
    fun updateReasoning(id: Long, reasoning: String) {
        val values = ContentValues().apply { put("reasoning", reasoning) }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateToolResult(id: Long, result: String, isError: Boolean, status: String) {
        val values = ContentValues().apply {
            put("tool_result", result)
            put("tool_error", if (isError) 1 else 0)
            put("status", status)
        }
        writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    fun messages(conversationId: Long): List<StoredMessage> {
        val out = mutableListOf<StoredMessage>()
        readableDatabase.query(
            "messages", null, "conversation_id = ?", arrayOf(conversationId.toString()),
            null, null, "id ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out += cursorToMessage(c)
            }
        }
        return out
    }

    /** The last user message in the conversation, if any. Used to re-run the last turn. */
    fun lastUserMessage(conversationId: Long): StoredMessage? {
        readableDatabase.query(
            "messages", null,
            "conversation_id = ? AND role = ?",
            arrayOf(conversationId.toString(), StoredMessage.ROLE_USER),
            null, null, "id DESC", "1",
        ).use { c ->
            return if (c.moveToFirst()) cursorToMessage(c) else null
        }
    }

    /** Deletes every message after [afterId] in the conversation (re-run preparation). */
    fun deleteMessagesAfter(conversationId: Long, afterId: Long) {
        writableDatabase.delete(
            "messages", "conversation_id = ? AND id > ?",
            arrayOf(conversationId.toString(), afterId.toString()),
        )
        touchConversation(conversationId, null)
    }

    fun clearConversation(conversationId: Long) {
        writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId.toString()))
    }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun cursorToMessage(c: android.database.Cursor) = StoredMessage(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        conversationId = c.getLong(c.getColumnIndexOrThrow("conversation_id")),
        role = c.getString(c.getColumnIndexOrThrow("role")),
        content = c.getString(c.getColumnIndexOrThrow("content")),
        reasoning = c.getStringOrNull("reasoning"),
        status = c.getString(c.getColumnIndexOrThrow("status")),
        toolName = c.getStringOrNull("tool_name"),
        toolCallId = c.getStringOrNull("tool_call_id"),
        toolArgs = c.getStringOrNull("tool_args"),
        toolResult = c.getStringOrNull("tool_result"),
        toolCallsJson = c.getStringOrNull("tool_calls"),
        toolError = c.getInt(c.getColumnIndexOrThrow("tool_error")) == 1,
        attachmentPath = c.getStringOrNull("attachment_path"),
        attachmentName = c.getStringOrNull("attachment_name"),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
    )

    companion object {
        private const val DB_NAME = "mcp_chat.db"
        private const val DB_VERSION = 3
    }
}

/** A persisted chat message. */
data class StoredMessage(
    val id: Long = 0,
    val conversationId: Long,
    val role: String,
    val content: String = "",
    val reasoning: String? = null,
    val status: String = STATUS_DONE,
    val toolName: String? = null,
    val toolCallId: String? = null,
    val toolArgs: String? = null,
    val toolResult: String? = null,
    val toolCallsJson: String? = null,
    val toolError: Boolean = false,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"
        const val ROLE_SYSTEM = "system"

        const val STATUS_PENDING = "pending"
        const val STATUS_STREAMING = "streaming"
        const val STATUS_DONE = "done"
        const val STATUS_ERROR = "error"
        const val STATUS_DECLINED = "declined"
    }
}
