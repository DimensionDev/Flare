package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.mastodonLike(
    favourited: Boolean,
    favouritesCount: Long,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_like_$statusKey",
        icon = if (favourited) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (favourited) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(favouritesCount),
        color = if (favourited) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Like(
                    postKey = statusKey,
                    liked = favourited,
                    accountKey = accountKey,
                    count = favouritesCount,
                )
            },
    )

public fun ActionMenu.Companion.mastodonRepost(
    reblogged: Boolean,
    reblogsCount: Long,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_repost_$statusKey",
        icon = if (reblogged) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (reblogged) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(reblogsCount),
        color = if (reblogged) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Reblog(
                    postKey = statusKey,
                    reblogged = reblogged,
                    count = reblogsCount,
                    accountKey = accountKey,
                )
            },
    )

public fun ActionMenu.Companion.mastodonBookmark(
    bookmarked: Boolean,
    accountKey: MicroBlogKey?,
    statusKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "mastodon_bookmark_$statusKey",
        icon =
            if (bookmarked) {
                UiIcon.Unbookmark
            } else {
                UiIcon.Bookmark
            },
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) {
                    ActionMenu.Item.Text.Localized.Type.Unbookmark
                } else {
                    ActionMenu.Item.Text.Localized.Type.Bookmark
                },
            ),
        count = UiNumber(0),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Mastodon.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                )
            },
    )
