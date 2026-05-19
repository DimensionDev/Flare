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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun <T> CacheData<T>.collectAsState(): CacheableState<T> {
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

internal class CacheableState<T>(
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
            data =
                when (it) {
                    is CacheState.Empty -> null
                    is CacheState.Success -> it.data
                }
        }
    }
}

@Composable
internal fun <T : Any> UiState<CacheData<ImmutableList<T>>>.toPagingState(): PagingState<T> =
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
