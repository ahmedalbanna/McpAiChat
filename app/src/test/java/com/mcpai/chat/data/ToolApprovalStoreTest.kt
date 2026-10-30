package com.mcpai.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ToolApprovalStore] — auto-approval state management.
 * Uses [FakeToolApprovalStore] (in-memory) so no Android/SQLite needed.
 */
class ToolApprovalStoreTest {

    private lateinit var store: ToolApprovalStore

    @Before
    fun setUp() {
        store = FakeToolApprovalStore()
    }

    @Test
    fun `isAutoApproved returns false by default`() {
        assertFalse(store.isAutoApproved("srv-1", "add"))
    }

    @Test
    fun `setAutoApproved true then isAutoApproved returns true`() {
        store.setAutoApproved("srv-1", "add", true)
        assertTrue(store.isAutoApproved("srv-1", "add"))
    }

    @Test
    fun `setAutoApproved false revokes auto-approval`() {
        store.setAutoApproved("srv-1", "add", true)
        store.setAutoApproved("srv-1", "add", false)
        assertFalse(store.isAutoApproved("srv-1", "add"))
    }

    @Test
    fun `auto-approval is per tool per server`() {
        store.setAutoApproved("srv-1", "add", true)
        assertFalse(store.isAutoApproved("srv-1", "echo"))
        assertFalse(store.isAutoApproved("srv-2", "add"))
        assertTrue(store.isAutoApproved("srv-1", "add"))
    }

    @Test
    fun `allForServer returns only that server's approvals`() {
        store.setAutoApproved("srv-1", "add", true)
        store.setAutoApproved("srv-1", "echo", true)
        store.setAutoApproved("srv-2", "add", true)

        val srv1 = store.allForServer("srv-1")
        assertEquals(2, srv1.size)
        assertTrue(srv1.all { it.serverId == "srv-1" })

        val srv2 = store.allForServer("srv-2")
        assertEquals(1, srv2.size)
    }

    @Test
    fun `revoke removes auto-approval for specific tool`() {
        store.setAutoApproved("srv-1", "add", true)
        store.setAutoApproved("srv-1", "echo", true)

        store.revoke("srv-1", "add")

        assertFalse(store.isAutoApproved("srv-1", "add"))
        assertTrue(store.isAutoApproved("srv-1", "echo"))
    }

    @Test
    fun `allForServer returns empty when no approvals`() {
        assertTrue(store.allForServer("srv-1").isEmpty())
    }
}