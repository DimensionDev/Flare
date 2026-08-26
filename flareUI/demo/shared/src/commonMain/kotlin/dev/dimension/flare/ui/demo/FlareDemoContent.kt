package dev.dimension.flare.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Row
import dev.dimension.flare.ui.foundation.Text

/** The complete demo screen shared by every platform host. */
@Composable
@FlareUiComposable
public fun FlareDemoContent() {
    var count by remember { mutableIntStateOf(0) }

    Column {
        Text("Flare UI renderer runtime")
        Text("One shared composition renders through the selected backend.")
        Text(
            text = "Count: $count",
            modifier = FlareModifier(testTag = "demo-count"),
        )
        Row {
            NativeButton(
                label = "Increment",
                onClick = { count += 1 },
            )
            NativeButton(
                label = "Reset",
                enabled = count != 0,
                onClick = { count = 0 },
            )
        }
    }
}
