package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher

public expect object PlatformDispatchers {
    public val IO: CoroutineDispatcher
}
