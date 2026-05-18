package dev.dimension.flare.data.datasource.microblog.loader

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation

public enum class RelationActionType {
    Follow,
    Block,
    Mute,
}

public interface RelationLoader {
    public val supportedTypes: Set<RelationActionType>

    public suspend fun relation(userKey: MicroBlogKey): UiRelation

    public suspend fun follow(userKey: MicroBlogKey)

    public suspend fun unfollow(userKey: MicroBlogKey)

    public suspend fun block(userKey: MicroBlogKey)

    public suspend fun unblock(userKey: MicroBlogKey)

    public suspend fun mute(userKey: MicroBlogKey)

    public suspend fun unmute(userKey: MicroBlogKey)
}
