package dev.dimension.flare.flareui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.UiComposable

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderRowCompose(
    props: Unit,
    children: List<ComposeTreeNode>,
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        renderComposeChildren(children)
    }
}
