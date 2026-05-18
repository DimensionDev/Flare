package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val debugRepositoryDispatcher: CoroutineDispatcher = Dispatchers.Default
