package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.pixivBookmark(
    statusKey: MicroBlogKey,
    bookmarked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "pixiv_bookmark_$statusKey",
        icon = if (bookmarked) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) ActionMenu.Item.Text.Localized.Type.Unbookmark else ActionMenu.Item.Text.Localized.Type.Bookmark,
            ),
        count = UiNumber(count),
        color = if (bookmarked) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Pixiv.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )
