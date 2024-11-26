package dev.dimension.flare

import dev.dimension.flare.ui.screen.home.homeScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.gtkkn.bindings.gio.ApplicationFlags
import org.gtkkn.bindings.gio.annotations.GioVersion2_28
import org.gtkkn.bindings.gtk.Application
import org.gtkkn.bindings.gtk.ApplicationWindow
import org.gtkkn.extensions.gio.runApplication

private const val APP_ID = "dev.dimension.flare"

@OptIn(GioVersion2_28::class)
fun main() {
    // Create a new application
    val app = Application(APP_ID, ApplicationFlags.FLAGS_NONE)
    val context = AppContextImpl(CoroutineScope(Dispatchers.Default))

    // Connect to "activate" signal of `app`
    app.connectActivate {
//        // Create a button with label and margins
//        val button = Button()
//        button.setLabel("Click me!")
//        button.setMargins(12)
//
//        // Connect to "clicked" signal of `button`
//        button.connectClicked {
//            // Set the label to "Hello World!" after the button has been clicked on
//            button.setLabel("Hello World!")
//        }

        // Create a window and set the title
        val window = ApplicationWindow(app)
        window.setTitle("Flare")

        val home =
            with(context) {
                homeScreen()
            }
        window.setChild(home)

        // Present window
        window.present()
    }

    // Run the application
    app.runApplication()
}

internal sealed interface AppContext {
    val coroutineScope: CoroutineScope
}

private data class AppContextImpl(
    override val coroutineScope: CoroutineScope,
) : AppContext

internal fun <T> AppContext.observe(
    flow: Flow<T>,
    onEach: (T) -> Unit,
) = coroutineScope.launch {
    flow.collect {
        onEach(it)
    }
}
