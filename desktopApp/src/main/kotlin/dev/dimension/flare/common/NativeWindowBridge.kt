package dev.dimension.flare.common

import dev.dimension.flare.common.macos.MacosBridge
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import dev.dimension.flare.ui.route.Route
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils

internal class NativeWindowBridge(
    private val scope: CoroutineScope,
) {
    fun openImageImageViewer(url: String) {
        if (SystemUtils.IS_OS_MAC_OSX) {
            MacosBridge.openImageViewer(url)
        } else {
            // TODO: Implement for other platforms
        }
    }

    fun openStatusImageViewer(route: Route.StatusMedia) {
        scope.launch {
            val medias =
                StatusPresenter(
                    accountType = route.accountType,
                    statusKey = route.statusKey,
                ).moleculeFlow
                    .map {
                        it.status.map {
                            (it.content as? UiTimeline.ItemContent.Status)?.images.orEmpty().toImmutableList()
                        }
                    }.mapNotNull {
                        it.takeSuccess()
                    }.distinctUntilChanged()
                    .firstOrNull()
            if (medias != null) {
                if (SystemUtils.IS_OS_MAC_OSX) {
                    MacosBridge.openStatusImageViewer(medias, selectedIndex = route.index)
                } else {
                    // TODO: Implement for other platforms
                }
            }
        }
    }
}
