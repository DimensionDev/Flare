package dev.dimension.flare.common

class Event<out T>(
    private val content: T?,
    initialHandled: Boolean = false,
) {
    @Suppress("MemberVisibilityCanBePrivate")
    var hasBeenHandled = initialHandled
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
}
