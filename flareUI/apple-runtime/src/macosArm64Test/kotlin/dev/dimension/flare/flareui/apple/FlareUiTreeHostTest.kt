@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.flareui.apple

import androidx.compose.runtime.mutableIntStateOf
import dev.dimension.flare.flareui.Button
import dev.dimension.flare.flareui.Column
import dev.dimension.flare.flareui.Text
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSTimer
import platform.Foundation.create
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FlareUiTreeHostTest {
    @Test
    fun recomposesExternallyOwnedState() {
        val count = mutableIntStateOf(0)
        val host =
            FlareUiTreeHost {
                Column {
                    Text("Count: ${count.intValue}")
                    Button("+") {
                        count.intValue += 1
                    }
                }
            }
        var nodes = emptyList<FlareUiNodeSnapshot>()
        host.setOnTreeChanged { nodes = it }
        drainMainRunLoop()

        (nodes.single().children[1].payload as FlareButtonPayload).performClick()
        assertEquals(1, count.intValue)
        drainMainRunLoop()

        assertEquals(
            "Count: 1",
            (nodes.single().children[0].payload as FlareTextPayload).value.literal,
        )
        host.dispose()
    }

    private fun drainMainRunLoop() {
        NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.05,
            repeats = false,
        ) {}
        NSRunLoop.currentRunLoop.runUntilDate(NSDate.create(timeIntervalSinceNow = 0.1))
    }
}
