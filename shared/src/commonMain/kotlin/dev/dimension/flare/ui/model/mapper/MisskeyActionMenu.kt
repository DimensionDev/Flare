package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.route.DeeplinkRoute

public fun ActionMenu.Companion.misskeyRenote(
    postKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_renote_$postKey",
        icon = UiIcon.Retweet,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Retweet),
        count = UiNumber(count),
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Misskey.Renote(
                    postKey = postKey,
                    count = count,
                    accountKey = accountKey,
                )
            },
        actionFamily = PostActionFamily.Repost,
    )

public fun ActionMenu.Companion.misskeyReact(
    postKey: MicroBlogKey,
    hasReacted: Boolean,
    reaction: String?,
    count: Long,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_react_$postKey",
        icon = if (hasReacted) UiIcon.UnReact else UiIcon.React,
        text =
            ActionMenu.Item.Text.Localized(
                if (hasReacted) {
                    ActionMenu.Item.Text.Localized.Type.UnReact
                } else {
                    ActionMenu.Item.Text.Localized.Type.React
                },
            ),
        count = UiNumber(count),
        color = if (hasReacted) ActionMenu.Item.Color.Red else null,
        clickEvent =
            if (!hasReacted || reaction.isNullOrEmpty()) {
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status
                        .AddReaction(
                            statusKey = postKey,
                            accountType =
                                accountKey?.let { AccountType.Specific(it) }
                                    ?: AccountType.Guest,
                        ),
                )
            } else {
                ClickEvent.event(
                    accountKey,
                ) { accountKey ->
                    PostEvent.Misskey.React(
                        postKey = postKey,
                        hasReacted = true,
                        reaction = reaction,
                        count = count,
                        accountKey = accountKey,
                    )
                }
            },
        actionFamily = PostActionFamily.React,
    )

public fun ActionMenu.Companion.misskeyFavourite(
    postKey: MicroBlogKey,
    favourited: Boolean,
    accountKey: MicroBlogKey?,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "misskey_favourite_$postKey",
        icon = if (favourited) UiIcon.Favourite else UiIcon.UnFavourite,
        text =
            ActionMenu.Item.Text.Localized(
                if (favourited) {
                    ActionMenu.Item.Text.Localized.Type.UnFavorite
                } else {
                    ActionMenu.Item.Text.Localized.Type.Favorite
                },
            ),
        count = UiNumber(0),
        color = if (favourited) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
            ) { accountKey ->
                PostEvent.Misskey.Favourite(
                    postKey = postKey,
                    favourited = favourited,
                    accountKey = accountKey,
                )
            },
        actionFamily = PostActionFamily.Favorite,
    )
