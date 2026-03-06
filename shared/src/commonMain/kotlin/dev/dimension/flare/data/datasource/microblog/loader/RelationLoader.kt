package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation

internal enum class RelationActionType {
    Follow,
    Block,
    Mute,
}

internal interface RelationLoader {
    val accountKey: MicroBlogKey
    val supportedTypes: Set<RelationActionType>

    suspend fun relation(userKey: MicroBlogKey): UiRelation

    suspend fun follow(userKey: MicroBlogKey)

    suspend fun unfollow(userKey: MicroBlogKey)

    suspend fun block(userKey: MicroBlogKey)

    suspend fun unblock(userKey: MicroBlogKey)

    suspend fun mute(userKey: MicroBlogKey)

    suspend fun unmute(userKey: MicroBlogKey)
}
