package dev.dimension.flare.ui.demo

import android.app.Activity
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
@Config(sdk = [35], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
public class FlareDemoAndroidViewResourcesTest {
    @Test
    public fun rendersAndUpdatesGeneratedResources() {
        withAttachedHost { column ->
            val image = column.getChildAt(0) as ShapeableImageView
            val count = column.getChildAt(3) as MaterialTextView
            val updates = column.getChildAt(4) as MaterialTextView
            val actions = column.getChildAt(5) as LinearLayout
            val increment = actions.getChildAt(0) as MaterialButton

            assertNotNull(image.drawable)
            assertEquals("Flare resource image", image.contentDescription)
            assertEquals("Count: 0", count.text.toString())
            assertEquals("0 updates", updates.text.toString())
            assertEquals(Gravity.TOP or Gravity.START, column.gravity)
            assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, actions.gravity)
            assertEquals(12.dp(column), column.dividerDrawable.intrinsicHeight)
            assertEquals(12.dp(actions), actions.dividerDrawable.intrinsicWidth)

            val lazyRow = column.findViewWithTag<RecyclerView>("demo-lazy-row")
            val lazyColumn = column.findViewWithTag<RecyclerView>("demo-lazy-column")
            assertNotNull(lazyRow)
            assertNotNull(lazyColumn)
            assertEquals(50, checkNotNull(lazyRow.adapter).itemCount)
            assertEquals(10_000, checkNotNull(lazyColumn.adapter).itemCount)

            increment.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

            assertSame(count, column.getChildAt(3))
            assertEquals("Count: 1", count.text.toString())
            assertEquals("1 update", updates.text.toString())
            assertEquals(10_001, checkNotNull(lazyColumn.adapter).itemCount)
        }
    }

    @Test
    @Config(qualifiers = "zh")
    public fun rendersChineseCatalog() {
        withAttachedHost { column ->
            val title = column.getChildAt(1) as MaterialTextView
            val count = column.getChildAt(3) as MaterialTextView
            val updates = column.getChildAt(4) as MaterialTextView
            val actions = column.getChildAt(5) as LinearLayout
            val increment = actions.getChildAt(0) as MaterialButton

            assertEquals("Flare UI 渲染运行时", title.text.toString())
            assertEquals("计数：0", count.text.toString())
            assertEquals("已更新 0 次", updates.text.toString())
            assertEquals("增加", increment.text.toString())
        }
    }

    private fun withAttachedHost(block: (LinearLayout) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val context = ContextThemeWrapper(activity, MaterialR.style.Theme_Material3_DayNight)
        val host = createAndroidViewDemoView(context) as FlareAndroidViewHost

        try {
            activity.setContentView(host)
            shadowOf(Looper.getMainLooper()).idle()

            val column = host.getChildAt(0) as LinearLayout
            block(column)
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun Int.dp(view: android.view.View): Int = (this * view.resources.displayMetrics.density).roundToInt()
}
