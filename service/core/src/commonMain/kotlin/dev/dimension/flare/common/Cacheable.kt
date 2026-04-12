package dev.dimension.flare.common

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext

public class Cacheable<T>(
    fetchSource: suspend () -> Unit,
    cacheSource: () -> Flow<T>,
) : CacheData<T>(
        fetchSource = fetchSource,
        cacheSource = cacheSource,
    )

@Suppress("UNCHECKED_CAST")
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
                        withContext(Dispatchers.PlatformIO) {
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

public sealed class CacheState<T> {
    public class Empty<T> : CacheState<T>()

    public data class Success<T>(
        val data: T,
    ) : CacheState<T>()
}

public fun <T : Any> PagingData.Companion.emptyFlow(isError: Boolean = false): Flow<PagingData<T>> =
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
