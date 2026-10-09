@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.dimension.flare.common

private fun normalize(text: String): String = js("text.normalize('NFC')")

public actual fun String.normalizeNfc(): String = normalize(this)
