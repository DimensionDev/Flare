package dev.dimension.flare.ui.lazy

/**
 * A bounded stable-key/content-type pool shared by the adaptive Apple renderers.
 *
 * Exact-key lookup and compatible LIFO fallback are both O(1) amortized. Type buckets retain lazy
 * tombstones after an exact-key take, then compact once those tombstones exceed a small bound.
 */
internal class LazyItemReusePool<T>(
    maxSize: Int,
    private val onEvicted: (T) -> Unit,
) {
    private class Entry<T>(
        val contentType: Any,
        val key: Any,
        val value: T,
        var active: Boolean = true,
    )

    private class TypeBucket<T> {
        var entries = ArrayDeque<Entry<T>>()
        var activeCount: Int = 0
    }

    private val entriesByKey = linkedMapOf<Any, Entry<T>>()
    private val entriesByType = mutableMapOf<Any, TypeBucket<T>>()
    private var maxSize = maxSize

    init {
        require(maxSize >= 0) { "Lazy item reuse pool size must be non-negative." }
    }

    val size: Int
        get() = entriesByKey.size

    fun resize(maxSize: Int) {
        require(maxSize >= 0) { "Lazy item reuse pool size must be non-negative." }
        this.maxSize = maxSize
        trimToSize()
    }

    fun put(
        contentType: Any,
        key: Any,
        value: T,
    ) {
        entriesByKey.remove(key)?.let { previous ->
            deactivate(previous)
            onEvicted(previous.value)
        }
        val entry = Entry(contentType, key, value)
        entriesByKey[key] = entry
        entriesByType.getOrPut(contentType, ::TypeBucket).apply {
            entries.addLast(entry)
            activeCount += 1
            compactIfNeeded()
        }
        trimToSize()
    }

    fun take(
        contentType: Any,
        key: Any,
    ): T? {
        entriesByKey.remove(key)?.let { exact ->
            deactivate(exact)
            if (exact.contentType == contentType) return exact.value
            onEvicted(exact.value)
        }

        val bucket = entriesByType[contentType] ?: return null
        while (bucket.entries.isNotEmpty()) {
            val fallback = bucket.entries.removeLast()
            if (!fallback.active) continue
            check(entriesByKey.remove(fallback.key) === fallback) {
                "Lazy item reuse pool indices diverged for key ${fallback.key}."
            }
            deactivate(fallback)
            return fallback.value
        }
        error("Lazy item reuse pool type bucket lost its active entries.")
    }

    fun clear() {
        val retained = entriesByKey.values.map(Entry<T>::value)
        entriesByKey.clear()
        entriesByType.clear()
        evictAll(retained)
    }

    private fun trimToSize() {
        var evicted: MutableList<T>? = null
        while (entriesByKey.size > maxSize) {
            val oldest = entriesByKey.entries.first()
            val oldestKey = oldest.key
            val oldestEntry = oldest.value
            entriesByKey.remove(oldestKey)
            deactivate(oldestEntry)
            if (evicted == null) evicted = mutableListOf()
            evicted += oldestEntry.value
        }
        evicted?.let(::evictAll)
    }

    private fun deactivate(entry: Entry<T>) {
        if (!entry.active) return
        entry.active = false
        val bucket = checkNotNull(entriesByType[entry.contentType])
        bucket.activeCount -= 1
        check(bucket.activeCount >= 0) { "Lazy item reuse pool type count became negative." }
        if (bucket.activeCount == 0) {
            entriesByType.remove(entry.contentType)
        } else {
            bucket.compactIfNeeded()
        }
    }

    private fun TypeBucket<T>.compactIfNeeded() {
        val retainedCapacity = maxOf(MIN_TYPE_BUCKET_CAPACITY, activeCount * 2)
        if (entries.size <= retainedCapacity) return
        val compacted = ArrayDeque<Entry<T>>()
        entries.forEach { entry ->
            if (entry.active) compacted.addLast(entry)
        }
        entries = compacted
    }

    private fun evictAll(values: List<T>) {
        var failure: Throwable? = null
        values.forEach { value ->
            try {
                onEvicted(value)
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        failure?.let { throw it }
    }
}

private const val MIN_TYPE_BUCKET_CAPACITY: Int = 16
