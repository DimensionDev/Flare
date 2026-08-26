@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget

public interface NativeButtonWidget : FlareWidget {
    public fun setLabel(value: String)

    public fun setEnabled(value: Boolean)

    public fun setOnClick(value: () -> Unit)
}

@Composable
@FlareUiComposable
public fun NativeButton(
    label: String,
    modifier: FlareModifier = FlareModifier.None,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    EmitFlareWidget(
        componentType = NativeButtonWidget::class,
        modifier = modifier,
        update = {
            set(label, NativeButtonWidget::setLabel)
            set(enabled, NativeButtonWidget::setEnabled)
            set(onClick, NativeButtonWidget::setOnClick)
        },
    )
}
