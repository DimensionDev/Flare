package androidx.paging.compose

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val mainDispatcher: CoroutineContext
    get() = Dispatchers.Main
