package app.cash.paging.compose

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.CoroutineContext

internal actual val mainDispatcher: CoroutineContext = AndroidUiDispatcher.Main