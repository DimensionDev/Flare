package dev.dimension.flare.data.network.rss

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.Foundation.NSASCIIStringEncoding
import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSString
import platform.Foundation.NSUTF16BigEndianStringEncoding
import platform.Foundation.NSUTF16LittleEndianStringEncoding
import platform.Foundation.NSUTF16StringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun decodeBytes(
    bytes: ByteArray,
    charset: String,
): String? {
    val nsEncoding =
        when (charset.lowercase()) {
            "utf-8", "utf8" -> NSUTF8StringEncoding
            "utf-16" -> NSUTF16StringEncoding
            "utf-16le" -> NSUTF16LittleEndianStringEncoding
            "utf-16be" -> NSUTF16BigEndianStringEncoding
            "us-ascii", "ascii" -> NSASCIIStringEncoding
            "iso-8859-1", "latin1", "latin-1" -> NSISOLatin1StringEncoding
            "gbk", "gb2312", "gb18030", "x-gbk" ->
                CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingGB_18030_2000.toUInt())
            else -> null
        } ?: return null
    return bytes.usePinned {
        val data = NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())
        NSString.create(data = data, encoding = nsEncoding)?.toString()
    }
}
