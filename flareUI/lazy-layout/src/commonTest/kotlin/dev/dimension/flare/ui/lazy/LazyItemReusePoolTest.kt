package dev.dimension.flare.ui.lazy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        assertEquals(null, pool.take(contentType = "type-a", key = "key-a"))
    }

    @Test
    fun prefersTheStableKeyThenUsesACompatibleLifoFallback() {
        val evicted = mutableListOf<String>()
        val pool = LazyItemReusePool<String>(maxSize = 4, onEvicted = evicted::add)

        pool.put(contentType = "card", key = "one", value = "first")
        pool.put(contentType = "card", key = "two", value = "second")
        pool.put(contentType = "avatar", key = "three", value = "third")

        assertEquals("first", pool.take(contentType = "card", key = "one"))
        assertEquals("second", pool.take(contentType = "card", key = "missing"))
        assertEquals(null, pool.take(contentType = "card", key = "missing-again"))
        assertEquals(emptyList(), evicted)
    }

    @Test
    fun incompatibleExactKeyIsEvictedBeforeACompatibleFallbackIsReused() {
        val evicted = mutableListOf<String>()
        val pool = LazyItemReusePool<String>(maxSize = 4, onEvicted = evicted::add)
        pool.put(contentType = "card", key = "fallback", value = "card-root")
        pool.put(contentType = "avatar", key = "stable", value = "avatar-root")

        assertEquals("card-root", pool.take(contentType = "card", key = "stable"))
        assertEquals(listOf("avatar-root"), evicted)
        assertEquals(0, pool.size)
    }

    @Test
    fun clearEvictsEveryRetainedValue() {
        val evicted = mutableListOf<String>()
        val pool = LazyItemReusePool<String>(maxSize = 3, onEvicted = evicted::add)
        pool.put(contentType = "row", key = 1, value = "one")
        pool.put(contentType = "row", key = 2, value = "two")

        pool.clear()

        assertEquals(0, pool.size)
        assertEquals(listOf("one", "two"), evicted)
    }

    @Test
    fun clearAttemptsEveryEvictionBeforeRethrowing() {
        val evicted = mutableListOf<String>()
        val pool =
            LazyItemReusePool<String>(maxSize = 3) { value ->
                evicted += value
                if (value == "one") error("dispose failed")
            }
        pool.put(contentType = "row", key = 1, value = "one")
        pool.put(contentType = "row", key = 2, value = "two")

        assertFailsWith<IllegalStateException> { pool.clear() }

        assertEquals(0, pool.size)
        assertEquals(listOf("one", "two"), evicted)
    }
}
