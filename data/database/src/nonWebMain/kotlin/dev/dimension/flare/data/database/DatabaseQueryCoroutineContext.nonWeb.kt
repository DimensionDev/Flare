package dev.dimension.flare.data.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.coroutines.CoroutineContext

internal actual val databaseQueryCoroutineContext: CoroutineContext = Dispatchers.IO
