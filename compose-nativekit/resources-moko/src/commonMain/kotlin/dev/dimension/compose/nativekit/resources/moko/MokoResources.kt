package dev.dimension.compose.nativekit.resources.moko

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitContent
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.StringDesc
import dev.icerock.moko.resources.desc.desc
import dev.icerock.moko.resources.format

/** Resolves Moko resources for the platform hosting the current NativeKit composition. */
public interface MokoResourceResolver {
    public fun resolve(value: StringDesc): String

    public fun resolve(value: ImageResource): NativeKitImage
}

/** Backend-neutral image value returned by [imageResource]. */
public expect class NativeKitImage

private val LocalMokoResourceResolver =
    staticCompositionLocalOf<MokoResourceResolver> {
        error(
            "No MokoResourceResolver was provided. " +
                "Wrap NativeKit content in ProvideMokoResources.",
        )
    }

/** Supplies resource lookup once around a platform host's NativeKit content. */
@Composable
@NativeKitComposable
public fun ProvideMokoResources(
    resolver: MokoResourceResolver,
    content: NativeKitContent,
) {
    CompositionLocalProvider(LocalMokoResourceResolver provides resolver) {
        content()
    }
}

/** Resolves an already-built [StringDesc], including raw and composed descriptions. */
@Composable
@NativeKitComposable
public fun stringResource(value: StringDesc): String = LocalMokoResourceResolver.current.resolve(value)

/** Resolves a generated Moko string resource. */
@Composable
@NativeKitComposable
public fun stringResource(resource: StringResource): String = stringResource(resource.desc())

/** Resolves and formats a generated Moko string resource. */
@Composable
@NativeKitComposable
public fun stringResource(
    resource: StringResource,
    vararg formatArgs: Any,
): String = stringResource(resource.format(*formatArgs))

/** Resolves a generated Moko plural resource for [quantity]. */
@Composable
@NativeKitComposable
public fun pluralStringResource(
    resource: PluralsResource,
    quantity: Int,
): String = stringResource(resource.desc(quantity))

/** Resolves and formats a generated Moko plural resource for [quantity]. */
@Composable
@NativeKitComposable
public fun pluralStringResource(
    resource: PluralsResource,
    quantity: Int,
    vararg formatArgs: Any,
): String = stringResource(resource.format(quantity, *formatArgs))

/** Resolves a generated Moko image into a value consumable by NativeKit components. */
@Composable
@NativeKitComposable
public fun imageResource(resource: ImageResource): NativeKitImage {
    val resolver = LocalMokoResourceResolver.current
    return remember(resolver, resource) {
        resolver.resolve(resource)
    }
}
