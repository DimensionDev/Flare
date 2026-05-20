package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineShortcutDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabProvider
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SecondaryTabsPresenter :
    PresenterBase<SecondaryTabsPresenter.State>(),
    KoinComponent {
    @Immutable
    public interface State {
        public val items: UiState<ImmutableList<Item>>
    }

    @Immutable
    public class Item(
        public val user: UiState<UiProfile>,
        public val tabs: ImmutableList<Tab>,
    )

    @Immutable
    public data class Tab(
        val title: UiStrings,
        val icon: UiIcon,
        val destination: Destination,
    )

    @Immutable
    public sealed interface Destination {
        public data class Route(
            val route: DeeplinkRoute,
        ) : Destination

        public data class Timeline(
            val tabItem: TimelineTabItemV2,
        ) : Destination
    }

    private val accountRepository: AccountRepository by inject()
    private val timelinePersistenceMapper: TimelinePersistenceMapper by inject()

    private val itemsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services
                    .mapNotNull { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler
                                .userById(service.accountKey.id)
                                .toUi()
                                .distinctUntilChangedBy {
                                    it.takeSuccess()?.let {
                                        buildString {
                                            append(it.key)
                                            append("-")
                                            append(it.name.raw)
                                            append("-")
                                            append(it.avatar)
                                            append("-")
                                            append(it.handle.raw)
                                        }
                                    }
                                }.map { userState ->
                                    userState.takeSuccess()?.let { user ->
                                        Item(
                                            user = userState,
                                            tabs =
                                                (
                                                    listOf(
                                                        Tab(
                                                            title = UiStrings.Me,
                                                            icon = UiIcon.Profile,
                                                            destination =
                                                                Destination.Route(
                                                                    DeeplinkRoute.Profile.User(
                                                                        accountType = AccountType.Specific(service.accountKey),
                                                                        userKey = user.key,
                                                                    ),
                                                                ),
                                                        ),
                                                    ) +
                                                        (service as? TimelineTabProvider)
                                                            ?.timelineShortcuts
                                                            ?.mapNotNull(::toTab)
                                                            .orEmpty()
                                                ).toImmutableList(),
                                        )
                                    }
                                }
                        } else {
                            null
                        }
                    }
            }.combineLatestFlowLists()
            .map {
                it.filterNotNull().toImmutableList()
            }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val items by itemsFlow.collectAsUiState()

        return object : State {
            override val items = items
        }
    }

    private fun toTab(shortcut: TimelineShortcutDescriptor): Tab? =
        when (val target = shortcut.target) {
            is TimelineShortcutDescriptor.Target.Route -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination = Destination.Route(target.toDeeplinkRoute() ?: return null),
                )
            }

            is TimelineShortcutDescriptor.Target.Timeline -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination =
                        Destination.Timeline(
                            timelinePersistenceMapper.toTabItem(
                                TimelineTabDescriptor.Source(
                                    ref = target.ref,
                                    display = target.display,
                                ),
                            ),
                        ),
                )
            }
        }

    private fun TimelineShortcutDescriptor.Target.Route.toDeeplinkRoute(): DeeplinkRoute? =
        when (id) {
            TimelineShortcutDescriptor.RouteIds.ALL_LISTS -> accountKey?.let(DeeplinkRoute::AllLists)
            TimelineShortcutDescriptor.RouteIds.ALL_DIRECT_MESSAGES -> accountKey?.let(DeeplinkRoute::AllDirectMessages)
            TimelineShortcutDescriptor.RouteIds.BLUESKY_ALL_FEEDS -> accountKey?.let(DeeplinkRoute.Bluesky::AllFeeds)
            TimelineShortcutDescriptor.RouteIds.MISSKEY_ALL_ANTENNAS -> accountKey?.let(DeeplinkRoute.Misskey::AllAntennas)
            TimelineShortcutDescriptor.RouteIds.MISSKEY_ALL_CHANNELS -> accountKey?.let(DeeplinkRoute.Misskey::AllChannels)
            else -> null
        }
}
