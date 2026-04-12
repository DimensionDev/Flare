package dev.dimension.flare.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

public actual val Dispatchers.PlatformIO: kotlinx.coroutines.CoroutineDispatcher
    get() = Dispatchers.IO
