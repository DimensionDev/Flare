package dev.dimension.compose.nativekit.resources.moko

import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.desc.StringDesc
import platform.AppKit.NSImage

/** Resolver for AppKit hosts. */
public data object AppleMokoResourceResolver : MokoResourceResolver {
    override fun resolve(value: StringDesc): String = value.localized()

    override fun resolve(value: ImageResource): NativeKitImage =
        NativeKitImage(
            nsImage =
                requireNotNull(value.toNSImage()) {
                    "Unable to load Moko image resource $value."
                },
        )
}

public actual data class NativeKitImage internal constructor(
    public val nsImage: NSImage,
)
