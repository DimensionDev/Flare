package dev.dimension.flare.ui.model

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import kotlinx.collections.immutable.toPersistentList

public object PostActionLayoutPreviewHelper {
    public fun withPreviewActions(post: UiTimelineV2.Post): UiTimelineV2.Post {
        var replacedMore = false
        val previewActions =
            post.actions
                .map { action ->
                    val item = action as? ActionMenu.Item
                    if (item?.isMoreMenuDisplayItem() == true) {
                        replacedMore = true
                        previewMoreGroup(item)
                    } else if (item?.actionFamily == PostActionFamily.Repost) {
                        previewRepostGroup(item)
                    } else {
                        action
                    }
                }.let { actions ->
                    if (replacedMore) actions else actions + previewMoreGroup()
                }
        return post.copy(actions = previewActions.toPersistentList())
    }

    private fun ActionMenu.Item.isMoreMenuDisplayItem(): Boolean =
        actionFamily == null &&
            icon == UiIcon.More &&
            text == ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More)

    private fun previewMoreGroup(
        displayItem: ActionMenu.Item =
            ActionMenu.Item(
                icon = UiIcon.More,
                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
            ),
    ): ActionMenu.Group =
        ActionMenu.Group(
            displayItem = displayItem,
            actions =
                listOf(
                    ActionMenu.Item(
                        icon = UiIcon.Translate,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Translate),
                        actionFamily = PostActionFamily.Translate,
                    ),
                    ActionMenu.Item(
                        icon = UiIcon.Bookmark,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Bookmark),
                        count = UiNumber(4),
                        actionFamily = PostActionFamily.Bookmark,
                    ),
                    ActionMenu.Item(
                        icon = UiIcon.Share,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                        actionFamily = PostActionFamily.Share,
                    ),
                ).toPersistentList(),
        )

    private fun previewRepostGroup(displayItem: ActionMenu.Item): ActionMenu.Group =
        ActionMenu.Group(
            displayItem = displayItem,
            actions =
                listOf(
                    displayItem,
                    ActionMenu.Item(
                        icon = UiIcon.Quote,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                        count = UiNumber(2),
                        actionFamily = PostActionFamily.Quote,
                    ),
                ).toPersistentList(),
        )
}
