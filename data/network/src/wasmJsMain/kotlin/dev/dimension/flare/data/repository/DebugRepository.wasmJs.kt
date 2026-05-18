package dev.dimension.flare.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val debugRepositoryDispatcher: CoroutineDispatcher = Dispatchers.Default
