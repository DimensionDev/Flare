package dev.dimension.flare.ui.route

internal data class FloatingWindowState(
    val route: Route.WindowRoute,
) {
    var bringToFront: () -> Unit = {}
}
