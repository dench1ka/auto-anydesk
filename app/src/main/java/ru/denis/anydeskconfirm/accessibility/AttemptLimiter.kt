package ru.denis.anydeskconfirm.accessibility

/** Main-thread state. Window IDs identify accessibility windows, not their text. */
class AttemptLimiter {
    companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_MS = 700L
        const val LIFETIME_MS = 8_000L
    }
    private data class State(val firstSeen: Long, var attempts: Int = 0, var lastAttempt: Long? = null,
                             var accepted: Boolean = false, var rememberAttempted: Boolean = false)
    private val states = LinkedHashMap<Int, State>()
    private var lastGlobalAttempt: Long? = null

    /** Returns true only on first detection of this window. No time-based rearming. */
    fun observe(windowId: Int, now: Long): Boolean {
        if (states.containsKey(windowId)) return false
        if (states.size >= 128) states.remove(states.keys.first())
        states[windowId] = State(now)
        return true
    }

    fun canAttempt(windowId: Int, now: Long, enabled: Boolean): Boolean {
        val state = states[windowId] ?: return false
        return enabled && !state.accepted && state.attempts < MAX_ATTEMPTS &&
            now - state.firstSeen in 0..LIFETIME_MS &&
            (lastGlobalAttempt == null || now - lastGlobalAttempt!! >= RETRY_MS) &&
            (state.lastAttempt == null || now - state.lastAttempt!! >= RETRY_MS)
    }

    fun needsRemember(windowId: Int): Boolean = states[windowId]?.rememberAttempted == false
    fun markRemember(windowId: Int) { states[windowId]?.rememberAttempted = true }

    fun record(windowId: Int, now: Long, accepted: Boolean) {
        val state = states[windowId] ?: return
        state.attempts++
        state.lastAttempt = now
        state.accepted = accepted
        lastGlobalAttempt = now
    }

    fun retryAllowed(windowId: Int, now: Long): Boolean {
        val state = states[windowId] ?: return false
        return !state.accepted && state.attempts < MAX_ATTEMPTS && now - state.firstSeen in 0..LIFETIME_MS
    }
}
