package dev.dimension.flare.ui.screen.rss

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.File
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.delete_rss_source
import dev.dimension.flare.compose.ui.edit_rss_source
import dev.dimension.flare.compose.ui.empty_rss_sources
import dev.dimension.flare.compose.ui.more
import dev.dimension.flare.compose.ui.tab_settings_add
import dev.dimension.flare.compose.ui.tab_settings_remove
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformListItem
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

public fun LazyListScope.rssListWithTabs(
    state: RssListWithTabsPresenter.State,
    onClicked: (item: UiRssSource) -> Unit,
    onEdit: (id: Int) -> Unit,
) {
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
                PlatformText(
                    text = stringResource(Res.string.empty_rss_sources),
                    style = PlatformTheme.typography.headline,
                )
            }
        },
    ) { index, itemCount, it ->
        PlatformListItem(
            modifier =
                Modifier
                    .listCard(
                        index = index,
                        totalCount = itemCount,
                    ).clickable {
                        onClicked.invoke(it)
                    },
            headlineContent = {
                it.title?.let {
                    PlatformText(text = it)
                }
            },
            supportingContent = {
                PlatformText(it.url)
            },
            leadingContent = {
                NetworkImage(
                    model = it.favIcon,
                    contentDescription = it.title,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingContent = {
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
                        PlatformIconButton(
                            onClick = {
                                if (isPinned) {
                                    state.unpinTab(it)
                                } else {
                                    state.pinTab(it)
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

                    var showDropdown by remember {
                        mutableStateOf(false)
                    }
                    PlatformIconButton(onClick = { showDropdown = true }) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                            contentDescription = stringResource(Res.string.more),
                        )
                        PlatformDropdownMenu(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                        ) {
                            PlatformDropdownMenuItem(
                                text = {
                                    PlatformText(
                                        text = stringResource(Res.string.edit_rss_source),
                                    )
                                },
                                onClick = {
                                    onEdit.invoke(it.id)
                                    showDropdown = false
                                },
                                leadingIcon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Pen,
                                        contentDescription = stringResource(Res.string.edit_rss_source),
                                    )
                                },
                            )
                            PlatformDropdownMenuItem(
                                text = {
                                    PlatformText(
                                        text = stringResource(Res.string.delete_rss_source),
                                        color = PlatformTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    state.delete(it.id)
                                    showDropdown = false
                                },
                                leadingIcon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Trash,
                                        contentDescription = stringResource(Res.string.delete_rss_source),
                                        tint = PlatformTheme.colorScheme.error,
                                    )
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}
