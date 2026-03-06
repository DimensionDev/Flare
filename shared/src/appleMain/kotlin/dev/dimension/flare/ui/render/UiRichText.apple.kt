package dev.dimension.flare.ui.render

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSArray
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage
import platform.NaturalLanguage.NLLanguageRecognizer

public actual typealias PlatformText = NSArray

public interface SwiftPlatformTextRenderer {
    public fun render(richText: UiRichText): PlatformText
}

private object PlatformTextRendererHolder : KoinComponent {
    val renderer: SwiftPlatformTextRenderer by inject()
}

internal actual fun UiRichText.renderPlatformText(): PlatformText = PlatformTextRendererHolder.renderer.render(this)

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
