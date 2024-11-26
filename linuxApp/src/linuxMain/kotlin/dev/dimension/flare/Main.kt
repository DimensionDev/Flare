package dev.dimension.flare

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.di.KoinHelper
import dev.dimension.flare.ui.screen.home.homeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import org.gtkkn.bindings.gio.ApplicationFlags
import org.gtkkn.bindings.gio.annotations.GioVersion2_28
import org.gtkkn.bindings.gtk.Application
import org.gtkkn.bindings.gtk.ApplicationWindow
import org.gtkkn.extensions.gio.runApplication

private const val APP_ID = "dev.dimension.flare"

@OptIn(GioVersion2_28::class, ExperimentalCoroutinesApi::class)
fun main() {
    val app = Application(APP_ID, ApplicationFlags.FLAGS_NONE)
    // dirty hack to make the main dispatcher work
    Dispatchers.setMain(Dispatchers.Default)
    val context = AppContextImpl(CoroutineScope(Dispatchers.Default))
    KoinHelper.start(
        object : InAppNotification {
            override fun onProgress(
                message: Message,
                progress: Int,
                total: Int,
            ) {
            }

            override fun onSuccess(message: Message) {
            }

            override fun onError(
                message: Message,
                throwable: Throwable,
            ) {
            }
        },
    )
    app.connectActivate {
        ApplicationWindow(app).apply {
            setSizeRequest(800, 600)
            setTitle("Flare")
            setChild(
                with(context) {
                    homeScreen()
                },
            )
            present()
        }
    }
    app.runApplication()
}
