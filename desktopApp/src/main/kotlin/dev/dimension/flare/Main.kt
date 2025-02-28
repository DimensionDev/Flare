package dev.dimension.flare

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.jthemedetecor.OsThemeDetector
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.di.desktopModule
import dev.dimension.flare.ui.route.FloatingWindowState
import dev.dimension.flare.ui.route.WindowRoute
import dev.dimension.flare.ui.route.WindowRouter
import dev.dimension.flare.ui.theme.FlareTheme
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import java.awt.Desktop
import java.util.function.Consumer

private val detector = OsThemeDetector.getDetector()

fun main(args: Array<String>) {
    startKoin {
        modules(desktopModule + KoinHelper.modules())
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
            route: WindowRoute,
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
        var isDarkTheme by remember {
            mutableStateOf(detector.isDark)
        }
        val listener =
            remember {
                Consumer<Boolean> { isDark ->
                    isDarkTheme = isDark
                }
            }
        DisposableEffect(Unit) {
            detector.registerListener(listener)
            onDispose {
                detector.removeListener(listener)
            }
        }
        val navController = rememberNavController()
        LaunchedEffect(Unit) {
            if (SystemUtils.IS_OS_MAC_OSX) {
                Desktop.getDesktop().setOpenURIHandler {
                    navController.navigate(it.uri.toString())
                }
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.flare_logo),
            state =
                rememberWindowState(
                    position = WindowPosition(Alignment.Center),
                    size = DpSize(1280.dp, 720.dp),
                ),
        ) {
            FlareTheme(
                isDarkTheme = isDarkTheme,
            ) {
                FlareApp(
                    navController = navController,
                    onRawImage = { url ->
                        openWindow(
                            url,
                            WindowRoute.RawImage(url),
                        )
                    },
                    onStatusMedia = { accountType, statusKey, index ->
                        openWindow(
                            "$accountType/$statusKey",
                            WindowRoute.StatusMedia(
                                accountType = accountType,
                                statusKey = statusKey,
                                index = index,
                            ),
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
                content = {
                    LaunchedEffect(key) {
                        value.bringToFront = {
                            window.toFront()
                        }
                    }
                    FlareTheme(isDarkTheme = isDarkTheme) {
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
