package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.ListDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for pinnable lists.
 * Different from [AllListPresenter] in that it also includes Bluesky feeds.
 * This presenter should be used for managing pinnable tabs.
 */
public class PinnableListPresenter(
    private val accountType: AccountType,
) : PresenterBase<PinnableListState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): PinnableListState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .mapNotNull { service ->
                    remember(service) {
                        service
                            .takeIf {
                                it is ListDataSource
                            }?.let {
                                it as ListDataSource
                            }?.myList
                    }?.collectAsState()?.toUi()
                }.flatMap { it }

        val bsky =
            serviceState
                .mapNotNull { service ->
                    remember(service) {
                        service
                            .takeIf {
                                it is BlueskyDataSource
                            }?.let {
                                it as BlueskyDataSource
                            }?.myFeeds
                    }?.collectAsState()?.toUi()
                }.flatMap { it }

        val result =
            if (bsky is UiState.Success) {
                items.map {
                    it + bsky.data
                }
            } else {
                items
            }

        return object : PinnableListState {
            override val items =
                result.map {
                    it.toImmutableList()
                }
        }
    }
}

@Immutable
public interface PinnableListState {
    public val items: UiState<ImmutableList<UiList>>
}
