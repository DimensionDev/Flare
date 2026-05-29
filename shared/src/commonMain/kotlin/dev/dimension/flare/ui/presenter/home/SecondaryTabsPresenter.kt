package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.repository.AccountRepository
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
                                                        ShortcutSpec(
                                                            title = UiStrings.Me,
                                                            icon = UiIcon.Profile,
                                                            target =
                                                                ShortcutSpec.Target.Route(
                                                                    DeeplinkRoute.Profile.User(
                                                                        accountType = AccountType.Specific(service.accountKey),
                                                                        userKey = user.key,
                                                                    ),
                                                                ),
                                                        ),
                                                    ) +
                                                        (service as? TimelineTabConfigurationDataSource)
                                                            ?.shortcuts
                                                            .orEmpty()
                                                ).mapNotNull(::toTab)
                                                    .toImmutableList(),
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

    private fun toTab(shortcut: ShortcutSpec): Tab? =
        when (val target = shortcut.target) {
            is ShortcutSpec.Target.Route -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination = Destination.Route(target.route),
                )
            }

            is ShortcutSpec.Target.Timeline -> {
                Tab(
                    title = shortcut.title,
                    icon = shortcut.icon,
                    destination = Destination.Timeline(target.tabItem),
                )
            }
        }
}
