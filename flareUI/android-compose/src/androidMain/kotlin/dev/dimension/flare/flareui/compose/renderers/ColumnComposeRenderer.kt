package dev.dimension.flare.flareui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.UiComposable

@Composable
@UiComposable
@Suppress("UNUSED_PARAMETER")
internal fun renderColumnCompose(
    props: Unit,
    children: List<ComposeTreeNode>,
) {
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        renderComposeChildren(children)
    }
}
