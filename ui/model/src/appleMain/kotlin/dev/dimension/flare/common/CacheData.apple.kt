package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val cacheDataDispatcher: CoroutineDispatcher = Dispatchers.IO
