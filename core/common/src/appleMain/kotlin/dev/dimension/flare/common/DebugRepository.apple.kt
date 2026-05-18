package dev.dimension.flare.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val debugRepositoryDispatcher: CoroutineDispatcher = Dispatchers.IO
