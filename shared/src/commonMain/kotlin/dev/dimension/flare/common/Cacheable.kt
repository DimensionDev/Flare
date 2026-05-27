package dev.dimension.flare.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class Cacheable<T>(
    fetchSource: suspend () -> Unit,
    cacheSource: () -> Flow<T>,
) : CacheData<T>(
        fetchSource = fetchSource,
        cacheSource = cacheSource,
    )

@Suppress("UNCHECKED_CAST")
@HiddenFromObjC
public class MemCacheable<T>(
    private val key: String,
    fetchSource: suspend () -> T,
) : CacheData<T>(
        fetchSource = {
            update(key, fetchSource.invoke())
        },
        cacheSource = {
            subscribe(key)
        },
    ) {
    public companion object {
        private val caches = mutableMapOf<String, MutableStateFlow<Any?>>()

        public fun <T> update(
            key: String,
            value: T,
        ) {
            caches[key]?.value = value
        }

        public fun <T> updateWith(
            key: String,
            update: (T) -> T,
        ) {
            if (caches.containsKey(key)) {
                caches[key]?.value = update(caches[key]?.value as T)
            }
        }

        public fun <T> subscribe(key: String): Flow<T> =
            caches
                .getOrPut(key) {
                    MutableStateFlow(null)
                }.filterNotNull() as Flow<T>
    }
}

@HiddenFromObjC
public sealed class CacheData<T>(
    private val fetchSource: suspend () -> Unit,
    private val cacheSource: () -> Flow<T>,
) {
    private val refreshFlow = MutableStateFlow(0)
    private val cacheFlow by lazy {
        cacheSource.invoke()
    }
    public val refreshState: Flow<LoadState> =
        refreshFlow
            .transform {
                emit(LoadState.Loading)
                emit(
                    try {
                        withContext(Dispatchers.IO) {
                            fetchSource.invoke()
                        }
                        LoadState.NotLoading(true)
                    } catch (e: Exception) {
                        LoadState.Error(e)
                    },
                )
            }.catch { emit(LoadState.Error(it)) }

    public val data: Flow<CacheState<T>> =
        cacheFlow
            .map<T, CacheState<T>> {
                CacheState.Success(it)
            }.onStart {
                emit(CacheState.Empty())
            }

    public fun refresh() {
        refreshFlow.value++
    }
}

@HiddenFromObjC
public sealed class CacheState<T> {
    public class Empty<T> : CacheState<T>()

    public data class Success<T>(
        val data: T,
    ) : CacheState<T>()
}

@Composable
@HiddenFromObjC
public fun <T> CacheData<T>.collectAsState(): CacheableState<T> {
    val state =
        remember(this) {
            CacheableState(this)
        }

    LaunchedEffect(state) {
        state.collectData()
    }

    LaunchedEffect(state) {
        state.collectRefreshState()
    }

    return state
}

@HiddenFromObjC
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

    internal suspend fun collectRefreshState() {
        cacheData.refreshState.collect { refreshState = it }
    }

    internal suspend fun collectData() {
        cacheData.data.collect {
            data =
                when (it) {
                    is CacheState.Empty -> null
                    is CacheState.Success -> it.data
                }
        }
    }
}

@Composable
@HiddenFromObjC
public fun <T : Any> UiState<CacheData<ImmutableList<T>>>.toPagingState(): PagingState<T> =
    when (this) {
        is UiState.Loading -> PagingState.Loading()
        is UiState.Error -> PagingState.Error(throwable, onRetry = {})
        is UiState.Success -> data.collectAsState().toPagingState()
    }

@Composable
private fun <T : Any> CacheableState<ImmutableList<T>>.toPagingState(): PagingState<T> {
    val data = data
    return if (data != null) {
        if (data.isNotEmpty()) {
            PagingState.Success.ImmutableSuccess(
                data = data,
                onRefresh = this::refresh,
                onRetry = this::refresh,
            )
        } else {
            PagingState.Empty(this::refresh)
        }
    } else if (refreshState is LoadState.Loading) {
        PagingState.Loading()
    } else if (refreshState is LoadState.Error) {
        PagingState.Error((refreshState as LoadState.Error).error, onRetry = { refresh() })
    } else {
        PagingState.Empty(this::refresh)
    }
}

internal fun <T : Any> PagingData.Companion.emptyFlow(isError: Boolean = false): Flow<PagingData<T>> =
    flowOf(
        PagingData.empty(
            sourceLoadStates =
                LoadStates(
                    refresh =
                        if (isError) {
                            LoadState.Error(Exception("Empty paging data"))
                        } else {
                            LoadState.NotLoading(endOfPaginationReached = true)
                        },
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = true),
                ),
        ),
    )
