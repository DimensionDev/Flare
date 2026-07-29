package dev.dimension.flare.ui.demo

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.plugin.badge.AndroidComposeBadgeRendererPlugin
import dev.dimension.flare.ui.plugin.badge.AndroidViewBadgeRendererPlugin

/** Creates the demo with the Android View renderer backend. */
public fun createAndroidViewDemoView(context: Context): View {
    val padding = (24 * context.resources.displayMetrics.density).toInt()
    return FlareAndroidViewHost(
        context = context,
        widgetSystem =
            createAndroidWidgetSystem(
                AndroidViewBadgeRendererPlugin,
            ),
    ).apply {
        setPadding(padding, padding, padding, padding)
        setContent {
            FlareDemoContent()
        }
    }
}

/** Creates the same shared demo content with the Android Compose renderer backend. */
public fun createAndroidComposeDemoView(context: Context): View =
    ComposeView(context).apply {
        setContent {
            val widgetSystem =
                remember {
                    createAndroidComposeWidgetSystem(
                        AndroidComposeBadgeRendererPlugin,
                    )
                }
            FlareComposeHost(
                widgetSystem = widgetSystem,
                modifier = Modifier.padding(24.dp),
            ) {
                FlareDemoContent()
            }
        }
    }
