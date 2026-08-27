package dev.dimension.flare.ui.demo

import android.content.Context
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.android.AndroidViewLazyLayoutRendererPlugin
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.compose.AndroidComposeLazyLayoutRendererPlugin
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.resources.moko.AndroidComposeMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.AndroidMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.AndroidViewMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources

/** Creates the demo with the Android View renderer backend. */
public fun createAndroidViewDemoView(context: Context): View {
    val padding = (24 * context.resources.displayMetrics.density).toInt()
    val resolver = AndroidMokoResourceResolver(context)
    return FlareAndroidViewHost(
        context = context,
        widgetSystem =
            createAndroidWidgetSystem(
                AndroidViewMokoResourcesRendererPlugin,
                AndroidViewLazyLayoutRendererPlugin,
            ),
    ).apply {
        setPadding(padding, padding, padding, padding)
        setContent {
            ProvideMokoResources(resolver) {
                FlareDemoContent()
            }
        }
    }
}

/** Creates the same demo with the Android Compose renderer backend. */
public fun createAndroidComposeDemoView(context: Context): View {
    val widgetSystem =
        createAndroidComposeWidgetSystem(
            AndroidComposeMokoResourcesRendererPlugin,
            AndroidComposeLazyLayoutRendererPlugin,
        )
    return ComposeView(context).apply {
        setContent {
            val currentContext = LocalContext.current
            val configuration = LocalConfiguration.current
            val resolver =
                remember(currentContext, configuration) {
                    AndroidMokoResourceResolver(currentContext)
                }
            MaterialTheme(
                colorScheme =
                    if (isSystemInDarkTheme()) {
                        darkColorScheme()
                    } else {
                        lightColorScheme()
                    },
            ) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(DEMO_CONTENT_PADDING)) {
                        FlareComposeHost(widgetSystem = widgetSystem) {
                            ProvideMokoResources(resolver) {
                                FlareDemoContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DEMO_CONTENT_PADDING = 24.dp
