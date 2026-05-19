package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

public actual object PlatformDispatchers {
    public actual val IO: CoroutineDispatcher = Dispatchers.IO
}
