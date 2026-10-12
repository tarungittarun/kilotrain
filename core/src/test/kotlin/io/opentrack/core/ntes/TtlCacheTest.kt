package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {

    @Test
    fun freshEntryIsReturned() {
        var now = 1_000L
        val cache = TtlCache<String, String>({ now })
        cache.put("k", "v")
        now += 89_000
        assertEquals("v", cache.get("k", 90_000)?.value)
    }

    @Test
    fun expiredEntryIsNeverReturned() {
        var now = 1_000L
        val cache = TtlCache<String, String>({ now })
        cache.put("k", "v")
        now += 90_001
        assertNull(cache.get("k", 90_000))
        assertEquals(0, cache.size())
    }

    @Test
    fun aRepeatedKeyRefreshes() {
        var now = 1_000L
        val cache = TtlCache<String, String>({ now })
        cache.put("k", "v1")
        now += 30_000
        cache.put("k", "v2")
        now += 30_000
        assertEquals("v2", cache.get("k", 90_000)?.value)
    }
}
