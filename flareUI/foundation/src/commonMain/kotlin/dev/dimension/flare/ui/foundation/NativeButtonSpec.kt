package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlarePrimitive
import dev.dimension.flare.ui.FlareUiComposable

@FlarePrimitive
public interface NativeButtonSpec {
    @Composable
    @FlareUiComposable
    public operator fun invoke(
        label: String,
        modifier: FlareModifier = FlareModifier,
        enabled: Boolean = true,
        onClick: () -> Unit,
    )
}
