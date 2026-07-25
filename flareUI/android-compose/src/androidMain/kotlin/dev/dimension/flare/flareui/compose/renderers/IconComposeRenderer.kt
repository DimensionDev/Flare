package dev.dimension.flare.flareui.compose

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.UiComposable
import androidx.compose.ui.res.painterResource
import dev.dimension.flare.flareui.AndroidFlareResourceResolver
import dev.dimension.flare.flareui.IconProps

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderIconCompose(
    props: IconProps,
    children: List<ComposeTreeNode>,
    resources: AndroidFlareResourceResolver,
) {
    Icon(
        painter = painterResource(resources.imageId(props.image)),
        contentDescription =
            props.contentDescription?.let { description ->
                resolveFlareText(description, resources)
            },
    )
}
