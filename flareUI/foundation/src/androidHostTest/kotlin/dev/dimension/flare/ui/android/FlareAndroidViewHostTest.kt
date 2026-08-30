package dev.dimension.flare.ui.android

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.HorizontalAlignment
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
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
public class FlareAndroidViewHostTest {
    @Test
    public fun rendersAndRecomposesInPlace() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val context = ContextThemeWrapper(activity, MaterialR.style.Theme_Material3_DayNight)
        val host =
            FlareAndroidViewHost(
                context = context,
                widgetSystem = createAndroidWidgetSystem(),
            )

        try {
            activity.setContentView(host)
            host.setContent {
                var count by remember { mutableIntStateOf(0) }
                Column(
                    spacing = 12f,
                    horizontalAlignment = HorizontalAlignment.End,
                ) {
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
            val label = column.getChildAt(0) as MaterialTextView
            val button = column.getChildAt(1) as MaterialButton
            assertEquals("Count 0", label.text.toString())
            assertEquals("count", label.tag)
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, column.layoutParams.width)
            assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, label.layoutParams.width)
            assertEquals(Gravity.TOP or Gravity.END, column.gravity)
            assertEquals(
                (12 * activity.resources.displayMetrics.density).roundToInt(),
                column.dividerDrawable.intrinsicHeight,
            )

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
