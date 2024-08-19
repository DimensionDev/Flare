package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.RecommendInstancePagingSource
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull

class NodeInfoPresenter : PresenterBase<NodeInfoState>() {
    @OptIn(FlowPreview::class)
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
                        config = PagingConfig(pageSize = 20),
                    ) {
                        RecommendInstancePagingSource()
                    }.flow.cachedIn(scope),
                    filterFlow,
                ) { pagingData, filter ->
                    pagingData.filter {
                        it.name.contains(filter, ignoreCase = true) ||
                            it.domain.contains(filter, ignoreCase = true)
                    }
                }
            }.collectAsLazyPagingItems()

        val detectedPlatformType =
            remember(filterFlow) {
                filterFlow.mapNotNull {
                    runCatching {
                        NodeInfoService.detectPlatformType(it)
                    }
                }
            }.collectAsUiState().value.flatMap {
                it.getOrNull()?.let {
                    UiState.Success(it)
                } ?: run {
                    it.exceptionOrNull()?.let { it1 -> UiState.Error(it1) }
                } ?: UiState.Loading()
            }

        return object : NodeInfoState {
            override val instances = instances.toPagingState()
            override val detectedPlatformType = detectedPlatformType
            override val canNext = detectedPlatformType is UiState.Success<PlatformType>

            override fun setFilter(value: String) {
                if (filter != value) {
                    filter = value
                }
            }
        }
    }
}

@Immutable
interface NodeInfoState {
    val instances: PagingState<UiInstance>
    val detectedPlatformType: UiState<PlatformType>
    val canNext: Boolean

    fun setFilter(value: String)
}
