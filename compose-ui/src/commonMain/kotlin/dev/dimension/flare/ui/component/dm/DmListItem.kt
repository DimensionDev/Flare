package dev.dimension.flare.ui.component.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.List
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.dm_list_empty
import dev.dimension.flare.compose.ui.dm_list_error
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.ItemPlaceHolder
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.component.platform.PlatformListItem
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

public fun LazyListScope.dmList(
    data: PagingState<UiDMRoom>,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    itemsIndexed(
        data,
        emptyContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.List,
                        contentDescription = stringResource(Res.string.dm_list_empty),
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(Res.string.dm_list_empty),
                        style = PlatformTheme.typography.headline,
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
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                        contentDescription = stringResource(Res.string.dm_list_error),
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(Res.string.dm_list_error),
                        style = PlatformTheme.typography.headline,
                    )
                    PlatformText(
                        text = it.message.orEmpty(),
                        style = PlatformTheme.typography.headline,
                    )
                }
            }
        },
        itemContent = { index, itemCount, item ->
            PlatformListItem(
                headlineContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.hasUser) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                item.users.fastForEach { user ->
                                    RichText(
                                        text = user.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.users.size == 1) {
                                        PlatformText(
                                            text = user.handle,
                                            style = PlatformTheme.typography.caption,
                                            color = PlatformTheme.colorScheme.caption,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        val lastMessage = item.lastMessage
                        if (lastMessage != null) {
                            DateTimeText(
                                lastMessage.timestamp,
                                style = PlatformTheme.typography.caption,
                                color = PlatformTheme.colorScheme.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    if (!item.hasUser) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleUser,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size),
                            tint = PlatformTheme.colorScheme.primary,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size),
                        ) {
                            repeat(
                                min(item.users.size, 2),
                            ) {
                                val avatar = item.users[it].avatar
                                if (item.users.size == 1) {
                                    AvatarComponent(avatar)
                                } else {
                                    Box(
                                        modifier =
                                            Modifier
                                                .offset(
                                                    x = (it * 12).dp,
                                                    y = (it * 12).dp,
                                                ),
                                    ) {
                                        AvatarComponent(
                                            avatar,
                                            size = AvatarComponentDefaults.compatSize,
                                        )
                                    }
                                }
                            }
                            if (item.users.size > 1) {
                                PlatformText(
                                    item.users.size.toString(),
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                PlatformTheme.colorScheme.card,
                                                shape = PlatformTheme.shapes.small,
                                            ).padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                },
                supportingContent = {
                    PlatformText(
                        text = item.lastMessageText,
                        style = PlatformTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    if (item.unreadCount > 0) {
                        PlatformText(
                            text = item.unreadCount.toString(),
                            modifier =
                                Modifier
                                    .background(
                                        PlatformTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ).padding(horizontal = 4.dp),
                        )
                    }
                },
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = itemCount,
                        ).clickable {
                            onItemClicked.invoke(item.key)
                        },
            )
        },
    )
}
