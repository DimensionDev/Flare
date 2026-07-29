package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlarePrimitive
import dev.dimension.flare.ui.FlareUiComposable

@FlarePrimitive
public interface TextSpec {
    @Composable
    @FlareUiComposable
    public operator fun invoke(
        text: String,
        modifier: FlareModifier = FlareModifier,
    )
}
