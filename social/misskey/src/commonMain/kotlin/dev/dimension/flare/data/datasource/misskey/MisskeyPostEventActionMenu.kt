package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.misskeyFavourite
import dev.dimension.flare.ui.model.mapper.misskeyReact
import dev.dimension.flare.ui.model.mapper.misskeyRenote

internal fun PostEvent.misskeyNextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.Misskey.React -> {
            ActionMenu.misskeyReact(
                postKey = postKey,
                hasReacted = !hasReacted,
                reaction = reaction,
                count = (count + if (!hasReacted) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        }

        is PostEvent.Misskey.Renote -> {
            ActionMenu.misskeyRenote(
                postKey = postKey,
                count = count + 1,
                accountKey = accountKey,
            )
        }

        is PostEvent.Misskey.Favourite -> {
            ActionMenu.misskeyFavourite(
                postKey = postKey,
                favourited = !favourited,
                accountKey = accountKey,
            )
        }

        else -> {
            null
        }
    }
