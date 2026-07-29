package dev.dimension.flare.ui.plugin.badge

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.testTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
class AndroidBadgePluginTest {
    @Test
    fun installsExternalNativePrimitiveAndUpdatesItInPlace() {
        val controller =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
        val activity = controller.get()
        val host =
            FlareAndroidViewHost(
                context = activity,
                widgetSystem =
                    createAndroidWidgetSystem(
                        AndroidViewBadgeRendererPlugin,
                    ),
            )
        try {
            activity.setContentView(host)

            host.setContent {
                var count by remember { mutableIntStateOf(0) }
                Column {
                    Text("Independent primitive plugin")
                    Badge(
                        text = "Badge $count",
                        modifier = FlareModifier.testTag("badge"),
                        tone = BadgeTone.Positive,
                        onClick = { count += 1 },
                    )
                }
            }
            shadowOf(Looper.getMainLooper()).idle()

            val column = host.getChildAt(0) as LinearLayout
            val badge = column.getChildAt(1) as TextView
            assertEquals("Badge 0", badge.text.toString())
            assertEquals("badge", badge.tag)

            badge.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

            assertSame(badge, column.getChildAt(1))
            assertEquals("Badge 1", badge.text.toString())
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun createsCompositionOnlyWhileAttachedAndRestoresItOnReattach() {
        val controller =
            Robolectric
                .buildActivity(Activity::class.java)
                .setup()
        val activity = controller.get()
        val host =
            FlareAndroidViewHost(
                context = activity,
                widgetSystem = createAndroidWidgetSystem(),
            )

        try {
            host.setContent {
                Column {
                    Text("Lifecycle content")
                }
            }

            assertFalse(host.isAttachedToWindow)
            assertEquals(0, host.childCount)

            activity.setContentView(host)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(host.isAttachedToWindow)
            assertEquals(1, host.childCount)

            activity.setContentView(FrameLayout(activity))
            shadowOf(Looper.getMainLooper()).idle()

            assertFalse(host.isAttachedToWindow)
            assertEquals(0, host.childCount)

            activity.setContentView(host)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(host.isAttachedToWindow)
            assertEquals(1, host.childCount)
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
