package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import dev.dimension.flare.common.BasePagingSource
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.network.nodeinfo.NodeData
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@WebPresenter("loginServiceSelect")
public class NodeInfoPresenter : PresenterBase<NodeInfoState>() {
    private val loginPlatformRegistry: LoginPlatformRegistry by koinInject()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): NodeInfoState {
        val scope = rememberCoroutineScope()
        var filter by remember { mutableStateOf("") }
        val filterFlow =
            remember {
                snapshotFlow { filter }
                    .debounce(666L)
            }
        val instances =
            remember {
                combine(
                    Pager(
                        config = pagingConfig,
                    ) {
                        LoginRecommendInstancePagingSource(loginPlatformRegistry)
                    }.flow.cachedIn(scope),
                    filterFlow,
                ) { pagingData, filter ->
                    pagingData.filter {
                        it.name.contains(filter, ignoreCase = true) ||
                            it.domain.contains(filter, ignoreCase = true)
                    }
                }
            }.collectAsLazyPagingItems()

        val detectedPlatformType by remember(filterFlow) {
            filterFlow.flatMapLatest {
                flow {
                    runCatching {
                        emit(UiState.Loading())
                        loginPlatformRegistry.detectPlatformType(it)
                    }.onSuccess {
                        emit(UiState.Success(it))
                    }.onFailure {
                        emit(UiState.Error(it))
                    }
                }
            }
        }.collectAsState(UiState.Loading())

        return object : NodeInfoState {
            override val instances = instances.toPagingState()
            override val detectedPlatformType = detectedPlatformType
            override val canNext = detectedPlatformType.isSuccess

            override fun setFilter(value: String) {
                if (filter != value) {
                    filter = value
                }
            }
        }
    }
}

@Immutable
public interface NodeInfoState {
    public val instances: PagingState<UiInstance>
    public val detectedPlatformType: UiState<NodeData>
    public val canNext: Boolean

    public fun setFilter(value: String)
}

private class LoginRecommendInstancePagingSource(
    private val loginPlatformRegistry: LoginPlatformRegistry,
) : BasePagingSource<Int, UiInstance>() {
    override fun getRefreshKey(state: androidx.paging.PagingState<Int, UiInstance>): Int? = null

    override suspend fun doLoad(params: LoadParams<Int>): LoadResult<Int, UiInstance> {
        val recommendations =
            coroutineScope {
                loginPlatformRegistry.all
                    .map { provider ->
                        async {
                            tryRun {
                                provider.recommendInstances()
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll()
                    .flatten()
            }
        val instances =
            recommendations
                .sortedWith(
                    compareByDescending<RecommendedInstance> { it.priority }
                        .thenByDescending { it.instance.usersCount },
                ).distinctBy { it.instance.type to it.instance.domain }
                .map { it.instance }
        return LoadResult.Page(
            data = instances,
            prevKey = null,
            nextKey = null,
        )
    }
}
