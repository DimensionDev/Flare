package dev.dimension.flare.common

internal fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
