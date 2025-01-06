package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation

public sealed interface ProfileAction {
    public suspend operator fun invoke(
        userKey: MicroBlogKey,
        relation: UiRelation,
    )

    public fun relationState(relation: UiRelation): Boolean

    public interface Block : ProfileAction

    public interface Mute : ProfileAction
}
