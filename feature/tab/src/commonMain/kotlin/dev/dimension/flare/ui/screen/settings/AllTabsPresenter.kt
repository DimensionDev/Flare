package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.AllRssTimelineData
import dev.dimension.flare.data.model.tab.RssTimelineData
import dev.dimension.flare.data.model.tab.SubscriptionTimelineData
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@WebPresenter("allTabs")
public class AllTabsPresenter : PresenterBase<AllTabsPresenter.State>() {
    @Composable
    override fun body(): State {
        val accountState = remember { AccountsPresenter() }.body()
        val accountTabs =
            accountState.accounts.map {
                it
                    .toImmutableList()
                    .mapNotNull { it.profile.takeSuccess() }
                    .map { user ->
                        val tabs = listTabPresenter(accountKey = user.key).tabs.takeSuccess()
                        State.AccountTabs(
                            profile = user,
                            tabs = tabs?.toImmutableList() ?: persistentListOf(),
                        )
                    }.toImmutableList()
            }
        val flattenedAccountTabs =
            accountTabs.map { groups ->
                groups
                    .map { group ->
                        State.FlattenedAccountTabs(
                            profile = group.profile,
                            sections =
                                group.tabs
                                    .map { section ->
                                        State.FlattenedTabSection(
                                            title = section.title.asText(),
                                            data =
                                                when (val data = section.data) {
                                                    is dev.dimension.flare.common.PagingState.Success -> {
                                                        (0 until data.itemCount)
                                                            .mapNotNull { data.peek(it) }
                                                            .toImmutableList()
                                                    }

                                                    else -> {
                                                        persistentListOf()
                                                    }
                                                },
                                        )
                                    }.filter { section -> section.data.isNotEmpty() }
                                    .toImmutableList(),
                        )
                    }.filter { group -> group.sections.isNotEmpty() }
                    .toImmutableList()
            }

        val rssSources =
            remember {
                RssSourcesPresenter()
            }.body()
        val rssTabs =
            remember(rssSources.sources) {
                (
                    listOfNotNull(
                        if (rssSources.sources.isNotEmpty()) {
                            RssTimelineSpecs.allRss
                                .candidate(
                                    data = AllRssTimelineData,
                                ).toUiTimelineTabItem()
                        } else {
                            null
                        },
                    ) +
                        rssSources.sources.map { source -> source.toUiTimelineTabItem() }
                ).toImmutableList()
            }

        return object : State {
            override val accountTabs = accountTabs
            override val flattenedAccountTabs = flattenedAccountTabs
            override val rssTabs = rssTabs
        }
    }

    @Composable
    private fun listTabPresenter(accountKey: MicroBlogKey) =
        run {
            remember(accountKey) {
                PinnableTimelineTabPresenter(accountType = AccountType.Specific(accountKey))
            }.body()
        }

    @Immutable
    public interface State {
        public val rssTabs: ImmutableList<UiTimelineTabItem>
        public val accountTabs: UiState<ImmutableList<AccountTabs>>
        public val flattenedAccountTabs: UiState<ImmutableList<FlattenedAccountTabs>>

        @Immutable
        public data class AccountTabs(
            val profile: UiProfile,
            val tabs: ImmutableList<PinnableTimelineTabPresenter.PinnableTimelineTab>,
        )

        @Immutable
        public data class FlattenedAccountTabs(
            val profile: UiProfile,
            val sections: ImmutableList<FlattenedTabSection>,
        )

        @Immutable
        public data class FlattenedTabSection(
            val title: UiText,
            val data: ImmutableList<UiTimelineTabItem>,
        )
    }
}

private fun UiRssSource.toUiTimelineTabItem(): UiTimelineTabItem {
    val title = UiText.Raw(title ?: url)
    val icon =
        favIcon?.let { IconType.Url(it) }
            ?: if (type == SubscriptionType.RSS) {
                IconType.Material(UiIcon.Rss)
            } else {
                IconType.FavIcon(host)
            }
    return if (type == SubscriptionType.RSS) {
        RssTimelineSpecs.rss
            .candidate(
                data = RssTimelineData(url),
                title = title,
                icon = icon,
            ).toUiTimelineTabItem()
    } else {
        RssTimelineSpecs.subscription
            .candidate(
                data =
                    SubscriptionTimelineData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                title = title,
                icon = icon,
            ).toUiTimelineTabItem()
    }
}
