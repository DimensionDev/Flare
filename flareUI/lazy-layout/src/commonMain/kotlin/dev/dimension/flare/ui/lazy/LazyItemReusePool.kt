package dev.dimension.flare.ui.lazy

/** A stable-key-aware reuse pool shared by the adaptive Apple renderers. */
internal class LazyItemReusePool<T>(
    maxSize: Int,
    private val onEvicted: (T) -> Unit,
) {
    private data class Entry<T>(
        val contentType: Any,
        val value: T,
    )

    private val entries = linkedMapOf<Any, Entry<T>>()
    private var maxSize = maxSize

    init {
        require(maxSize >= 0) { "Lazy item reuse pool size must be non-negative." }
    }

    val size: Int
        get() = entries.size

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
        entries.remove(key)?.let { onEvicted(it.value) }
        entries[key] = Entry(contentType, value)
        trimToSize()
    }

    private fun trimToSize() {
        while (entries.size > maxSize) {
            val oldest = entries.entries.first()
            val key = oldest.key
            val value = oldest.value.value
            entries.remove(key)
            onEvicted(value)
        }
    }

    fun take(
        contentType: Any,
        key: Any,
    ): T? {
        val exact = entries[key]
        if (exact != null) {
            entries.remove(key)
            if (exact.contentType == contentType) return exact.value
            onEvicted(exact.value)
        }
        val fallback = entries.entries.lastOrNull { it.value.contentType == contentType } ?: return null
        val fallbackKey = fallback.key
        val fallbackValue = fallback.value.value
        entries.remove(fallbackKey)
        return fallbackValue
    }

    fun clear() {
        entries.clear()
    }
}
