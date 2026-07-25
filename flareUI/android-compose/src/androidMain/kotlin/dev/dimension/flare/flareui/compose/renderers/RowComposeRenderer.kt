package dev.dimension.flare.flareui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.UiComposable
import dev.dimension.flare.flareui.AndroidFlareResourceResolver

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderRowCompose(
    props: Unit,
    children: List<ComposeTreeNode>,
    resources: AndroidFlareResourceResolver,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        renderComposeChildren(children, resources)
    }
}
