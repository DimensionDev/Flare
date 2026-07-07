package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.tumblrLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "tumblr_like_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(count),
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.Tumblr.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Like,
    )

public fun ActionMenu.Companion.tumblrRepost(
    statusKey: MicroBlogKey,
    reposted: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "tumblr_repost_$statusKey",
        icon = if (reposted) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (reposted) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(count),
        color = if (reposted) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            if (reposted) {
                ClickEvent.Noop
            } else {
                ClickEvent.event(
                    accountKey,
                    PostEvent.Tumblr.Repost(
                        postKey = statusKey,
                        reposted = reposted,
                        count = count,
                        accountKey = accountKey,
                    ),
                )
            },
        actionFamily = PostActionFamily.Repost,
    )
