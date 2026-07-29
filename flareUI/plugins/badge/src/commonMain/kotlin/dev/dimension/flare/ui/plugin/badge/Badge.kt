package dev.dimension.flare.ui.plugin.badge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlarePrimitive
import dev.dimension.flare.ui.FlareUiComposable

@Immutable
public enum class BadgeTone {
    Neutral,
    Positive,
    Warning,
}

@FlarePrimitive
public interface BadgeSpec {
    @Composable
    @FlareUiComposable
    public operator fun invoke(
        text: String,
        modifier: FlareModifier = FlareModifier,
        tone: BadgeTone = BadgeTone.Neutral,
        onClick: () -> Unit = {},
    )
}
