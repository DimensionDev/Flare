package dev.dimension.flare.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
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

public class AllTabsPresenter(
//    private val filterIsTimeline: Boolean = false,
) : PresenterBase<AllTabsPresenter.State>(),
    KoinComponent {
    private val timelinePersistenceMapper: TimelinePersistenceMapper by inject()

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
                            timelinePersistenceMapper.toTabItem(
                                RssTimelineSpecs.allRss.toTimelineTabDescriptor(
                                    data = RssTimelineSpecs.AllRssData,
                                ),
                            )
                        } else {
                            null
                        },
                    ) +
                        rssSources.sources.map { source -> source.toTimelineTabItemV2(timelinePersistenceMapper) }
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
    public interface State {
        public val rssTabs: ImmutableList<TimelineTabItemV2>
        public val accountTabs: UiState<ImmutableList<AccountTabs>>

        @Immutable
        public data class AccountTabs(
            val profile: UiProfile,
            val tabs: ImmutableList<PinnableTimelineTabPresenter.PinnableTimelineTab>,
        )
    }
}

private fun UiRssSource.toTimelineTabItemV2(timelinePersistenceMapper: TimelinePersistenceMapper): TimelineTabItemV2 {
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
            RssTimelineSpecs.rss.toTimelineTabDescriptor(
                data = RssTimelineSpecs.RssData(url),
                title = title,
                icon = icon,
            )
        } else {
            RssTimelineSpecs.subscription.toTimelineTabDescriptor(
                data =
                    RssTimelineSpecs.SubscriptionData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                title = title,
                icon = icon,
            )
        }
    return timelinePersistenceMapper.toTabItem(source)
}
