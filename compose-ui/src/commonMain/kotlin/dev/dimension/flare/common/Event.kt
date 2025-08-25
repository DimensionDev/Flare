package dev.dimension.flare.common

public class Event<out T>(
    private val content: T?,
    initialHandled: Boolean = false,
) {
    @Suppress("MemberVisibilityCanBePrivate")
    public var hasBeenHandled: Boolean = initialHandled
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    public fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
}
