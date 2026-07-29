package dev.dimension.flare.flareui.demo.android

import android.app.Activity
import android.os.Bundle
import dev.dimension.flare.ui.demo.createAndroidViewDemoView

/** Runs the shared demo through the Android View backend. */
public class AndroidViewDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Flare UI · Android View"
        setContentView(createAndroidViewDemoView(this))
    }
}
