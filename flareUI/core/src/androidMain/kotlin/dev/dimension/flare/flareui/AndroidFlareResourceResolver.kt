package dev.dimension.flare.flareui

/**
 * Maps platform-neutral resource references to the generated Android R class.
 *
 * Consuming resource modules generate implementations of this interface.
 */
public interface AndroidFlareResourceResolver {
    public fun stringId(resource: FlareStringResource): Int

    public fun imageId(resource: FlareImageResource): Int

    public companion object {
        public val None: AndroidFlareResourceResolver =
            object : AndroidFlareResourceResolver {
                override fun stringId(resource: FlareStringResource): Int =
                    error("No Android string resolver was provided for ${resource.key.qualifiedName}")

                override fun imageId(resource: FlareImageResource): Int =
                    error("No Android image resolver was provided for ${resource.key.qualifiedName}")
            }
    }
}
