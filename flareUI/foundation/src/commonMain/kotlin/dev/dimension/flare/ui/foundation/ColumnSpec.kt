package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlarePrimitive
import dev.dimension.flare.ui.FlareUiComposable

@FlarePrimitive
public interface ColumnSpec {
    @Composable
    @FlareUiComposable
    public operator fun invoke(
        modifier: FlareModifier = FlareModifier,
        content: FlareContent,
    )
}
