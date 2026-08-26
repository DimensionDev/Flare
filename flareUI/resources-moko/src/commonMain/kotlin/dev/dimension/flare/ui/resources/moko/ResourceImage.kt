@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.resources.moko

import androidx.compose.runtime.Composable
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget

/** Renderer contract for the optional resource image primitive. */
public interface ResourceImageWidget : FlareWidget {
    public fun setImage(value: FlareImage)

    public fun setContentDescription(value: String?)
}

/** Displays an image returned by [imageResource] without changing Foundation's API. */
@Composable
@FlareUiComposable
public fun ResourceImage(
    image: FlareImage,
    contentDescription: String?,
    modifier: FlareModifier = FlareModifier.None,
) {
    EmitFlareWidget(
        componentType = ResourceImageWidget::class,
        modifier = modifier,
        update = {
            set(image, ResourceImageWidget::setImage)
            set(contentDescription, ResourceImageWidget::setContentDescription)
        },
    )
}
