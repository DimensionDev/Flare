package dev.dimension.flare.ui.render

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import kotlinx.collections.immutable.ImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSArray
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage
import platform.NaturalLanguage.NLLanguageRecognizer

public actual typealias PlatformText = NSArray

public interface SwiftPlatformTextRenderer {
    public fun render(renderRuns: ImmutableList<RenderContent>): PlatformText
}

internal class ApplePlatformTextRenderer(
    private val renderer: SwiftPlatformTextRenderer,
) : PlatformTextRendering {
    override fun render(renderRuns: ImmutableList<RenderContent>): PlatformText = renderer.render(renderRuns)
}

internal interface PlatformTextRendering {
    fun render(renderRuns: ImmutableList<RenderContent>): PlatformText
}

private object PlatformTextRendererHolder : KoinComponent {
    val renderer: PlatformTextRendering by inject()
}

internal actual fun renderPlatformText(renderRuns: ImmutableList<RenderContent>): PlatformText =
    PlatformTextRendererHolder.renderer.render(renderRuns)

internal actual fun String.isRtl(): Boolean = isRightToLeft()

@OptIn(BetaInteropApi::class)
private fun String.isRightToLeft(): Boolean =
    autoreleasepool {
        val langCode =
            NLLanguageRecognizer
                .dominantLanguageForString(this) ?: return@autoreleasepool false

        NSLocale.characterDirectionForLanguage(langCode) ==
            NSLocaleLanguageDirectionRightToLeft
    }
