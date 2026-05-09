package dev.dimension.flare.common

internal object MimeTypes {
    fun detectFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "image/jpeg"
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "image/png"
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return "image/gif"
        if (bytes.size >= 12 &&
            b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
            (bytes[8].toInt() and 0xFF) == 0x57 &&
            (bytes[9].toInt() and 0xFF) == 0x45 &&
            (bytes[10].toInt() and 0xFF) == 0x42 &&
            (bytes[11].toInt() and 0xFF) == 0x50
        ) {
            return "image/webp"
        }
        if (bytes.size >= 12 &&
            (bytes[4].toInt() and 0xFF) == 0x66 &&
            (bytes[5].toInt() and 0xFF) == 0x74 &&
            (bytes[6].toInt() and 0xFF) == 0x79 &&
            (bytes[7].toInt() and 0xFF) == 0x70
        ) {
            return "video/mp4"
        }
        return null
    }

    fun extensionFor(mimeType: String?): String? =
        when (mimeType?.lowercase()?.substringBefore(';')?.trim()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "image/bmp" -> "bmp"
            "image/svg+xml" -> "svg"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            "video/x-matroska" -> "mkv"
            "video/webm" -> "webm"
            else -> null
        }

    fun hasExtension(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return false
        val ext = name.substring(dot + 1)
        return ext.isNotEmpty() && ext.all { it.isLetterOrDigit() }
    }
}
