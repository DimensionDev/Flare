package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

internal fun ActionMenu.Companion.nostrRepost(
    statusKey: MicroBlogKey,
    repostEventId: String?,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "nostr_repost_$statusKey",
        icon = if (repostEventId != null) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (repostEventId != null) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(count),
        color = if (repostEventId != null) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(accountKey) {
                PostEvent.Nostr.Repost(
                    postKey = statusKey,
                    repostEventId = repostEventId,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )

internal fun ActionMenu.Companion.nostrLike(
    statusKey: MicroBlogKey,
    reactionEventId: String?,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "nostr_like_$statusKey",
        icon = if (reactionEventId != null) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (reactionEventId != null) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(count),
        color = if (reactionEventId != null) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(accountKey) {
                PostEvent.Nostr.Like(
                    postKey = statusKey,
                    reactionEventId = reactionEventId,
                    count = count,
                    accountKey = accountKey,
                )
            },
    )
