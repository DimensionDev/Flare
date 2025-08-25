package dev.dimension.flare.ui.screen.list

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.list_delete
import dev.dimension.flare.compose.ui.list_edit
import dev.dimension.flare.compose.ui.more
import dev.dimension.flare.compose.ui.tab_settings_add
import dev.dimension.flare.compose.ui.tab_settings_remove
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenu
import dev.dimension.flare.ui.component.platform.PlatformDropdownMenuItem
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

public fun LazyListScope.uiListWithTabs(
    state: AllListWithTabsPresenter.State,
    toList: (UiList) -> Unit,
    editList: (UiList) -> Unit,
    deleteList: (UiList) -> Unit,
) {
    uiListItemComponent(
        state.items,
        toList,
        trailingContent = { item ->
            state.currentTabs.onSuccess { currentTabs ->
                val isPinned =
                    remember(
                        item,
                        currentTabs,
                    ) {
                        currentTabs.contains(item.id)
                    }
                PlatformIconButton(
                    onClick = {
                        if (isPinned) {
                            state.unpinTab(item)
                        } else {
                            state.pinTab(item)
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
            if (!item.readonly) {
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
                                    text = stringResource(Res.string.list_edit),
                                )
                            },
                            onClick = {
                                editList(item)
                                showDropdown = false
                            },
                            leadingIcon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Pen,
                                    contentDescription = stringResource(Res.string.list_edit),
                                )
                            },
                        )
                        PlatformDropdownMenuItem(
                            text = {
                                PlatformText(
                                    text = stringResource(Res.string.list_delete),
                                    color = PlatformTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                deleteList(item)
                                showDropdown = false
                            },
                            leadingIcon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription = stringResource(Res.string.list_delete),
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
