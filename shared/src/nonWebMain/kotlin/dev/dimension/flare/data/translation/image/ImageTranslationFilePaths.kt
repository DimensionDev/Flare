package dev.dimension.flare.data.translation.image

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath

internal fun imageTranslationFileUrl(path: String): String {
    val normalized = path.replace('\\', '/')
    val absolute = if (normalized.startsWith('/')) normalized else "/$normalized"
    return "file://" + absolute.encodeURLPath()
}

internal fun imageTranslationFilePath(url: String): String {
    val path = url.removePrefix("file://").decodeURLPart()
    return if (path.length >= 4 && path[0] == '/' && path[1].isLetter() && path[2] == ':' && path[3] == '/') {
        path.drop(1)
    } else {
        path
    }
}
