package dev.dimension.flare.ui.screen.rss

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.add_rss_source
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.delete_rss_source
import dev.dimension.flare.edit_rss_source
import dev.dimension.flare.empty_rss_sources
import dev.dimension.flare.more
import dev.dimension.flare.tab_settings_add
import dev.dimension.flare.tab_settings_remove
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssSourcesPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun RssListScreen(
    toItem: (UiRssSource) -> Unit,
    onEdit: (UiRssSource) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter { presenter() }

    LazyColumn(
        modifier =
            modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = LocalWindowPadding.current + PaddingValues(top = 8.dp),
    ) {
        item {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillParentMaxWidth(),
            ) {
                AccentButton(
                    onClick = {
                        onAdd.invoke()
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Plus,
                        contentDescription = stringResource(Res.string.add_rss_source),
                    )
                    Text(
                        text = stringResource(Res.string.add_rss_source),
                    )
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
        }
        itemsIndexed(
            state.sources,
            emptyContent = {
                Column(
                    modifier =
                        Modifier
                            .fillParentMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.File,
                        contentDescription = stringResource(Res.string.empty_rss_sources),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = stringResource(Res.string.empty_rss_sources),
                        style = FluentTheme.typography.subtitle,
                    )
                }
            },
        ) { index, itemCount, it ->
            CardExpanderItem(
                onClick = {
                    toItem.invoke(it)
                },
                heading = {
                    it.title?.let {
                        Text(text = it)
                    }
                },
                caption = {
                    Text(it.url)
                },
                icon = {
                    NetworkImage(
                        model = it.favIcon,
                        contentDescription = it.title,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.currentTabs.onSuccess { currentTabs ->
                            val isPinned =
                                remember(
                                    it,
                                    currentTabs,
                                ) {
                                    currentTabs.contains(it.url)
                                }
                            SubtleButton(
                                iconOnly = true,
                                onClick = {
                                    if (isPinned) {
                                        state.unpinSource(it)
                                    } else {
                                        state.pinSource(it)
                                    }
                                },
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

                        MenuFlyoutContainer(
                            flyout = {
                                MenuFlyoutItem(
                                    text = {
                                        Text(
                                            text = stringResource(Res.string.edit_rss_source),
                                        )
                                    },
                                    onClick = {
                                        onEdit.invoke(it)
                                        isFlyoutVisible = false
                                    },
                                    icon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Pen,
                                            contentDescription = stringResource(Res.string.edit_rss_source),
                                        )
                                    },
                                )
                                MenuFlyoutItem(
                                    text = {
                                        Text(
                                            text = stringResource(Res.string.delete_rss_source),
                                            color = FluentTheme.colors.system.critical,
                                        )
                                    },
                                    onClick = {
                                        state.delete(it.id)
                                        isFlyoutVisible = false
                                    },
                                    icon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Trash,
                                            contentDescription = stringResource(Res.string.delete_rss_source),
                                            tint = FluentTheme.colors.system.critical,
                                        )
                                    },
                                )
                            },
                        ) {
                            SubtleButton(onClick = { isFlyoutVisible = true }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(Res.string.more),
                                )
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
    settingsRepository: SettingsRepository = koinInject(),
    appScope: CoroutineScope = koinInject(),
) = run {
    val state = remember { RssSourcesPresenter() }.invoke()
    val tabSettings by settingsRepository.tabSettings.collectAsUiState()
    val currentTabs =
        tabSettings.map {
            it.mainTabs
                .filterIsInstance<RssTimelineTabItem>()
                .map { it.feedUrl }
                .toImmutableList()
        }
    object : RssSourcesPresenter.State by state {
        val currentTabs = currentTabs

        fun pinSource(source: UiRssSource) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs =
                            mainTabs + RssTimelineTabItem(source),
                    )
                }
            }
        }

        fun unpinSource(source: UiRssSource) {
            appScope.launch {
                settingsRepository.updateTabSettings {
                    copy(
                        mainTabs =
                            mainTabs.filterNot { it is RssTimelineTabItem && it.feedUrl == source.url },
                    )
                }
            }
        }
    }
}
