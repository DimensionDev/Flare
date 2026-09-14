package dev.dimension.compose.nativekit.resources.moko

import android.content.Context
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.desc.StringDesc

/** Android resolver used by both View and Compose hosts. */
public class AndroidMokoResourceResolver(
    private val context: Context,
) : MokoResourceResolver {
    override fun resolve(value: StringDesc): String = value.toString(context)

    override fun resolve(value: ImageResource): NativeKitImage = NativeKitImage(value.drawableResId)
}

public actual data class NativeKitImage internal constructor(
    public val drawableResId: Int,
)
