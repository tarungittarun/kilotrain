package io.opentrack.core.ntes

/** Minimal TTL cache (pure JVM, unit-tested). */
class TtlCache<K, V>(private val clock: () -> Long) {
    data class Entry<V>(val value: V, val storedAt: Long)

    private val entries = LinkedHashMap<K, Entry<V>>()

    /** Returns the value only while fresh; expired entries are evicted, never reused (3.4). */
    fun get(key: K, ttlMillis: Long): Entry<V>? {
        val now = clock()
        val entry = entries[key] ?: return null
        if (now - entry.storedAt > ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry
    }

    fun put(key: K, value: V) {
        entries[key] = Entry(value, clock())
    }

    fun clear() = entries.clear()
    fun size(): Int = entries.size
}
