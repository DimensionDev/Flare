@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.demo

import android.content.Context
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import dev.dimension.flare.ui.android.AndroidViewLazyLayoutRendererPlugin
import dev.dimension.flare.ui.android.AndroidViewNavigationOwner
import dev.dimension.flare.ui.android.AndroidViewNavigationRendererPlugin
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.compose.AndroidComposeLazyLayoutRendererPlugin
import dev.dimension.flare.ui.compose.AndroidComposeNavigationRendererPlugin
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.resources.moko.AndroidComposeMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.AndroidMokoResourceResolver
import dev.dimension.flare.ui.resources.moko.AndroidViewMokoResourcesRendererPlugin
import dev.dimension.flare.ui.resources.moko.ProvideMokoResources

/** Creates the demo with the Android View renderer backend. */
public fun createAndroidViewDemoView(context: FragmentActivity): View {
    val resolver = AndroidMokoResourceResolver(context)
    return FlareAndroidViewHost(
        context = context,
        widgetSystem =
            createAndroidWidgetSystem(
                AndroidViewMokoResourcesRendererPlugin,
                AndroidViewLazyLayoutRendererPlugin,
                AndroidViewNavigationRendererPlugin,
            ),
        nativeControllerOwner = AndroidViewNavigationOwner(context),
    ).apply {
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
            AndroidComposeNavigationRendererPlugin,
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
                Surface(modifier = Modifier.fillMaxSize()) {
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
