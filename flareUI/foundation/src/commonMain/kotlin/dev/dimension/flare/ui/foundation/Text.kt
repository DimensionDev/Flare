@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.foundation

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget

public interface TextWidget : FlareWidget {
    public fun setText(value: String)
}

@Composable
@FlareUiComposable
public fun Text(
    text: String,
    modifier: FlareModifier = FlareModifier.None,
) {
    EmitFlareWidget(
        componentType = TextWidget::class,
        modifier = modifier,
        update = {
            set(text, TextWidget::setText)
        },
    )
}
