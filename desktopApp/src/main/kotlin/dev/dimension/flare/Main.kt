package dev.dimension.flare

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.dimension.flare.common.DeeplinkHandler
import dev.dimension.flare.common.SandboxHelper
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.di.composeUiModule
import dev.dimension.flare.di.desktopModule
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.ProvideComposeWindow
import dev.dimension.flare.ui.theme.ProvideThemeSettings
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.NavigationDefaults
import io.github.kdroidfilter.nucleus.aot.runtime.AotRuntime
import io.github.kdroidfilter.nucleus.core.runtime.DeepLinkHandler
import io.github.kdroidfilter.nucleus.core.runtime.SingleInstanceManager
import io.github.kdroidfilter.nucleus.window.DecoratedWindow
import io.github.kdroidfilter.nucleus.window.TitleBar
import io.github.kdroidfilter.nucleus.window.styling.LocalTitleBarStyle
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun main(args: Array<String>) {
    if (AotRuntime.isTraining()) {
        Thread({
            Thread.sleep(15.seconds.toJavaDuration())
            exitProcess(0)
        }, "aot-timer").apply {
            isDaemon = false
            start()
        }
    }
    val restoreRequestFlow = MutableStateFlow(0)
    DeepLinkHandler.register(args) { uri ->
        DeeplinkHandler.handleDeeplink(uri.toString())
    }
    val isFirstInstance =
        SingleInstanceManager.isSingleInstance(
            onRestoreFileCreated = { DeepLinkHandler.writeUriTo(this) },
            onRestoreRequest = {
                DeepLinkHandler.readUriFrom(this)
                restoreRequestFlow.value++
            },
        )
    if (!isFirstInstance) {
        return
    }
    SandboxHelper.configureSandboxArgs()
    startKoin {
        modules(
            desktopModule + KoinHelper.modules() + composeUiModule,
        )
    }
    application {
        setSingletonImageLoaderFactory { context ->
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        KtorNetworkFetcherFactory(
                            httpClient =
                                ktorClient {
                                    useDefaultTransformers = false
                                },
                        ),
                    )
                }.crossfade(true)
                .build()
        }
        ProvideThemeSettings {
            DecoratedWindow(
                onCloseRequest = {
                    exitApplication()
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.flare_logo),
                state =
                    rememberWindowState(
                        position = WindowPosition(Alignment.Center),
                        size = DpSize(520.dp, 840.dp),
                    ),
            ) {
                val restoreRequest by restoreRequestFlow.collectAsState()
                LaunchedEffect(restoreRequest) {
                    if (restoreRequest > 0) {
                        window.toFront()
                        window.requestFocus()
                    }
                }
                val backButtonState =
                    remember {
                        NavigationBackButtonState()
                    }
                FlareTheme {
                    ProvideComposeWindow {
                        FlareApp(
                            backButtonState = backButtonState,
                        )
                    }
                    TitleBar(
                        style =
                            LocalTitleBarStyle.current.copy(
                                colors =
                                    LocalTitleBarStyle.current.colors.copy(
                                        background =
                                            FluentTheme.colors.background.mica.base
                                                .copy(alpha = 0f),
                                        inactiveBackground = Color.Transparent,
                                    ),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            NavigationDefaults.BackButton(
                                onClick = {
                                    backButtonState.onClick.invoke()
                                },
                                modifier =
                                    Modifier
                                        .let {
                                            if (SystemUtils.IS_OS_WINDOWS) {
                                                it.width(70.dp)
                                            } else {
                                                it
                                            }
                                        },
                                disabled = !backButtonState.canGoBack,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal class NavigationBackButtonState {
    var canGoBack by mutableStateOf(false)
        private set
    var onClick: () -> Unit = {}
        private set

    fun attach(onClick: () -> Unit) {
        this.onClick = onClick
    }

    fun update(canGoBack: Boolean) {
        this.canGoBack = canGoBack
    }
}
