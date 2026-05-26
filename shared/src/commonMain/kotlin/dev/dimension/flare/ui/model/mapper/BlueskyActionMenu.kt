package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.blueskyReblog(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    count: Long,
    cid: String,
    uri: String,
    repostUri: String?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_reblog_$postKey",
        icon = if (repostUri != null) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (repostUri != null) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(count),
        color = if (repostUri != null) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Reblog(
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    repostUri = repostUri,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

public fun ActionMenu.Companion.blueskyLike(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    count: Long,
    cid: String,
    uri: String,
    likedUri: String?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_like_$postKey",
        icon = if (likedUri != null) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (likedUri != null) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        color = if (likedUri != null) ActionMenu.Item.Color.Red else null,
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Like(
                    postKey = postKey,
                    cid = cid,
                    uri = uri,
                    likedUri = likedUri,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

public fun ActionMenu.Companion.blueskyBookmark(
    accountKey: MicroBlogKey,
    postKey: MicroBlogKey,
    uri: String,
    cid: String,
    count: Long,
    bookmarked: Boolean,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "bluesky_bookmark_$postKey",
        icon = if (bookmarked) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (bookmarked) {
                    ActionMenu.Item.Text.Localized.Type.Unbookmark
                } else {
                    ActionMenu.Item.Text.Localized.Type.Bookmark
                },
            ),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Bluesky.Bookmark(
                    postKey = postKey,
                    uri = uri,
                    cid = cid,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                    count = count,
                )
            },
    )
