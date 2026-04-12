package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun ActionMenu.Companion.like(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    count: Long,
    extras: Map<String, String> = emptyMap(),
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_LIKE}_$statusKey",
        icon = if (toggled) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(count),
        color = if (toggled) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.mutation(
                accountKey = accountKey,
                statusKey = statusKey,
                type = StatusMutation.TYPE_LIKE,
                params = buildMap {
                    put(StatusMutation.PARAM_TOGGLED, toggled.toString())
                    put(StatusMutation.PARAM_COUNT, count.toString())
                    putAll(extras)
                },
            ),
    )

public fun ActionMenu.Companion.repost(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    count: Long,
    extras: Map<String, String> = emptyMap(),
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_REPOST}_$statusKey",
        icon = if (toggled) UiIcon.Unretweet else UiIcon.Retweet,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.Unretweet
                } else {
                    ActionMenu.Item.Text.Localized.Type.Retweet
                },
            ),
        count = UiNumber(count),
        color = if (toggled) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.mutation(
                accountKey = accountKey,
                statusKey = statusKey,
                type = StatusMutation.TYPE_REPOST,
                params = buildMap {
                    put(StatusMutation.PARAM_TOGGLED, toggled.toString())
                    put(StatusMutation.PARAM_COUNT, count.toString())
                    putAll(extras)
                },
            ),
    )

public fun ActionMenu.Companion.bookmark(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    count: Long = 0,
    extras: Map<String, String> = emptyMap(),
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_BOOKMARK}_$statusKey",
        icon = if (toggled) UiIcon.Unbookmark else UiIcon.Bookmark,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.Unbookmark
                } else {
                    ActionMenu.Item.Text.Localized.Type.Bookmark
                },
            ),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.mutation(
                accountKey = accountKey,
                statusKey = statusKey,
                type = StatusMutation.TYPE_BOOKMARK,
                params = buildMap {
                    put(StatusMutation.PARAM_TOGGLED, toggled.toString())
                    put(StatusMutation.PARAM_COUNT, count.toString())
                    putAll(extras)
                },
            ),
    )

public fun ActionMenu.Companion.react(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    count: Long,
    clickEvent: ClickEvent,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_REACT}_$statusKey",
        icon = if (toggled) UiIcon.UnReact else UiIcon.React,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.UnReact
                } else {
                    ActionMenu.Item.Text.Localized.Type.React
                },
            ),
        count = UiNumber(count),
        color = if (toggled) ActionMenu.Item.Color.Red else null,
        clickEvent = clickEvent,
    )

public fun ActionMenu.Companion.favourite(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    extras: Map<String, String> = emptyMap(),
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_FAVOURITE}_$statusKey",
        icon = if (toggled) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(0),
        color = if (toggled) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.mutation(
                accountKey = accountKey,
                statusKey = statusKey,
                type = StatusMutation.TYPE_FAVOURITE,
                params = buildMap {
                    put(StatusMutation.PARAM_TOGGLED, toggled.toString())
                    putAll(extras)
                },
            ),
    )

public fun ActionMenu.Companion.likeComment(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    toggled: Boolean,
    count: Long,
    extras: Map<String, String> = emptyMap(),
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "${StatusMutation.TYPE_LIKE_COMMENT}_$statusKey",
        icon = if (toggled) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (toggled) {
                    ActionMenu.Item.Text.Localized.Type.Unlike
                } else {
                    ActionMenu.Item.Text.Localized.Type.Like
                },
            ),
        count = UiNumber(count),
        color = if (toggled) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.mutation(
                accountKey = accountKey,
                statusKey = statusKey,
                type = StatusMutation.TYPE_LIKE_COMMENT,
                params = buildMap {
                    put(StatusMutation.PARAM_TOGGLED, toggled.toString())
                    put(StatusMutation.PARAM_COUNT, count.toString())
                    putAll(extras)
                },
            ),
    )
