package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.ui.humanizer.humanizePercentage
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
@Immutable
public data class UiPoll internal constructor(
    val id: String,
    val options: SerializableImmutableList<Option>,
    val multiple: Boolean,
    val ownVotes: SerializableImmutableList<Int>,
    private val voteEvent: PostEvent.PollEvent?,
    // null indicates no expiration
    private val expiresAt: Instant?,
    private val enabled: Boolean = true,
) {
    val expired: Boolean by lazy {
        if (expiresAt == null) {
            false
        } else {
            expiresAt < Clock.System.now()
        }
    }
    val voted: Boolean by lazy { ownVotes.isNotEmpty() }
    val expiredAt: UiDateTime? by lazy { expiresAt?.toUi() }
    val onVote: ClickContext.(
        selectedOptionIndexes: ImmutableList<Int>,
    ) -> Unit by lazy {
        { selectedOptionIndexes ->
            handleVote(
                clickContext = this,
                selectedOptionIndexes = selectedOptionIndexes,
            )
        }
    }

    val canVote: Boolean by lazy { !expired && !voted && enabled }

    @Serializable
    @Immutable
    public data class Option internal constructor(
        val title: String,
        val votesCount: Long,
        val percentage: Float,
    ) {
        val humanizedPercentage: String by lazy { percentage.humanizePercentage() }
    }

    private fun handleVote(
        clickContext: ClickContext,
        selectedOptionIndexes: ImmutableList<Int>,
    ) {
        if (voteEvent != null) {
            ClickEvent
                .event(
                    accountKey = voteEvent.accountKey,
                    postEvent = voteEvent.copyWithOptions(options = selectedOptionIndexes),
                ).onClicked
                .invoke(clickContext)
        }
    }
}
