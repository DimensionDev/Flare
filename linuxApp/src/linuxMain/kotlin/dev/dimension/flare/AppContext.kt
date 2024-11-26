package dev.dimension.flare

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal sealed interface AppContext {
    val coroutineScope: CoroutineScope
}

internal data class AppContextImpl(
    override val coroutineScope: CoroutineScope,
) : AppContext

internal fun <T> AppContext.observe(
    flow: Flow<T>,
    onEach: (T) -> Unit,
) = coroutineScope.launch {
    flow.collect {
        onEach(it)
    }
}
