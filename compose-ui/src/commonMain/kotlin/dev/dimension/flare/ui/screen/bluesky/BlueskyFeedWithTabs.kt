package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.ThumbtackSlash
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.tab_settings_add
import dev.dimension.flare.compose.ui.tab_settings_remove
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.UiListItem
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.status.StatusPlaceholder
import dev.dimension.flare.ui.component.uiListItemComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.onSuccess
import org.jetbrains.compose.resources.stringResource

public fun LazyListScope.myBlueskyFeedWithTabs(
    state: BlueskyFeedsWithTabsPresenter.State,
    toFeed: (UiList) -> Unit,
) {
    uiListItemComponent(
        state.myFeeds,
        onClicked = toFeed,
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

public fun LazyListScope.popularBlueskyFeedWithTabs(
    state: BlueskyFeedsWithTabsPresenter.State,
    toFeed: (UiList) -> Unit,
) {
    itemsIndexed(
        state.popularFeeds,
        loadingCount = 5,
        loadingContent = { index, itemCount ->
            StatusPlaceholder(
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = itemCount,
                        ),
            )
        },
    ) { index, itemCount, (item, subscribed) ->
        UiListItem(
            onClicked = {
                toFeed.invoke(item)
            },
            item = item,
            trailingContent = {
                PlatformIconButton(
                    onClick = {
                        if (subscribed) {
                            state.unsubscribe(item)
                            state.unpinTab(item)
                        } else {
                            state.subscribe(item)
                            state.pinTab(item)
                        }
                    },
                ) {
                    if (subscribed) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Trash,
                            contentDescription = null,
                        )
                    } else {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .listCard(
                        index = index,
                        totalCount = itemCount,
                    ),
        )
    }
}
