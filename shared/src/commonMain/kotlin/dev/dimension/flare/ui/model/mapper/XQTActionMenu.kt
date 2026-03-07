package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

internal fun ActionMenu.Companion.xqtRetweet(
    statusKey: MicroBlogKey,
    retweeted: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "xqt_retweet_$statusKey",
        icon = if (retweeted) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (retweeted) ActionMenu.Item.Text.Localized.Type.Unretweet else ActionMenu.Item.Text.Localized.Type.Retweet,
            ),
        count = UiNumber(count),
        color = if (retweeted) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.XQT.Retweet(
                    postKey = statusKey,
                    retweeted = retweeted,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

internal fun ActionMenu.Companion.xqtLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "xqt_like_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
            ),
        count = UiNumber(count),
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.XQT.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

internal fun ActionMenu.Companion.xqtBookmark(
    statusKey: MicroBlogKey,
    bookmarked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "xqt_bookmark_$statusKey",
        icon = if (bookmarked) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) ActionMenu.Item.Text.Localized.Type.Unbookmark else ActionMenu.Item.Text.Localized.Type.Bookmark,
            ),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.XQT.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )
