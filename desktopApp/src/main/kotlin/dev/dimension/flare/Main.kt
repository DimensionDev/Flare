package dev.dimension.flare

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.dimension.flare.common.DeeplinkHandler
import dev.dimension.flare.common.NoopIPC
import dev.dimension.flare.common.SandboxHelper
import dev.dimension.flare.common.windows.WindowsIPC
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.di.composeUiModule
import dev.dimension.flare.di.desktopModule
import dev.dimension.flare.ui.route.APPSCHEMA
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.ProvideComposeWindow
import dev.dimension.flare.ui.theme.ProvideThemeSettings
import io.github.kdroidfilter.platformtools.darkmodedetector.windows.setWindowsAdaptiveTitleBar
import it.sauronsoftware.junique.AlreadyLockedException
import it.sauronsoftware.junique.JUnique
import org.apache.commons.lang3.SystemUtils
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

fun main(args: Array<String>) {
    if (SystemUtils.IS_OS_LINUX && isRunning(args)) {
        return
    }
    if (SystemUtils.IS_OS_LINUX) {
        ensureMimeInfo()
        ensureDesktopEntry()
    }
    SandboxHelper.configureSandboxArgs()
    val ports = WindowsIPC.parsePorts(args)
    val platformIPC =
        if (ports != null) {
            WindowsIPC(
                ports,
                onDeeplink = {
                    DeeplinkHandler.handleDeeplink(it)
                },
            )
        } else {
            NoopIPC
        }
    startKoin {
        modules(
            desktopModule + KoinHelper.modules() + composeUiModule +
                module {
                    single { platformIPC }
                },
        )
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
            Window(
                onCloseRequest = {
                    exitApplication()
                    platformIPC.sendShutdown()
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.flare_logo),
                state =
                    rememberWindowState(
                        position = WindowPosition(Alignment.Center),
                        size = DpSize(520.dp, 840.dp),
                    ),
            ) {
                if (SystemUtils.IS_OS_WINDOWS) {
                    window.setWindowsAdaptiveTitleBar()
                }
                FlareTheme {
                    ProvideComposeWindow {
                        FlareApp()
                    }
                }
            }
        }
    }
}

private const val ENTRY_FILE_NAME = "flare.desktop"
private const val LOCK_ID = "dev.dimensiondev.flare"

private fun ensureDesktopEntry() {
    val entryFile =
        File("${System.getProperty("user.home")}/.local/share/applications/$ENTRY_FILE_NAME")
    if (!entryFile.exists()) {
        entryFile.createNewFile()
    }
    val path = Files.readSymbolicLink(Paths.get("/proc/self/exe"))
    entryFile.writeText(
        "[Desktop Entry]${System.lineSeparator()}" +
            "Type=Application${System.lineSeparator()}" +
            "Name=Flare${System.lineSeparator()}" +
            "Icon=\"${path.parent.parent.absolutePathString() + "/lib/Flare.png" + "\""}${System.lineSeparator()}" +
            "Exec=\"${path.absolutePathString() + "\" %u"}${System.lineSeparator()}" +
            "Terminal=false${System.lineSeparator()}" +
            "Categories=Network;Internet;${System.lineSeparator()}" +
            "MimeType=application/x-$APPSCHEMA;x-scheme-handler/$APPSCHEMA;",
    )
}

private fun ensureMimeInfo() {
    val file = File("${System.getProperty("user.home")}/.local/share/applications/mimeinfo.cache")
    if (!file.exists()) {
        file.createNewFile()
    }
    val text = file.readText()
    if (text.isEmpty() || text.isBlank()) {
        file.writeText("[MIME Cache]${System.lineSeparator()}")
    }
    if (!file.readText().contains("x-scheme-handler/$APPSCHEMA=$ENTRY_FILE_NAME;")) {
        file.appendText("${System.lineSeparator()}x-scheme-handler/$APPSCHEMA=$ENTRY_FILE_NAME;")
    }
}

private fun isRunning(args: Array<String>): Boolean {
    val running =
        try {
            JUnique.acquireLock(LOCK_ID) {
                DeeplinkHandler.handleDeeplink(it)
                null
            }
            false
        } catch (e: AlreadyLockedException) {
            true
        }
    if (running) {
        args.forEach {
            JUnique.sendMessage(LOCK_ID, it)
        }
    }
    return running
}
