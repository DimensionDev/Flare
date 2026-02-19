package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Rss
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.feeds_discover_feeds_created_by
import dev.dimension.flare.compose.ui.list_empty
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.platform.PlatformListItem
import dev.dimension.flare.ui.component.platform.PlatformSegmentedListItem
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

public fun <T : UiList> LazyListScope.uiListItemComponent(
    items: PagingState<T>,
    onClicked: ((T) -> Unit)? = null,
    trailingContent: @Composable RowScope.(T) -> Unit = {},
) {
    itemsIndexed(
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
        loadingContent = { index, itemCount ->
            ItemPlaceHolder(
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = itemCount,
                        ),
            )
        },
        errorContent = {
            ErrorContent(
                error = it,
                modifier = Modifier.fillParentMaxSize(),
                onRetry = {
                },
            )
        },
    ) { index, itemCount, item ->
        UiListItem(
            onClicked =
                onClicked?.let {
                    {
                        it.invoke(item)
                    }
                },
            item = item,
            trailingContent = trailingContent,
            index = index,
            totalCount = itemCount,
        )
    }
}

@Composable
public fun <T : UiList> UiListItem(
    onClicked: (() -> Unit)?,
    item: T,
    trailingContent: @Composable (RowScope.(T) -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is UiList.List ->
            UiListCard(
                item = item,
                onClicked = onClicked,
                trailingContent = {
                    trailingContent.invoke(this, item)
                },
                index = index,
                totalCount = totalCount,
                modifier = modifier,
            )
        is UiList.Feed ->
            UiFeedCard(
                item = item,
                onClicked = onClicked,
                trailingContent = {
                    trailingContent.invoke(this, item)
                },
                index = index,
                totalCount = totalCount,
                modifier = modifier,
            )
        is UiList.Antenna ->
            UiAntennaCard(
                item = item,
                onClicked = onClicked,
                trailingContent = {
                    trailingContent.invoke(this, item)
                },
                index = index,
                totalCount = totalCount,
                modifier = modifier,
            )
        is UiList.Channel ->
            UiChannelCard(
                item = item,
                onClicked = onClicked,
                trailingContent = {
                    trailingContent.invoke(this, item)
                },
                index = index,
                totalCount = totalCount,
                modifier = modifier,
            )
    }
}

@Composable
private fun UiListCard(
    item: UiList.List,
    onClicked: (() -> Unit)?,
    trailingContent: @Composable (RowScope.() -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = item.description
    if (!description.isNullOrEmpty()) {
        Column(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card)
                    .let {
                        if (onClicked == null) {
                            it
                        } else {
                            it.clickable { onClicked() }
                        }
                    },
        ) {
            PlatformListItem(
                headlineContent = { PlatformText(text = item.title) },
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
                            imageVector = FontAwesomeIcons.Solid.List,
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
                            color = PlatformTheme.colorScheme.caption,
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        trailingContent.invoke(this)
                    }
                },
            )
            PlatformText(
                text = description,
                modifier =
                    Modifier
                        .background(PlatformTheme.colorScheme.card)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .padding(horizontal = screenHorizontalPadding),
            )
        }
    } else {
        PlatformSegmentedListItem(
            modifier = modifier,
            index = index,
            totalCount = totalCount,
            onClick = { onClicked?.invoke() },
            headlineContent = { PlatformText(text = item.title) },
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
                        imageVector = FontAwesomeIcons.Solid.List,
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
                        color = PlatformTheme.colorScheme.caption,
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent.invoke(this)
                }
            },
        )
    }
}

@Composable
private fun UiFeedCard(
    item: UiList.Feed,
    onClicked: (() -> Unit)?,
    trailingContent: @Composable (RowScope.() -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = item.description
    if (!description.isNullOrEmpty()) {
        Column(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card)
                    .let {
                        if (onClicked == null) {
                            it
                        } else {
                            it.clickable { onClicked() }
                        }
                    },
        ) {
            PlatformListItem(
                headlineContent = { PlatformText(text = item.title) },
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
                            color = PlatformTheme.colorScheme.caption,
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        trailingContent.invoke(this)
                    }
                },
            )
            PlatformText(
                text = description,
                modifier =
                    Modifier
                        .background(PlatformTheme.colorScheme.card)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .padding(horizontal = screenHorizontalPadding),
            )
        }
    } else {
        PlatformSegmentedListItem(
            modifier = modifier,
            index = index,
            totalCount = totalCount,
            onClick = { onClicked?.invoke() },
            headlineContent = { PlatformText(text = item.title) },
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
                        color = PlatformTheme.colorScheme.caption,
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent.invoke(this)
                }
            },
        )
    }
}

@Composable
private fun UiAntennaCard(
    item: UiList.Antenna,
    onClicked: (() -> Unit)?,
    trailingContent: @Composable (RowScope.() -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    PlatformSegmentedListItem(
        modifier = modifier,
        index = index,
        totalCount = totalCount,
        onClick = { onClicked?.invoke() },
        headlineContent = { PlatformText(text = item.title) },
        leadingContent = {
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
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent.invoke(this)
            }
        },
    )
}

@Composable
private fun UiChannelCard(
    item: UiList.Channel,
    onClicked: (() -> Unit)?,
    trailingContent: @Composable (RowScope.() -> Unit),
    index: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val description = item.description
    if (description != null) {
        Column(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card)
                    .let {
                        if (onClicked == null) {
                            it
                        } else {
                            it.clickable { onClicked() }
                        }
                    },
        ) {
            PlatformListItem(
                headlineContent = { PlatformText(text = item.title) },
                leadingContent = {
                    if (item.banner != null) {
                        NetworkImage(
                            model = item.banner,
                            contentDescription = item.title,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size)
                                    .clip(PlatformTheme.shapes.medium),
                        )
                    } else {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.List,
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
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        trailingContent.invoke(this)
                    }
                },
            )
            RichText(
                text = description,
                modifier =
                    Modifier
                        .background(PlatformTheme.colorScheme.card)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .padding(horizontal = screenHorizontalPadding),
            )
        }
    } else {
        PlatformSegmentedListItem(
            modifier = modifier,
            index = index,
            totalCount = totalCount,
            onClick = { onClicked?.invoke() },
            headlineContent = { PlatformText(text = item.title) },
            leadingContent = {
                if (item.banner != null) {
                    NetworkImage(
                        model = item.banner,
                        contentDescription = item.title,
                        modifier =
                            Modifier
                                .size(AvatarComponentDefaults.size)
                                .clip(PlatformTheme.shapes.medium),
                    )
                } else {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.List,
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
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingContent.invoke(this)
                }
            },
        )
    }
}

@Composable
public fun ItemPlaceHolder(modifier: Modifier = Modifier) {
    PlatformListItem(
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
