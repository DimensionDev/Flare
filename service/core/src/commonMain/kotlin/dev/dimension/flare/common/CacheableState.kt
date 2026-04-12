package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.LoadState

@Composable
public fun <T> CacheData<T>.collectAsState(): CacheableState<T> {
    val state = CacheableState(this)

    LaunchedEffect(this) {
        state.collectData()
    }

    LaunchedEffect(this) {
        state.collectRefreshState()
    }

    return state
}

public class CacheableState<T>(
    private val cacheData: CacheData<T>,
) {
    public fun refresh() {
        cacheData.refresh()
    }

    public var refreshState: LoadState by mutableStateOf(LoadState.Loading)
        private set

    public var data: T? by mutableStateOf(null)
        private set

    public suspend fun collectRefreshState() {
        cacheData.refreshState.collect { refreshState = it }
    }

    public suspend fun collectData() {
        cacheData.data.collect {
            data =
                when (it) {
                    is CacheState.Empty -> null
                    is CacheState.Success -> it.data
                }
        }
    }
}
