package dev.dimension.flare.flareui.demo.android

import android.app.Activity
import android.os.Bundle
import dev.dimension.flare.flareui.demo.FlareUiDemo
import dev.dimension.flare.flareui.view.FlareViewHost

public class ViewDemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(
            FlareViewHost(this).apply {
                setPadding(padding, padding, padding, padding)
                setContent {
                    FlareUiDemo()
                }
            },
        )
    }
}
