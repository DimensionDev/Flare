package dev.dimension.flare.ui.screen.misskey

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.HomeTimelineTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AntennasListPresenter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AntennasListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
) {
    val state by producePresenter("antennas_list_$accountType") {
        presenter(accountType)
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_antennas_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.data.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                ) {
                    uiListItemComponent(
                        state.data,
                        toTimeline,
                        trailingContent = { item ->
                            state.currentTabs.onSuccess { currentTabs ->
                                val isPinned =
                                    remember(
                                        item,
                                        currentTabs,
                                    ) {
                                        currentTabs.contains(item.id)
                                    }
                                IconButton(
                                    onClick = {
                                        if (isPinned) {
                                            state.unpinList(item)
                                        } else {
                                            state.pinList(item)
                                        }
                                    },
                                ) {
                                    AnimatedContent(isPinned) {
                                        if (it) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.ThumbtackSlash,
                                                contentDescription = stringResource(id = R.string.tab_settings_add),
                                            )
                                        } else {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Thumbtack,
                                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val accountState =
        remember(accountType) {
            UserPresenter(
                accountType = accountType,
                userKey = null,
            )
        }.invoke()
    val currentTabs =
        accountState.user.flatMap { user ->
            tabSettings.map {
                it.homeTabs
                    .getOrDefault(
                        user.key,
                        listOf(HomeTimelineTabItem(accountType = AccountType.Specific(user.key))),
                    ).filterIsInstance<Misskey.AntennasTimelineTabItem>()
                    .map { it.id }
                    .toImmutableList()
            }
        }
    val state =
        remember(accountType) {
            AntennasListPresenter(accountType)
        }.invoke()

    object : AntennasListPresenter.State by state {
        val currentTabs = currentTabs

        fun pinList(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            homeTabs =
                                homeTabs + (
                                    user.key to
                                        homeTabs
                                            .getOrDefault(
                                                user.key,
                                                defaultValue =
                                                    listOf(
                                                        HomeTimelineTabItem(accountType),
                                                    ),
                                            ).plus(
                                                Misskey.AntennasTimelineTabItem(
                                                    account = AccountType.Specific(user.key),
                                                    id = item.id,
                                                    metaData =
                                                        TabMetaData(
                                                            title = TitleType.Text(item.title),
                                                            icon = IconType.Material(IconType.Material.MaterialIcon.List),
                                                        ),
                                                ),
                                            )
                                ),
                        )
                    }
                }
            }
        }

        fun unpinList(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            homeTabs =
                                homeTabs + (
                                    user.key to

                                        homeTabs
                                            .getOrDefault(
                                                user.key,
                                                defaultValue =
                                                    listOf(
                                                        HomeTimelineTabItem(accountType),
                                                    ),
                                            ).filter {
                                                if (it is Misskey.AntennasTimelineTabItem) {
                                                    it.id != item.id
                                                } else {
                                                    true
                                                }
                                            }
                                ),
                        )
                    }
                }
            }
        }
    }
}
