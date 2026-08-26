package dev.dimension.flare.ui.demo

import android.content.Context
import android.view.View
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem

/** Creates the demo with the Android View renderer backend. */
public fun createAndroidViewDemoView(context: Context): View {
    val padding = (24 * context.resources.displayMetrics.density).toInt()
    return FlareAndroidViewHost(
        context = context,
        widgetSystem = createAndroidWidgetSystem(),
    ).apply {
        setPadding(padding, padding, padding, padding)
        setContent {
            FlareDemoContent()
        }
    }
}
