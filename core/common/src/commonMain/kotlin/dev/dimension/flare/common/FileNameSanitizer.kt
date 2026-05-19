package dev.dimension.flare.common

private val invalidFileNameCharsRegex = Regex("[^A-Za-z0-9._-]")

public fun String.sanitizeFileName(): String = replace(invalidFileNameCharsRegex, "_")
