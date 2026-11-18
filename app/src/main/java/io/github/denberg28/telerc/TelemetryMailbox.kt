package io.github.denberg28.telerc

import java.util.ArrayDeque

/** USB receive batches must not renew heartbeat freshness long after their arrival. */
class TelemetryMailbox {
    // Connection — Receive backlog. Bound memory and scheduling delay, not the radio watchdog.
    companion object { const val MAX_FRAMES = 16; const val MAX_AGE_MS = 250L }
    private data class Entry(val bytes: ByteArray, val at: Long)
    private val entries = ArrayDeque<Entry>()
    fun offer(frames: List<ByteArray>, now: Long) {
        frames.forEach {
            if (entries.size == MAX_FRAMES) entries.removeFirst()
            entries.addLast(Entry(it.copyOf(), now))
        }
    }
    fun poll(now: Long): ByteArray? {
        while (entries.isNotEmpty()) {
            val entry = entries.removeFirst()
            if (now - entry.at in 0..MAX_AGE_MS) return entry.bytes
        }
        return null
    }
}
