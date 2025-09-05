package dev.dimension.flare.ui.render

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageDirectionRightToLeft
import platform.Foundation.characterDirectionForLanguage
import platform.NaturalLanguage.NLLanguageRecognizer

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
