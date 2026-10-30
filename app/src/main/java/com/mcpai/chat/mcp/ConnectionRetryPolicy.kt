package com.mcpai.chat.mcp

/**
 * Encapsulates the "single retry on failure" policy used when connecting to
 * MCP servers.
 *
 * The policy attempts the given [block] up to [maxAttempts] times. On the
 * first failure it retries once; on the second failure it gives up and
 * returns the last error.
 *
 * Pure Kotlin — no Android or JSON dependencies, fully testable on the JVM.
 */
class ConnectionRetryPolicy(
    private val maxAttempts: Int = 2,
) {
    /**
     * Executes [block] up to [maxAttempts] times, returning the result on
     * success or the last exception on failure.
     */
    suspend fun <T> execute(block: suspend () -> T): Result<T> {
        var lastError: Throwable? = null
        repeat(maxAttempts) {
            try {
                return Result.success(block())
            } catch (e: Throwable) {
                lastError = e
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Unknown error"))
    }

    /** Number of attempts that will be made. */
    val attempts: Int get() = maxAttempts
}