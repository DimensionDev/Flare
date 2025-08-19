package dev.dimension.flare

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import dev.dimension.flare.common.DeeplinkHandler
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.di.desktopModule
import dev.dimension.flare.ui.route.FloatingWindowState
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.WindowRouter
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.ProvideThemeSettings
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import java.awt.Desktop

fun main(args: Array<String>) {
    startKoin {
        modules(desktopModule + KoinHelper.modules())
    }
    if (SystemUtils.IS_OS_MAC_OSX) {
        Desktop.getDesktop().setOpenURIHandler {
            DeeplinkHandler.handleDeeplink(it.uri.toString())
        }
    }
    application {
        setSingletonImageLoaderFactory { context ->
            ImageLoader
                .Builder(context)
                .crossfade(true)
                .build()
        }
        val extraWindowRoutes = remember { mutableStateMapOf<String, FloatingWindowState>() }

        fun openWindow(
            key: String,
            route: Route.WindowRoute,
        ) {
            if (extraWindowRoutes.containsKey(key)) {
                extraWindowRoutes[key]?.bringToFront?.invoke()
            } else {
                extraWindowRoutes.put(
                    key,
                    FloatingWindowState(route),
                )
            }
        }
        ProvideThemeSettings {
            Window(
                onCloseRequest = ::exitApplication,
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.flare_logo),
                state =
                    rememberWindowState(
                        position = WindowPosition(Alignment.Center),
                        size = DpSize(520.dp, 840.dp),
                    ),
            ) {
                FlareTheme {
                    FlareApp(
                        onWindowRoute = {
                            openWindow(
                                it.toString(),
                                it,
                            )
                        },
                    )
                }
            }

            extraWindowRoutes.forEach { (key, value) ->
                Window(
                    title = stringResource(Res.string.app_name),
                    icon = painterResource(Res.drawable.flare_logo),
                    onCloseRequest = {
                        extraWindowRoutes.remove(key)
                    },
                    onKeyEvent = {
                        if (it.key == Key.Escape) {
                            extraWindowRoutes.remove(key)
                            true
                        } else {
                            false
                        }
                    },
                    content = {
                        LaunchedEffect(key) {
                            value.bringToFront = {
                                window.toFront()
                            }
                        }
                        FlareTheme {
                            WindowRouter(
                                route = value.route,
                                onBack = {
                                    extraWindowRoutes.remove(key)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
