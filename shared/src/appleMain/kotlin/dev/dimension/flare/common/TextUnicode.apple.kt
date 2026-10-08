package dev.dimension.flare.common

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
public actual fun String.graphemeCount(): Int {
    val string = NSString.create(string = this)
    var index = 0uL
    var count = 0
    while (index < string.length) {
        index = string.rangeOfComposedCharacterSequenceAtIndex(index).useContents { location + length }
        count++
    }
    return count
}

@OptIn(BetaInteropApi::class)
public actual fun String.normalizeNfc(): String = NSString.create(string = this).precomposedStringWithCanonicalMapping
