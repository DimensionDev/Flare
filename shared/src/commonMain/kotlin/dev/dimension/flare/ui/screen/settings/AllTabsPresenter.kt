package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.platform.RssTimelineSpecs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.list.PinnableTimelineTabPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class AllTabsPresenter(
//    private val filterIsTimeline: Boolean = false,
) : PresenterBase<AllTabsPresenter.State>(),
    KoinComponent {
    private val timelineResolver: TimelineResolver by inject()

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

        val rssSources =
            remember {
                RssSourcesPresenter()
            }.body()
        val rssTabs =
            remember(rssSources.sources) {
                (
                    listOfNotNull(
                        if (rssSources.sources.isNotEmpty()) {
                            timelineResolver.toTabItem(
                                RssTimelineSpecs.allRss.target(
                                    data = RssTimelineSpecs.AllRssData,
                                ),
                            )
                        } else {
                            null
                        },
                    ) +
                        rssSources.sources.map { source -> source.toTimelineTabItemV2(timelineResolver) }
                ).toImmutableList()
            }

        return object : State {
            override val accountTabs = accountTabs
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
    internal interface State {
        val rssTabs: ImmutableList<TimelineTabItemV2>
        val accountTabs: UiState<ImmutableList<AccountTabs>>

        @Immutable
        public data class AccountTabs(
            val profile: UiProfile,
            val tabs: ImmutableList<PinnableTimelineTabPresenter.PinnableTimelineTab>,
        )
    }
}

private fun UiRssSource.toTimelineTabItemV2(timelineResolver: TimelineResolver): TimelineTabItemV2 {
    val title = UiText.Raw(title ?: url)
    val icon =
        favIcon?.let { IconType.Url(it) }
            ?: if (type == SubscriptionType.RSS) {
                IconType.Material(UiIcon.Rss)
            } else {
                IconType.FavIcon(host)
            }
    val source =
        if (type == SubscriptionType.RSS) {
            RssTimelineSpecs.rss.target(
                data = RssTimelineSpecs.RssData(url),
                title = title,
                icon = icon,
            )
        } else {
            RssTimelineSpecs.subscription.target(
                data =
                    RssTimelineSpecs.SubscriptionData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                title = title,
                icon = icon,
            )
        }
    return timelineResolver.toTabItem(source)
}
