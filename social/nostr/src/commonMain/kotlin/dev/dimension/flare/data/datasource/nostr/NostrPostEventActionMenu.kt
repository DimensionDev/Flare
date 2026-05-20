package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost

internal fun PostEvent.nostrNextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.Nostr.Repost -> {
            ActionMenu.nostrRepost(
                statusKey = postKey,
                repostEventId = if (repostEventId == null) "" else null,
                count = (count + if (repostEventId == null) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        }

        is PostEvent.Nostr.Like -> {
            ActionMenu.nostrLike(
                statusKey = postKey,
                reactionEventId = if (reactionEventId == null) "" else null,
                count = (count + if (reactionEventId == null) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )
        }

        is PostEvent.Nostr.Report -> {
            null
        }

        else -> {
            null
        }
    }
