package dev.dimension.flare.data.network.xqt.xchat

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal object XChatBase64 {
    fun encode(bytes: ByteArray): String = Base64.Default.encode(bytes)

    fun decode(value: String): ByteArray = Base64.Default.decode(value)

    fun encodeUrl(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    fun decodeUrl(value: String): ByteArray {
        val trimmed = value.trim()
        val padding = (4 - trimmed.length % 4) % 4
        return Base64.UrlSafe.decode(trimmed + "=".repeat(padding))
    }
}
