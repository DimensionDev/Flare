package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformPicker(
    options: ImmutableList<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
    ) {
        options.forEachIndexed { index, option ->
            toggleableItem(
                checked = selectedIndex == index,
                onCheckedChange = {
                    if (it) {
                        selectedIndex = index
                        onSelected(index)
                    }
                },
                label = option,
            )
        }
    }
}
