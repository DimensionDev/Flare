package dev.dimension.flare.flareui.view

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.flareui.Column
import dev.dimension.flare.flareui.Row
import dev.dimension.flare.flareui.Text
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import kotlin.math.roundToInt
import dev.dimension.flare.flareui.Button as FlareButton

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class FlareViewHostTest {
    @Test
    fun rendersNativeViewsAndRecomposesAfterClick() {
        val activity =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
                .get()
                .apply {
                    setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
                }
        val host = FlareViewHost(activity)
        activity.setContentView(host)

        host.setContent {
            var count by remember { mutableIntStateOf(0) }
            var showDetails by remember { mutableStateOf(false) }

            Column {
                Text("Flare UI")
                Text("One definition, five native renderers")
                Row {
                    FlareButton(
                        label = "−",
                        enabled = count > 0,
                        onClick = { count -= 1 },
                    )
                    Text("Count: $count")
                    FlareButton(
                        label = "+",
                        onClick = { count += 1 },
                    )
                }
                Row {
                    FlareButton(
                        label = if (showDetails) "Hide details" else "Show details",
                        onClick = { showDetails = !showDetails },
                    )
                    FlareButton(
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
        shadowOf(Looper.getMainLooper()).idle()

        val column = host.getChildAt(0) as LinearLayout
        assertEquals(LinearLayout.VERTICAL, column.orientation)
        assertEquals("Flare UI", (column.getChildAt(0) as MaterialTextView).text.toString())
        assertWrapContent(column.getChildAt(0))
        assertEquals(
            "One definition, five native renderers",
            (column.getChildAt(1) as MaterialTextView).text.toString(),
        )

        val counter = column.getChildAt(2) as LinearLayout
        assertEquals(LinearLayout.HORIZONTAL, counter.orientation)
        assertWrapContent(counter)
        val decrement = counter.getChildAt(0) as MaterialButton
        val count = counter.getChildAt(1) as MaterialTextView
        val increment = counter.getChildAt(2) as MaterialButton
        assertWrapContent(decrement)
        assertWrapContent(count)
        assertWrapContent(increment)
        layoutAtIntrinsicSize(column)
        assertEquals(0, column.getChildAt(0).left)
        assertEquals(0, column.getChildAt(1).left)
        assertEquals(0, counter.left)
        assertEquals(column.getChildAt(0).bottom, column.getChildAt(1).top)
        assertEquals(column.getChildAt(1).bottom, counter.top)
        assertEquals(0, decrement.top)
        assertEquals(0, count.top)
        assertEquals(0, increment.top)
        assertEquals(decrement.right, count.left)
        assertEquals(count.right, increment.left)
        val composeButtonMinWidth =
            (
                COMPOSE_MATERIAL3_BUTTON_MIN_WIDTH_DP *
                    activity.resources.displayMetrics.density
            ).roundToInt()
        assertEquals(
            buttonWidthDiagnostics(decrement),
            composeButtonMinWidth,
            decrement.measuredWidth,
        )
        assertEquals(
            buttonWidthDiagnostics(increment),
            composeButtonMinWidth,
            increment.measuredWidth,
        )
        assertEquals(false, decrement.isEnabled)
        assertEquals("Count: 0", count.text.toString())

        increment.performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

        assertEquals("Count: 1", (counter.getChildAt(1) as MaterialTextView).text.toString())
        assertEquals(true, decrement.isEnabled)

        val controls = column.getChildAt(3) as LinearLayout
        (controls.getChildAt(0) as MaterialButton).performClick()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

        assertEquals(
            "The state and events live in shared Compose Runtime code.",
            (column.getChildAt(4) as MaterialTextView).text.toString(),
        )
    }

    private fun assertWrapContent(view: View) {
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.height)

        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        assertEquals(0, params.leftMargin)
        assertEquals(0, params.topMargin)
        assertEquals(0, params.rightMargin)
        assertEquals(0, params.bottomMargin)
    }

    private fun layoutAtIntrinsicSize(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun buttonWidthDiagnostics(button: MaterialButton): String =
        "minimumWidth=${button.minimumWidth}, minWidth=${button.minWidth}, " +
            "padding=${button.paddingLeft}+${button.paddingRight}, " +
            "compoundPadding=${button.compoundPaddingLeft}+${button.compoundPaddingRight}, " +
            "inset=${button.insetLeft}+${button.insetRight}, " +
            "textWidth=${button.paint.measureText(button.text.toString())}"
}
