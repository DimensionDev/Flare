package dev.dimension.flare.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapper.renderVVOText
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map

class VVOStatusDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<VVOStatusDetailState>() {
    @Composable
    override fun body(): VVOStatusDetailState {
        val scope = rememberCoroutineScope()
        val service = accountServiceProvider(accountType)
        val status =
            service
                .flatMap {
                    remember(statusKey, accountType) {
                        it.status(statusKey)
                    }.collectAsState().toUi()
                }

        val extendedText =
            service.flatMap {
                require(it is VVODataSource)
                remember(statusKey, accountType) {
                    it.statusExtendedText(statusKey)
                }.collectAsState(UiState.Loading()).value
            }

        val actualStatus =
            status.flatMap { item ->
                service.map { service ->
                    require(service is VVODataSource)
                    when (extendedText) {
                        is UiState.Error -> item
                        is UiState.Loading -> item
                        is UiState.Success -> {
                            val content = item.content
                            if (content is UiTimeline.ItemContent.Status) {
                                item.copy(
                                    content =
                                        content.copy(
                                            content =
                                                renderVVOText(
                                                    extendedText.data,
                                                    service.accountKey,
                                                ).toUi(),
                                        ),
                                )
                            } else {
                                item
                            }
                        }
                    }
                }
            }

        val repost =
            service
                .map {
                    require(it is VVODataSource)
                    remember(statusKey, accountType) {
                        it.statusRepost(statusKey = statusKey, scope = scope).map {
                            it.map {
                                it.copy(
                                    content =
                                        (it.content as? UiTimeline.ItemContent.Status)?.copy(
                                            quote = persistentListOf(),
                                        ) ?: it.content,
                                )
                            }
                        }
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        val comment =
            service
                .map {
                    require(it is VVODataSource)
                    remember(statusKey, accountType) {
                        it.statusComment(statusKey = statusKey, scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        return object : VVOStatusDetailState {
            override val status = actualStatus
            override val comment = comment
            override val repost = repost
        }
    }
}

@Immutable
interface VVOStatusDetailState {
    val status: UiState<UiTimeline>
    val comment: PagingState<UiTimeline>
    val repost: PagingState<UiTimeline>
}
