package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.pixivFavourite(
    statusKey: MicroBlogKey,
    favourited: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "pixiv_bookmark_$statusKey",
        icon = if (favourited) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (favourited) ActionMenu.Item.Text.Localized.Type.UnFavorite else ActionMenu.Item.Text.Localized.Type.Favorite,
            ),
        count = UiNumber(count),
        color = if (favourited) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Pixiv.Bookmark(
                    postKey = statusKey,
                    bookmarked = favourited,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Favorite,
    )
