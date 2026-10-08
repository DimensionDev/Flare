package dev.dimension.flare.common

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping

@OptIn(BetaInteropApi::class)
public actual fun String.normalizeNfc(): String = NSString.create(string = this).precomposedStringWithCanonicalMapping
