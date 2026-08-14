package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

public fun semanticPostActionMenu(
    postKey: MicroBlogKey,
    action: SemanticPostAction,
    actionToken: String?,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item {
    val active =
        action == SemanticPostAction.Unfavourite ||
            action == SemanticPostAction.Unrepost ||
            action == SemanticPostAction.Unbookmark
    val family =
        when (action) {
            SemanticPostAction.Favourite,
            SemanticPostAction.Unfavourite,
            -> PostActionFamily.Like

            SemanticPostAction.Repost,
            SemanticPostAction.Unrepost,
            -> PostActionFamily.Repost

            SemanticPostAction.Bookmark,
            SemanticPostAction.Unbookmark,
            -> PostActionFamily.Bookmark
        }
    val icon =
        when (action) {
            SemanticPostAction.Favourite -> UiIcon.Like
            SemanticPostAction.Unfavourite -> UiIcon.Unlike
            SemanticPostAction.Repost -> UiIcon.Retweet
            SemanticPostAction.Unrepost -> UiIcon.Unretweet
            SemanticPostAction.Bookmark -> UiIcon.Bookmark
            SemanticPostAction.Unbookmark -> UiIcon.Unbookmark
        }
    val text =
        when (action) {
            SemanticPostAction.Favourite -> ActionMenu.Item.Text.Localized.Type.Like
            SemanticPostAction.Unfavourite -> ActionMenu.Item.Text.Localized.Type.Unlike
            SemanticPostAction.Repost -> ActionMenu.Item.Text.Localized.Type.Retweet
            SemanticPostAction.Unrepost -> ActionMenu.Item.Text.Localized.Type.Unretweet
            SemanticPostAction.Bookmark -> ActionMenu.Item.Text.Localized.Type.Bookmark
            SemanticPostAction.Unbookmark -> ActionMenu.Item.Text.Localized.Type.Unbookmark
        }
    return ActionMenu.Item(
        updateKey = "semantic_${family.name.lowercase()}_$postKey",
        icon = icon,
        text = ActionMenu.Item.Text.Localized(text),
        count = UiNumber(count),
        color = if (active) ActionMenu.Item.Color.PrimaryColor else null,
        clickEvent =
            ClickEvent.event(accountKey) { resolvedAccount ->
                PostEvent.Semantic(
                    postKey = postKey,
                    action = action,
                    actionToken = actionToken,
                    count = count,
                    accountKey = resolvedAccount,
                )
            },
        actionFamily = family,
    )
}
