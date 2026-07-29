package dev.dimension.flare.flareui.demo.android

import androidx.activity.ComponentActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeDemoActivityTest {
    @Test
    fun usesLifecycleAwareActivityHost() {
        assertTrue(
            ComponentActivity::class.java.isAssignableFrom(
                ComposeDemoActivity::class.java,
            ),
        )
    }
}
