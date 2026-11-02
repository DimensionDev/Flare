package dev.dimension.flare.feature.plugin.installer

import android.graphics.BitmapFactory

internal actual fun validatePluginIconImage(bytes: ByteArray) {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    requirePluginIconDimensions(options.outWidth, options.outHeight)
    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Platform icon cannot be decoded" }.recycle()
}
