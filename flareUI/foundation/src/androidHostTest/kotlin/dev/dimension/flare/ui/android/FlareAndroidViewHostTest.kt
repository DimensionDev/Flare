package dev.dimension.flare.ui.android

import android.app.Activity
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.NativeButton
import dev.dimension.flare.ui.foundation.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
public class FlareAndroidViewHostTest {
    @Test
    public fun rendersAndRecomposesInPlace() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val host =
            FlareAndroidViewHost(
                context = activity,
                widgetSystem = createAndroidWidgetSystem(),
            )

        try {
            activity.setContentView(host)
            host.setContent {
                var count by remember { mutableIntStateOf(0) }
                Column {
                    Text(
                        text = "Count $count",
                        modifier = FlareModifier(testTag = "count"),
                    )
                    NativeButton(
                        label = "Increment",
                        onClick = { count += 1 },
                    )
                }
            }
            shadowOf(Looper.getMainLooper()).idle()

            val column = host.getChildAt(0) as LinearLayout
            val label = column.getChildAt(0) as TextView
            val button = column.getChildAt(1) as Button
            assertEquals("Count 0", label.text.toString())
            assertEquals("count", label.tag)

            button.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

            assertSame(label, column.getChildAt(0))
            assertEquals("Count 1", label.text.toString())
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
