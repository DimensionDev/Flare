package dev.dimension.flare.ui.resources.moko

import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.desc.StringDesc
import platform.UIKit.UIImage

/** Resolver for UIKit hosts. */
public data object AppleMokoResourceResolver : MokoResourceResolver {
    override fun resolve(value: StringDesc): String = value.localized()

    override fun resolve(value: ImageResource): FlareImage =
        FlareImage(
            uiImage =
                requireNotNull(value.toUIImage()) {
                    "Unable to load Moko image resource $value."
                },
        )
}

public actual data class FlareImage internal constructor(
    public val uiImage: UIImage,
)
