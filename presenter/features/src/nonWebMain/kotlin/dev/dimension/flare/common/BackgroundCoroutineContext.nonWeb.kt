package dev.dimension.flare.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val backgroundCoroutineContext: CoroutineContext = Dispatchers.IO
