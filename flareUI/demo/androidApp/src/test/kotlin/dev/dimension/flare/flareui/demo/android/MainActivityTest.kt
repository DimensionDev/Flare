package dev.dimension.flare.flareui.demo.android

import android.R
import android.view.View
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
public class MainActivityTest {
    @Test
    public fun launchProvidesLifecycleOwnerToComposeViewTree() {
        val activity =
            try {
                Robolectric.buildActivity(MainActivity::class.java).setup().get()
            } catch (error: IllegalStateException) {
                if (error.message?.contains("ViewTreeLifecycleOwner not found") == true) {
                    fail("MainActivity did not install a ViewTreeLifecycleOwner before attaching ComposeView")
                }
                throw error
            }
        val content = activity.findViewById<View>(R.id.content)

        assertNotNull(content)
    }
}
