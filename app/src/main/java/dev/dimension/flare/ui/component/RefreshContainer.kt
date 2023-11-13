package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RefreshContainer(
    @Suppress("UNUSED_PARAMETER")
    refreshing: Boolean,
    @Suppress("UNUSED_PARAMETER")
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
) {
    Box(
        modifier =
        modifier,
    ) {
        content.invoke(this)
    }
}
