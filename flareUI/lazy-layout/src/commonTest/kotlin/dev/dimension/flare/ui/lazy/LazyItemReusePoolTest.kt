package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LazyItemReusePoolTest {
    @Test
    fun evictsOldestBindingsAcrossContentTypesAtTheGlobalLimit() {
        val evicted = mutableListOf<String>()
        val pool = LazyItemReusePool<String>(maxSize = 2, onEvicted = evicted::add)

        pool.put(contentType = "type-a", key = "key-a", value = "value-a")
        pool.put(contentType = "type-b", key = "key-b", value = "value-b")
        pool.put(contentType = "type-c", key = "key-c", value = "value-c")

        assertEquals(2, pool.size)
        assertEquals(listOf("value-a"), evicted)
        assertNull(pool.take(contentType = "type-a", key = "key-a"))
    }

    @Test
    fun prefersTheStableKeyAndDisposesAnIncompatibleMatch() {
        val evicted = mutableListOf<String>()
        val pool = LazyItemReusePool<String>(maxSize = 4, onEvicted = evicted::add)

        pool.put(contentType = "card", key = "one", value = "first")
        pool.put(contentType = "card", key = "two", value = "second")

        assertEquals("first", pool.take(contentType = "card", key = "one"))
        assertNull(pool.take(contentType = "avatar", key = "two"))
        assertEquals(listOf("second"), evicted)
    }
}
