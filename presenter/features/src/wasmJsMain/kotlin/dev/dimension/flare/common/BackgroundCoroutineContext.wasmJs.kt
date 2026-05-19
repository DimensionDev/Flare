package dev.dimension.flare.common

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val backgroundCoroutineContext: CoroutineContext = Dispatchers.Default
