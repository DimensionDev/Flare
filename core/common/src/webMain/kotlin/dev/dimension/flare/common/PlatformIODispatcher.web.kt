package dev.dimension.flare.common

import kotlinx.coroutines.Dispatchers

public actual val Dispatchers.PlatformIO: kotlinx.coroutines.CoroutineDispatcher
    get() = Dispatchers.Default
