package androidx.paging.compose

import app.cash.molecule.DisplayLinkClock
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

internal actual val mainDispatcher: CoroutineContext
    get() = Dispatchers.Main + DisplayLinkClock
