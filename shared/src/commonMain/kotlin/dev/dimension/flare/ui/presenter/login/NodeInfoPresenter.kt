package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class NodeInfoPresenter : PresenterBase<NodeInfoState>() {
    @Composable
    override fun body(): NodeInfoState {
        val scope = rememberCoroutineScope()
        var filter by remember { mutableStateOf("") }
        val filterFlow =
            remember {
                snapshotFlow { filter }
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

        val detectedPlatformType by if (instances.itemCount == 1) {
            remember {
                derivedStateOf {
                    instances.takeIf { instances.itemCount > 0 }?.peek(0)?.let {
                        UiState.Success(it.type)
                    } ?: UiState.Error(Throwable("No instance found"))
                }
            }
        } else {
            remember(filter) {
                flow {
                    emit(NodeInfoService.detectPlatformType(filter))
                }
            }.collectAsUiState()
        }

        val canNext by remember(detectedPlatformType) {
            derivedStateOf {
                detectedPlatformType is UiState.Success<PlatformType>
            }
        }

        return object : NodeInfoState {
            override val instances = instances.toPagingState()
            override val detectedPlatformType = detectedPlatformType
            override val canNext = canNext

            override fun setFilter(value: String) {
                if (filter != value) {
                    filter = value
                }
            }
        }
    }
}

interface NodeInfoState {
    val instances: PagingState<UiInstance>
    val detectedPlatformType: UiState<PlatformType>
    val canNext: Boolean

    fun setFilter(value: String)
}
