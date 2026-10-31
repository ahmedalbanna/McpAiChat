package com.mcpai.chat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side tests for [SqliteToolApprovalStore].
 *
 * The approval rules live in a SQLite table with a migration behind them, so
 * the schema has to be checked on a real SQLite, not on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ToolApprovalStoreDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: ChatDatabase
    private lateinit var store: ToolApprovalStore

    /** Unique per test so a run never collides with the device's own rules. */
    private val serverId = "test-server-${System.nanoTime()}"

    @Before
    fun setUp() {
        db = ChatDatabase(context)
        store = SqliteToolApprovalStore(db)
    }

    @After
    fun tearDown() {
        store.revoke(serverId, "echo")
        store.revoke(serverId, "add")
        db.close()
    }

    @Test
    fun anUnknownToolIsNotAutoApproved() {
        assertFalse(store.isAutoApproved(serverId, "echo"))
    }

    @Test
    fun autoApprovalRoundTrips() {
        store.setAutoApproved(serverId, "echo", true)
        assertTrue(store.isAutoApproved(serverId, "echo"))
    }

    @Test
    fun revocationClearsTheApproval() {
        store.setAutoApproved(serverId, "echo", true)
        store.revoke(serverId, "echo")
        assertFalse(store.isAutoApproved(serverId, "echo"))
    }

    @Test
    fun approvalsAreScopedToTheTool() {
        store.setAutoApproved(serverId, "echo", true)
        assertFalse(store.isAutoApproved(serverId, "add"))
    }

    @Test
    fun approvalsAreListedForTheirServerOnly() {
        store.setAutoApproved(serverId, "echo", true)
        store.setAutoApproved(serverId, "add", true)
        val listed = store.allForServer(serverId)
        assertEquals(listOf("add", "echo"), listed.map { it.toolName })
        assertTrue(listed.all { it.isAutoApproved })
        assertTrue(store.allForServer("some-other-server").none { it.serverId == serverId })
    }
}
