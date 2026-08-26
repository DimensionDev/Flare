package dev.dimension.flare.ui.resources.moko

import android.content.Context
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.desc.StringDesc

/** Android resolver used by both View and Compose hosts. */
public class AndroidMokoResourceResolver(
    private val context: Context,
) : MokoResourceResolver {
    override fun resolve(value: StringDesc): String = value.toString(context)

    override fun resolve(value: ImageResource): FlareImage = FlareImage(value.drawableResId)
}

public actual data class FlareImage internal constructor(
    public val drawableResId: Int,
)
