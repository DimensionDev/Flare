@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.dimension.flare.common

private fun countGraphemes(text: String): Int = js("Array.from(new Intl.Segmenter('und', {granularity: 'grapheme'}).segment(text)).length")

private fun normalize(text: String): String = js("text.normalize('NFC')")

public actual fun String.graphemeCount(): Int = countGraphemes(this)

public actual fun String.normalizeNfc(): String = normalize(this)
