package dev.dimension.flare.flareui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.flareui.Button
import dev.dimension.flare.flareui.Column
import dev.dimension.flare.flareui.FlareUiComposable
import dev.dimension.flare.flareui.Icon
import dev.dimension.flare.flareui.Row
import dev.dimension.flare.flareui.Text
import dev.dimension.flare.flareui.demo.shared.resources.DemoResources

/**
 * A small stateful screen shared by every demo backend.
 */
@Composable
@FlareUiComposable
public fun FlareUiDemo() {
    var count by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }

    Column {
        Row {
            Icon(
                image = DemoResources.Images.flareMark,
                contentDescription = DemoResources.Strings.logoDescription,
            )
            Text(DemoResources.Strings.title)
        }
        Text(DemoResources.Strings.subtitle)
        Row {
            Button(
                label = "−",
                enabled = count > 0,
                onClick = { count -= 1 },
            )
            Text("Count: $count")
            Button(
                label = "+",
                onClick = { count += 1 },
            )
        }
        Row {
            Button(
                label =
                    if (showDetails) {
                        DemoResources.Strings.hideDetails
                    } else {
                        DemoResources.Strings.showDetails
                    },
                onClick = { showDetails = !showDetails },
            )
            Button(
                label = DemoResources.Strings.reset,
                enabled = count != 0,
                onClick = { count = 0 },
            )
        }
        if (showDetails) {
            Text(DemoResources.Strings.details)
        }
    }
}
