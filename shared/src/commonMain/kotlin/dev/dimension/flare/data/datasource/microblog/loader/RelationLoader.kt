package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation

internal interface RelationLoader {
    val accountKey: MicroBlogKey

    suspend fun relation(userKey: MicroBlogKey): UiRelation

    suspend fun follow(userKey: MicroBlogKey)

    suspend fun unfollow(userKey: MicroBlogKey)

    suspend fun block(userKey: MicroBlogKey)

    suspend fun unblock(userKey: MicroBlogKey)

    suspend fun mute(userKey: MicroBlogKey)

    suspend fun unmute(userKey: MicroBlogKey)
}
