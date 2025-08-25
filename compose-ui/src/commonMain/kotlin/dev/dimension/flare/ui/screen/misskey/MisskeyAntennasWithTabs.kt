package dev.dimension.flare.ui.screen.misskey

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.tab_settings_add
import dev.dimension.flare.compose.ui.tab_settings_remove
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.onSuccess
import org.jetbrains.compose.resources.stringResource

public fun LazyListScope.misskeyAntennasWithTabs(
    state: MisskeyAntennasListWithTabsPresenter.State,
    onClick: (UiList) -> Unit,
) {
    uiListItemComponent(
        state.data,
        onClick,
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
        },
    )
}
