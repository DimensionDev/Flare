package dev.dimension.flare

import dev.dimension.flare.ui.screen.home.homeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.gtkkn.bindings.gio.ApplicationFlags
import org.gtkkn.bindings.gio.annotations.GioVersion2_28
import org.gtkkn.bindings.gtk.Application
import org.gtkkn.bindings.gtk.ApplicationWindow
import org.gtkkn.extensions.gio.runApplication

private const val APP_ID = "dev.dimension.flare"

@OptIn(GioVersion2_28::class)
fun main() {
    val app = Application(APP_ID, ApplicationFlags.FLAGS_NONE)
    val context = AppContextImpl(CoroutineScope(Dispatchers.Default))
    app.connectActivate {
        val window = ApplicationWindow(app)
        window.setTitle("Flare")
        val home =
            with(context) {
                homeScreen()
            }
        window.setChild(home)
        window.present()
    }
    app.runApplication()
}
