package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.MixedTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.settings.AccountEventPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class HomeTimelineWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<HomeTimelineWithTabsPresenter.State>(),
    KoinComponent {
    private val settingsRepository by inject<SettingsRepository>()

    public interface State : UserState {
        public val tabState: UiState<ImmutableList<TimelineItemPresenter.State>>
    }

    @Composable
    override fun body(): State {
        val accountState =
            remember(accountType) {
                UserPresenter(
                    accountType = accountType,
                    userKey = null,
                )
            }.body()

        val accountEvent =
            remember {
                AccountEventPresenter()
            }.body()

        LaunchedEffect(accountEvent.onAdded) {
            accountEvent.onAdded.collect { account ->
                val tab =
                    HomeTimelineTabItem(
                        accountKey = account.accountKey,
                        icon = UiRssSource.favIconUrl(account.accountKey.host),
                        title =
                            when (account.platformType) {
                                PlatformType.Mastodon -> "Mastodon"
                                PlatformType.Misskey -> "Misskey"
                                PlatformType.Bluesky -> "Bluesky"
                                PlatformType.xQt -> "X"
                                PlatformType.VVo -> vvo
                            },
                    )
                settingsRepository.updateTabSettings {
                    if (mainTabs.any { it.key == tab.key }) {
                        copy()
                    } else {
                        copy(
                            mainTabs =
                                (mainTabs + tab).distinctBy {
                                    it.key
                                },
                        )
                    }
                }
            }
        }

        LaunchedEffect(accountEvent.onRemoved) {
            accountEvent.onRemoved.collect { accountKey ->
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs = mainTabs.filterNot { it.account == AccountType.Specific(accountKey) },
                    )
                }
            }
        }

        val tabs by remember {
            settingsRepository.tabSettings
                .map { settings ->
                    if (accountType == AccountType.Guest) {
                        listOf(
                            HomeTimelineTabItem(AccountType.Guest),
                        )
                    } else {
                        (
                            listOfNotNull(
                                if (settings.enableMixedTimeline && settings.mainTabs.size > 1) {
                                    MixedTimelineTabItem(
                                        subTimelineTabItem = settings.mainTabs,
                                    )
                                } else {
                                    null
                                },
                            ) + settings.mainTabs
                        ).ifEmpty {
                            listOf(
                                HomeTimelineTabItem(
                                    accountType = AccountType.Active,
                                ),
                            )
                        }
                    }
                }.map {
                    it.toImmutableList()
                }
        }.collectAsUiState()
        val tabState =
            tabs.map {
                it
                    .map {
                        // use key inorder to force update when the list is changed
                        key(it.key) {
                            TimelineItemPresenter(it).body()
                        }
                    }.toImmutableList()
            }

        return remember(accountState, tabState) {
            object : State, UserState by accountState {
                override val tabState: UiState<ImmutableList<TimelineItemPresenter.State>> = tabState
            }
        }
    }
}
