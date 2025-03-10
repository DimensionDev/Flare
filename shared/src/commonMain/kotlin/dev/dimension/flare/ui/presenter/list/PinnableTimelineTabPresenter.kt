package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for pinnable lists.
 * Different from [AllListPresenter] in that it also includes Bluesky feeds.
 * This presenter should be used for managing pinnable tabs.
 */
public class PinnableTimelineTabPresenter(
    private val accountType: AccountType,
) : PresenterBase<PinnableTimelineTabPresenter.State>(),
    KoinComponent {
    public interface State {
        public sealed interface Tab {
            public val data: PagingState<UiList>

            public data class List(
                override val data: PagingState<UiList>,
            ) : Tab

            public data class Feed(
                override val data: PagingState<UiList>,
            ) : Tab
        }

        public val tabs: UiState<ImmutableListWrapper<Tab>>
    }

    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .mapNotNull {
                    it as? ListDataSource
                }.map { service ->
                    remember(service) {
                        service.myList(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()

        val feeds =
            serviceState
                .mapNotNull {
                    it as? BlueskyDataSource
                }.mapNotNull { service ->
                    remember(service) {
                        service.myFeeds
                    }
                }.toPagingState()

        val tabs =
            serviceState.map { service ->
                remember(
                    service,
                    items,
                    feeds,
                ) {
                    listOfNotNull(
                        if (service is BlueskyDataSource) {
                            State.Tab.Feed(feeds)
                        } else {
                            null
                        },
                        if (service is ListDataSource) {
                            State.Tab.List(items)
                        } else {
                            null
                        },
                    ).toImmutableList().toImmutableListWrapper()
                }
            }

        return object : State {
            override val tabs = tabs
        }
    }
}
