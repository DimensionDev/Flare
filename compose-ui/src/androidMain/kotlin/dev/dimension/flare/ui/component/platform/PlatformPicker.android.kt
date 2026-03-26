package dev.dimension.flare.ui.component.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

@Composable
internal actual fun PlatformPicker(
    options: ImmutableList<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = {
                    selectedIndex = index
                    onSelected(index)
                },
                label = {
                    Text(option)
                },
            )
        }
    }
}
