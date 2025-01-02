package dev.dimension.flare.ui.route

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

@Immutable
internal data class HomeTabRoute(
    val route: Route,
    val routeClass: KClass<out Route>,
    val title: StringResource,
    val icon: ImageVector,
)
