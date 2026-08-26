package dev.dimension.flare.flareui.demo.android

import android.app.Activity
import android.os.Bundle
import dev.dimension.flare.ui.demo.createAndroidViewDemoView

public class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Flare UI Demo"
        setContentView(createAndroidViewDemoView(this))
    }
}
