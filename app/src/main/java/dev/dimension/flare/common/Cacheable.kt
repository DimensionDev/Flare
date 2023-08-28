package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform

fun <T> Cacheable(
    fetchSource: suspend () -> Unit,
    cacheSource: () -> Flow<T>,
) = CacheData(fetchSource, cacheSource)

class CacheData<T> internal constructor(
    private val fetchSource: suspend () -> Unit,
    private val cacheSource: () -> Flow<T>,
) {
    private val refreshFlow = MutableStateFlow(0)
    private val cacheFlow by lazy {
        cacheSource.invoke()
    }
    val refreshState: Flow<LoadState> = refreshFlow
        .transform {
            emit(LoadState.Loading)
            emit(
                try {
                    fetchSource.invoke()
                    LoadState.Success
                } catch (e: Throwable) {
                    LoadState.Error(e)
                },
            )
        }
        .catch { emit(LoadState.Error(it)) }

    val data: Flow<CacheState<T>> = cacheFlow
        .map<T, CacheState<T>> {
            CacheState.Success(it)
        }
        .onStart {
            emit(CacheState.Empty())
        }

    fun refresh() {
        refreshFlow.value++
    }
}

sealed interface CacheState<T> {
    class Empty<T> : CacheState<T>
    data class Success<T>(val data: T) : CacheState<T>
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Success : LoadState
    data class Error(val error: Throwable) : LoadState
}

@Composable
fun <T> CacheData<T>.collectAsState(): CacheableState<T> {
    val state = remember(this) { CacheableState(this) }

    LaunchedEffect(state) {
        state.collectData()
    }

    LaunchedEffect(state) {
        state.collectRefreshState()
    }

    return state
}

class CacheableState<T>(
    private val cacheData: CacheData<T>,
) {
    fun refresh() {
        cacheData.refresh()
    }

    var refreshState: LoadState by mutableStateOf(LoadState.Loading)
        private set

    var data: T? by mutableStateOf(null)
        private set

    internal suspend fun collectRefreshState() {
        cacheData.refreshState.collect { refreshState = it }
    }

    internal suspend fun collectData() {
        cacheData.data.collect {
            data = when (it) {
                is CacheState.Empty -> null
                is CacheState.Success -> it.data
            }
        }
    }
}
