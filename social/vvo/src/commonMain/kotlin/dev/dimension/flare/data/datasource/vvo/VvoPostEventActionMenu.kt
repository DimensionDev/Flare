package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.ui.model.mapper.vvoFavorite
import dev.dimension.flare.ui.model.mapper.vvoLike
import dev.dimension.flare.ui.model.mapper.vvoLikeComment

internal fun PostEvent.vvoNextActionMenu(): ActionMenu.Item? =
    when (this) {
        is PostEvent.VVO.Like ->
            ActionMenu.vvoLike(
                statusKey = postKey,
                liked = !liked,
                count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )

        is PostEvent.VVO.LikeComment ->
            ActionMenu.vvoLikeComment(
                statusKey = postKey,
                liked = !liked,
                count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                accountKey = accountKey,
            )

        is PostEvent.VVO.Favorite ->
            ActionMenu.vvoFavorite(
                statusKey = postKey,
                favorited = !favorited,
                accountKey = accountKey,
            )

        else -> null
    }
