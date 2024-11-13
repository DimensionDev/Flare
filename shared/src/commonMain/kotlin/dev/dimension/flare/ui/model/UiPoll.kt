package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.humanizer.humanizePercentage
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Immutable
data class UiPoll internal constructor(
    val id: String,
    val options: ImmutableList<Option>,
    val multiple: Boolean,
    val ownVotes: ImmutableList<Int>,
    val onVote: (options: ImmutableList<Int>) -> Unit,
    private val expiresAt: Instant,
    private val enabled: Boolean = true,
) {
    val expired by lazy { expiresAt < Clock.System.now() }
    val voted by lazy { ownVotes.isNotEmpty() }
    val expiredAt by lazy { expiresAt.toUi() }

    val canVote by lazy { !expired && !voted && enabled }

    @Immutable
    data class Option(
        val title: String,
        val votesCount: Long,
        val percentage: Float,
    ) {
        val humanizedPercentage by lazy { percentage.humanizePercentage() }
    }
}
