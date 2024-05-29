package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation

sealed interface ProfileAction {
    suspend operator fun invoke(
        userKey: MicroBlogKey,
        relation: UiRelation,
    )

    fun relationState(relation: UiRelation): Boolean

    interface Block : ProfileAction

    interface Mute : ProfileAction
}
