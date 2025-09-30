package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

public class AllTabsPresenter(
    private val filterIsTimeline: Boolean = false,
) : PresenterBase<AllTabsPresenter.State>() {
    @Composable
    override fun body(): State {
        val accountState = remember { AccountsPresenter() }.body()
        val accountTabs =
            accountState.accounts.map {
                it
                    .toImmutableList()
                    .mapNotNull { it.second.takeSuccess() }
                    .map { user ->
                        val tabs =
                            remember(user.key) {
                                (
                                    TimelineTabItem.defaultPrimary(user) +
                                        TimelineTabItem.secondaryFor(
                                            user,
                                        )
                                ).let {
                                    if (filterIsTimeline) {
                                        it.filterIsInstance<TimelineTabItem>()
                                    } else {
                                        it
                                    }
                                }
                            }
                        val extraTabs = listTabPresenter(accountKey = user.key).tabs.takeSuccess()
                        State.AccountTabs(
                            profile = user,
                            tabs = tabs.toImmutableList(),
                            extraTabs = extraTabs?.toImmutableList() ?: persistentListOf(),
                        )
                    }.toImmutableList()
            }

        val rssTabs =
            remember {
                RssSourcesPresenter()
            }.body()

        return object : State {
            override val defaultTabs =
                TimelineTabItem.mainSidePanel
                    .let {
                        if (filterIsTimeline) {
                            it.filterIsInstance<TimelineTabItem>()
                        } else {
                            it
                        }
                    }.toImmutableList()
            override val accountTabs = accountTabs
            override val rssTabs = rssTabs.sources
        }
    }

    @Composable
    private fun listTabPresenter(accountKey: MicroBlogKey) =
        run {
            remember(accountKey) {
                PinnableTimelineTabPresenter(accountType = AccountType.Specific(accountKey))
            }.body()
        }

    public interface State {
        public val defaultTabs: ImmutableList<TabItem>
        public val rssTabs: PagingState<UiRssSource>
        public val accountTabs: UiState<ImmutableList<AccountTabs>>

        @Immutable
        public data class AccountTabs(
            val profile: UiProfile,
            val tabs: ImmutableList<TabItem>,
            val extraTabs: ImmutableList<PinnableTimelineTabPresenter.State.Tab>,
        )
    }
}
