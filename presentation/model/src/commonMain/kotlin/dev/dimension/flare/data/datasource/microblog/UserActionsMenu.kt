package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf

public fun userActionsMenu(
    accountKey: MicroBlogKey?,
    userKey: MicroBlogKey,
    handle: String,
): List<ActionMenu> =
    listOfNotNull(
        ActionMenu.Item(
            icon = UiIcon.Mute,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.MuteWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.MuteUser(accountKey, userKey),
                ),
        ),
        ActionMenu.Item(
            icon = UiIcon.Block,
            text =
                ActionMenu.Item.Text.Localized(
                    type = ActionMenu.Item.Text.Localized.Type.BlockWithHandleParameter,
                    parameters = persistentListOf(handle),
                ),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.BlockUser(accountKey, userKey),
                ),
        ),
    )
