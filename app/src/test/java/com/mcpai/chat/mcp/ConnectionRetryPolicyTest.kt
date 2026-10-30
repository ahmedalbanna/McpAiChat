package com.mcpai.chat.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ConnectionRetryPolicy] — pure retry logic, no Android/JSON needed.
 */
class ConnectionRetryPolicyTest {

    @Test
    fun `succeeds on first attempt when no exception`() = runBlocking {
        var callCount = 0
        val policy = ConnectionRetryPolicy(maxAttempts = 2)
        val result = policy.execute {
            callCount++
            "ok"
        }
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun `retries once on failure and succeeds on second attempt`() = runBlocking {
        var callCount = 0
        val policy = ConnectionRetryPolicy(maxAttempts = 2)
        val result = policy.execute {
            callCount++
            if (callCount == 1) throw RuntimeException("first attempt fails")
            "recovered"
        }
        assertTrue(result.isSuccess)
        assertEquals("recovered", result.getOrNull())
        assertEquals(2, callCount)
    }

    @Test
    fun `does not retry more than maxAttempts times`() = runBlocking {
        var callCount = 0
        val policy = ConnectionRetryPolicy(maxAttempts = 2)
        val result = policy.execute {
            callCount++
            throw RuntimeException("always fails")
        }
        assertTrue(result.isFailure)
        assertEquals(2, callCount)
    }

    @Test
    fun `returns the last exception on failure`() = runBlocking {
        val policy = ConnectionRetryPolicy(maxAttempts = 2)
        val result = policy.execute<String> {
            throw IllegalStateException("boom")
        }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `maxAttempts of 1 means no retry`() = runBlocking {
        var callCount = 0
        val policy = ConnectionRetryPolicy(maxAttempts = 1)
        val result = policy.execute {
            callCount++
            throw RuntimeException("fail")
        }
        assertTrue(result.isFailure)
        assertEquals(1, callCount)
    }

    @Test
    fun `default maxAttempts is 2`() {
        val policy = ConnectionRetryPolicy()
        assertEquals(2, policy.attempts)
    }
}