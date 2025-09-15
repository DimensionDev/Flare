package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.composefluent.component.LiteFilter
import io.github.composefluent.component.PillButton
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableList

@Composable
internal actual fun PlatformPicker(
    options: ImmutableList<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    LiteFilter(
        modifier = modifier,
    ) {
        options.forEachIndexed { index, option ->
            PillButton(
                selected = selectedIndex == index,
                onSelectedChanged = {
                    if (it) {
                        selectedIndex = index
                        onSelected(index)
                    }
                },
                content = {
                    Text(option)
                },
            )
        }
    }
}
