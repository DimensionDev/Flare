@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.dimension.flare.data.network.rss

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.definedExternally
import kotlin.js.js

internal actual fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String? =
    runCatching {
        TextDecoder(charset).decode(bytes.toUint8Array())
    }.getOrNull()

private external class TextDecoder(
    label: String = definedExternally,
) : JsAny {
    fun decode(input: JsAny? = definedExternally): String
}

private fun ByteArray.toUint8Array(): JsAny {
    val array = createUint8Array(size)
    forEachIndexed { index, byte ->
        setUint8(array, index, byte.toInt() and 0xff)
    }
    return array
}

private fun createUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun setUint8(
    array: JsAny,
    index: Int,
    value: Int,
): Unit = js("array[index] = value")
