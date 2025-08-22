package dev.dimension.flare.ui.screen.misskey

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.RegisterTabCallback
import dev.dimension.flare.Res
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AntennasListPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.ScrollbarContainer
import io.github.composefluent.component.SubtleButton
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun AntennasListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
) {
    val state by producePresenter("antennas_list_$accountType") {
        presenter(accountType)
    }

    val listState = rememberLazyListState()
    val scrollbarAdapter = rememberScrollbarAdapter(listState)
    RegisterTabCallback(listState, onRefresh = state::refresh)
    ScrollbarContainer(
        adapter = scrollbarAdapter,
    ) {
        LazyColumn(
            contentPadding = LocalWindowPadding.current + PaddingValues(vertical = 8.dp),
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            state = listState,
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
                        SubtleButton(
                            onClick = {
                                if (isPinned) {
                                    state.unpinList(item)
                                } else {
                                    state.pinList(item)
                                }
                            },
                            iconOnly = true,
                        ) {
                            AnimatedContent(isPinned) {
                                if (it) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.ThumbtackSlash,
                                        contentDescription = stringResource(Res.string.tab_settings_add),
                                    )
                                } else {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Thumbtack,
                                        contentDescription = stringResource(Res.string.tab_settings_remove),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
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
                it.mainTabs
                    .filterIsInstance<Misskey.AntennasTimelineTabItem>()
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
                            mainTabs =
                                mainTabs +
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
                    }
                }
            }
        }

        fun unpinList(item: UiList) {
            accountState.user.onSuccess { user ->
                appScope.launch {
                    settingsRepository.updateTabSettings {
                        copy(
                            mainTabs =
                                mainTabs.filter {
                                    if (it is Misskey.AntennasTimelineTabItem) {
                                        it.id != item.id
                                    } else {
                                        true
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
