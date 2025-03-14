package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Rss
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.component.status.ListComponent
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.theme.MediumAlpha
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

public fun LazyListScope.uiListItemComponent(
    items: PagingState<UiList>,
    onClicked: ((UiList) -> Unit)? = null,
    trailingContent: @Composable (UiList) -> Unit = {},
) {
    items(
        items,
        emptyContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillParentMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.List,
                        contentDescription = stringResource(Res.string.list_empty),
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(Res.string.list_empty),
                        style = PlatformTheme.typography.title,
                    )
                }
            }
        },
        loadingContent = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ItemPlaceHolder()
                HorizontalDivider()
            }
        },
        errorContent = {
            Column(
                modifier = Modifier.fillParentMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                    contentDescription = stringResource(Res.string.list_error),
                    modifier = Modifier.size(48.dp),
                )
                PlatformText(
                    text = stringResource(Res.string.list_error),
                    style = PlatformTheme.typography.title,
                )
            }
        },
    ) { item ->
        UiListItem(onClicked, item, trailingContent)
    }
}

@Composable
public fun UiListItem(
    onClicked: ((UiList) -> Unit)?,
    item: UiList,
    trailingContent: @Composable ((UiList) -> Unit),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .let {
                    if (onClicked == null) {
                        it
                    } else {
                        it
                            .clickable {
                                onClicked(item)
                            }
                    }
                },
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ListComponent(
            headlineContent = {
                PlatformText(text = item.title)
            },
            leadingContent = {
                if (item.avatar != null) {
                    NetworkImage(
                        model = item.avatar,
                        contentDescription = item.title,
                        modifier =
                            Modifier
                                .size(AvatarComponentDefaults.size)
                                .clip(PlatformTheme.shapes.medium),
                    )
                } else {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Rss,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(AvatarComponentDefaults.size)
                                .background(
                                    color = PlatformTheme.colorScheme.primaryContainer,
                                    shape = PlatformTheme.shapes.medium,
                                ).padding(8.dp),
                        tint = PlatformTheme.colorScheme.onPrimaryContainer,
                    )
                }
            },
            supportingContent = {
                if (item.creator != null) {
                    PlatformText(
                        text =
                            stringResource(
                                Res.string.feeds_discover_feeds_created_by,
                                item.creator?.handle ?: "Unknown",
                            ),
                        style = PlatformTheme.typography.caption,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha),
                    )
                }
            },
            trailingContent = {
                trailingContent.invoke(item)
            },
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
        )
        item.description?.takeIf { it.isNotEmpty() }?.let {
            PlatformText(
                text = it,
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
public fun ItemPlaceHolder(modifier: Modifier = Modifier) {
    ListComponent(
        modifier = modifier,
        headlineContent = {
            PlatformText(
                text = "lore ipsum dolor sit amet",
                modifier = Modifier.placeholder(true),
            )
        },
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(AvatarComponentDefaults.size)
                        .clip(PlatformTheme.shapes.medium)
                        .placeholder(true),
            )
        },
        supportingContent = {
            PlatformText(
                text = "lore ipsum",
                modifier = Modifier.placeholder(true),
            )
        },
    )
}
