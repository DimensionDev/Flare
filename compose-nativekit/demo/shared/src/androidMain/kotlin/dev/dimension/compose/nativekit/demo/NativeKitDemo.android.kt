@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit.demo

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
import dev.dimension.compose.nativekit.android.AndroidViewLazyLayoutRendererPlugin
import dev.dimension.compose.nativekit.android.AndroidViewNavigationOwner
import dev.dimension.compose.nativekit.android.AndroidViewNavigationRendererPlugin
import dev.dimension.compose.nativekit.android.NativeKitAndroidViewHost
import dev.dimension.compose.nativekit.android.createAndroidWidgetSystem
import dev.dimension.compose.nativekit.compose.AndroidComposeLazyLayoutRendererPlugin
import dev.dimension.compose.nativekit.compose.AndroidComposeNavigationRendererPlugin
import dev.dimension.compose.nativekit.compose.NativeKitComposeHost
import dev.dimension.compose.nativekit.compose.createAndroidComposeWidgetSystem
import dev.dimension.compose.nativekit.resources.moko.AndroidComposeMokoResourcesRendererPlugin
import dev.dimension.compose.nativekit.resources.moko.AndroidMokoResourceResolver
import dev.dimension.compose.nativekit.resources.moko.AndroidViewMokoResourcesRendererPlugin
import dev.dimension.compose.nativekit.resources.moko.ProvideMokoResources

/** Creates the demo with the Android View renderer backend. */
public fun createAndroidViewDemoView(context: FragmentActivity): View {
    val resolver = AndroidMokoResourceResolver(context)
    return NativeKitAndroidViewHost(
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
                NativeKitDemoContent()
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
                    NativeKitComposeHost(widgetSystem = widgetSystem) {
                        ProvideMokoResources(resolver) {
                            NativeKitDemoContent()
                        }
                    }
                }
            }
        }
    }
}
