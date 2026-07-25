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
import dev.dimension.flare.flareui.Row
import dev.dimension.flare.flareui.Text

/**
 * A small stateful screen shared by every demo backend.
 */
@Composable
@FlareUiComposable
public fun FlareUiDemo() {
    var count by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(false) }

    Column {
        Text("Flare UI")
        Text("One definition, five native renderers")
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
                label = if (showDetails) "Hide details" else "Show details",
                onClick = { showDetails = !showDetails },
            )
            Button(
                label = "Reset",
                enabled = count != 0,
                onClick = { count = 0 },
            )
        }
        if (showDetails) {
            Text("The state and events live in shared Compose Runtime code.")
        }
    }
}
