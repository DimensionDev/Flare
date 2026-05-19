package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-aware dispatcher aliases for shared code.
 */
public expect object PlatformDispatchers {
    /**
     * Uses the real IO dispatcher on platforms that provide it, and a Web-safe dispatcher otherwise.
     */
    public val IO: CoroutineDispatcher
}
