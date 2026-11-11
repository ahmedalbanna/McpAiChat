package com.mcpai.chat.engine

/**
 * Tracks which tools the user declined within one turn.
 *
 * A model that keeps asking for a declined tool would otherwise loop the
 * approval dialog; after the first decline the answer is fixed for the rest
 * of the turn. Extracted as a plain class so the decision is JVM-testable —
 * the engine above it only wires it in.
 */
class DeclinedToolGuard {

    private val declined = linkedSetOf<String>()

    /** True when this tool was already declined during this turn. */
    fun shouldSkip(name: String): Boolean = name in declined

    fun recordDecline(name: String) {
        declined += name
    }

    fun size(): Int = declined.size
}
