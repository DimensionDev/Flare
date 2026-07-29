package dev.dimension.flare.flareui.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.dimension.flare.ui.demo.createAndroidComposeDemoView

/** Runs the shared demo through the Jetpack Compose backend. */
public class ComposeDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Flare UI · Jetpack Compose"
        setContentView(createAndroidComposeDemoView(this))
    }
}
