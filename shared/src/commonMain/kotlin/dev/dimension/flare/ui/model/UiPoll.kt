package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.humanizePercentage
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Immutable
public data class UiPoll internal constructor(
    val id: String,
    val options: ImmutableList<Option>,
    val multiple: Boolean,
    val ownVotes: ImmutableList<Int>,
    val onVote: (options: ImmutableList<Int>) -> Unit,
    private val expiresAt: Instant,
    private val enabled: Boolean = true,
) {
    val expired: Boolean by lazy { expiresAt < Clock.System.now() }
    val voted: Boolean by lazy { ownVotes.isNotEmpty() }
    val expiredAt: UiDateTime by lazy { expiresAt.toUi() }

    val canVote: Boolean by lazy { !expired && !voted && enabled }

    @Immutable
    public data class Option internal constructor(
        val title: String,
        val votesCount: Long,
        val percentage: Float,
    ) {
        val humanizedPercentage: String by lazy { percentage.humanizePercentage() }
    }
}
