package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.humanizer.humanizePercentage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class UiPoll(
    val id: String,
    val options: ImmutableList<Option>,
    val expiresAt: Instant,
    val multiple: Boolean,
    val ownVotes: ImmutableList<Int>,
) {
    val humanizedExpiresAt by lazy { expiresAt.humanize() }
    val expired by lazy { expiresAt < Clock.System.now() }
    val voted by lazy { ownVotes.isNotEmpty() }

    data class Option(
        val title: String,
        val votesCount: Long,
        val percentage: Float,
    ) {
        val humanizedPercentage by lazy { percentage.humanizePercentage() }
    }
}
