package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.humanize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
public data class UiList internal constructor(
    val id: String,
    val title: String,
    val description: String? = null,
    val avatar: String? = null,
    val creator: UiUserV2? = null,
    val likedCount: Long = 0,
    val liked: Boolean = false,
    val platformType: PlatformType,
    val type: Type = Type.List,
    val readonly: Boolean = false,
) {
    val likedCountHumanized: String by lazy {
        likedCount.humanize()
    }

    val digits: ImmutableList<Digit>
        get() =
            likedCountHumanized
                .mapIndexed { index, char ->
                    Digit(char, index, likedCount)
                }.toImmutableList()

    public enum class Type {
        Feed,
        List,
        Antenna,
    }
}
