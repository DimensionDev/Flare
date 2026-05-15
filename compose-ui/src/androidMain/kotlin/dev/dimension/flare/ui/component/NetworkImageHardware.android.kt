package dev.dimension.flare.ui.component

import coil3.request.ImageRequest
import coil3.request.allowHardware

internal actual fun ImageRequest.Builder.applyAllowHardware(allowHardware: Boolean): ImageRequest.Builder = allowHardware(allowHardware)
