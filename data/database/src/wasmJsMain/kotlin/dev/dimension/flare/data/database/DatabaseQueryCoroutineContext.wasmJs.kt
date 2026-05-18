package dev.dimension.flare.data.database

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryCoroutineContext: CoroutineContext = Dispatchers.Default
