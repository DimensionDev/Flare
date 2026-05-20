package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val cacheDataDispatcher: CoroutineDispatcher = Dispatchers.Default
