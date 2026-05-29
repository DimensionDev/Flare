package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.vvoLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_like_$statusKey",
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
                PostEvent.VVO.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

public fun ActionMenu.Companion.vvoLikeComment(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_like_comment_$statusKey",
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
                PostEvent.VVO.LikeComment(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
    )

public fun ActionMenu.Companion.vvoFavorite(
    statusKey: MicroBlogKey,
    favorited: Boolean,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "vvo_favorite_$statusKey",
        icon = if (favorited) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (favorited) ActionMenu.Item.Text.Localized.Type.Unbookmark else ActionMenu.Item.Text.Localized.Type.Bookmark,
            ),
        count = UiNumber(0),
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.VVO.Favorite(
                    postKey = statusKey,
                    favorited = favorited,
                    accountKey = accountKey,
                ),
            ),
    )
