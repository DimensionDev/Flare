package dev.dimension.flare.flareui.demo.android

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class MainActivityTest {
    @Test
    public fun selectorStartsAndroidViewDemo() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<MaterialButton>(R.id.open_android_view).performClick()

        assertDemoIntent(shadowOf(activity).nextStartedActivity, DemoBackend.ANDROID_VIEW)
    }

    @Test
    public fun selectorStartsComposeDemo() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.findViewById<MaterialButton>(R.id.open_android_compose).performClick()

        assertDemoIntent(shadowOf(activity).nextStartedActivity, DemoBackend.COMPOSE)
    }

    @Test
    @Config(qualifiers = "zh")
    public fun selectorUsesChineseBackendLabels() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        assertEquals(
            "Android View 后端",
            activity.findViewById<MaterialButton>(R.id.open_android_view).text.toString(),
        )
        assertEquals(
            "Android Compose 后端",
            activity.findViewById<MaterialButton>(R.id.open_android_compose).text.toString(),
        )
    }

    @Test
    public fun androidViewDemoInstallsOnlyAndroidViewHost() {
        val activity = buildDemoActivity(DemoBackend.ANDROID_VIEW)

        val host = contentChild(activity) as FlareAndroidViewHost
        assertEquals(0, host.paddingLeft)
        assertEquals(0, host.paddingTop)
        assertEquals(0, host.paddingRight)
        assertEquals(0, host.paddingBottom)
    }

    @Test
    public fun composeDemoInstallsOnlyComposeHostWithLifecycleOwner() {
        val activity =
            try {
                buildDemoActivity(DemoBackend.COMPOSE)
            } catch (error: IllegalStateException) {
                if (error.message?.contains("ViewTreeLifecycleOwner not found") == true) {
                    fail("DemoActivity did not install a ViewTreeLifecycleOwner before attaching ComposeView")
                }
                throw error
            }

        val host = contentChild(activity)
        assertEquals(
            "androidx.compose.ui.platform.ComposeView",
            host.javaClass.name,
        )
    }

    private fun buildDemoActivity(backend: DemoBackend): DemoActivity =
        Robolectric
            .buildActivity(
                DemoActivity::class.java,
                DemoActivity.createIntent(RuntimeEnvironment.getApplication(), backend),
            ).setup()
            .get()

    private fun contentChild(activity: DemoActivity): View {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertNotNull(content)
        assertEquals(1, content.childCount)
        return content.getChildAt(0)
    }

    private fun assertDemoIntent(
        intent: Intent,
        backend: DemoBackend,
    ) {
        assertEquals(DemoActivity::class.java.name, intent.component?.className)
        assertEquals(backend.intentValue, intent.getStringExtra(DemoActivity.EXTRA_BACKEND))
    }
}
